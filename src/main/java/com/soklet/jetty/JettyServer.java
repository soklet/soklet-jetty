/*
 * Copyright 2015 Transmogrify LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soklet.jetty;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import com.soklet.web.server.*;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import com.soklet.util.InstanceProvider;
import com.soklet.web.request.SokletFilter;
import com.soklet.web.request.RequestContextSyncFilter;
import com.soklet.web.response.ResponseHandler;
import com.soklet.web.routing.RoutingServlet;
import com.soklet.web.server.StaticFilesConfiguration.CacheStrategy;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

/**
 * A <a href="http://eclipse.org/jetty">Jetty</a>-backed implementation of {@link Server}.
 *
 * @author <a href="http://revetkn.com">Mark Allen</a>
 * @since 1.0.0
 */
public class JettyServer implements Server {
  private final InstanceProvider instanceProvider;
  private final String host;
  private final int port;
  private final Optional<StaticFilesConfiguration> staticFilesConfiguration;
  private final List<FilterConfiguration> filterConfigurations;
  private final List<ServletConfiguration> servletConfigurations;
  private final List<WebSocketConfiguration> webSocketConfigurations;
  private final HandlerConfigurationFunction handlerConfigurationFunction;
  private final ConnectorConfigurationFunction connectorConfigurationFunction;
  private final Consumer<WebAppContext> webAppContextConfigurationFunction;
  private final org.eclipse.jetty.server.Server server;
  private boolean running;
  private final Object lifecycleLock = new Object();
  private final Logger logger = Logger.getLogger(JettyServer.class.getName());

  protected JettyServer(Builder builder) {
    requireNonNull(builder);
    this.instanceProvider = builder.instanceProvider;
    this.host = builder.host;
    this.port = builder.port;
    this.staticFilesConfiguration = Optional.ofNullable(builder.staticFilesConfiguration);
    this.filterConfigurations = Collections.unmodifiableList(builder.filterConfigurations);
    this.servletConfigurations = Collections.unmodifiableList(builder.servletConfigurations);
    this.webSocketConfigurations = Collections.unmodifiableList(builder.webSocketConfigurations);
    this.handlerConfigurationFunction = builder.handlerConfigurationFunction;
    this.connectorConfigurationFunction = builder.connectorConfigurationFunction;
    this.webAppContextConfigurationFunction = builder.webAppContextConfigurationFunction;
    this.server = createServer();
  }

  public static Builder forInstanceProvider(InstanceProvider instanceProvider) {
    requireNonNull(instanceProvider);
    return new Builder(instanceProvider);
  }

  public static class Builder {
    private final InstanceProvider instanceProvider;
    private String host;
    private int port;
    private StaticFilesConfiguration staticFilesConfiguration;
    private List<FilterConfiguration> filterConfigurations;
    private List<ServletConfiguration> servletConfigurations;
    private List<WebSocketConfiguration> webSocketConfigurations;
    private HandlerConfigurationFunction handlerConfigurationFunction;
    private ConnectorConfigurationFunction connectorConfigurationFunction;
    private Consumer<WebAppContext> webAppContextConfigurationFunction;

    private Builder(InstanceProvider instanceProvider) {
      this.instanceProvider = requireNonNull(instanceProvider);
      this.host = "0.0.0.0";
      this.port = 8888;
      this.filterConfigurations = emptyList();
      this.servletConfigurations = emptyList();
      this.webSocketConfigurations = emptyList();
      this.handlerConfigurationFunction = (server, handlers) -> handlers;
      this.connectorConfigurationFunction = (server, connectors) -> connectors;
      this.webAppContextConfigurationFunction = (webAppContext) -> {};
    }

    public Builder host(String host) {
      this.host = requireNonNull(host);
      return this;
    }

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public Builder staticFilesConfiguration(StaticFilesConfiguration staticFilesConfiguration) {
      this.staticFilesConfiguration = requireNonNull(staticFilesConfiguration);
      return this;
    }

    public Builder filterConfigurations(List<FilterConfiguration> filterConfigurations) {
      this.filterConfigurations = requireNonNull(filterConfigurations);
      return this;
    }

    public Builder servletConfigurations(List<ServletConfiguration> servletConfigurations) {
      this.servletConfigurations = requireNonNull(servletConfigurations);
      return this;
    }

    public Builder webSocketConfigurations(List<WebSocketConfiguration> webSocketConfigurations) {
      this.webSocketConfigurations = requireNonNull(webSocketConfigurations);
      return this;
    }

    public Builder handlerConfigurationFunction(HandlerConfigurationFunction handlerConfigurationFunction) {
      this.handlerConfigurationFunction = requireNonNull(handlerConfigurationFunction);
      return this;
    }

    public Builder connectorConfigurationFunction(ConnectorConfigurationFunction connectorConfigurationFunction) {
      this.connectorConfigurationFunction = requireNonNull(connectorConfigurationFunction);
      return this;
    }

    public Builder webAppContextConfigurationFunction(Consumer<WebAppContext>  webAppContextConfigurationFunction) {
      this.webAppContextConfigurationFunction = webAppContextConfigurationFunction;
      return this;
    }

    public JettyServer build() {
      return new JettyServer(this);
    }
  }

  @Override
  public void start() throws ServerException {
    synchronized (lifecycleLock) {
      if (isRunning()) throw new ServerException("Server is already running");

      if (logger.isLoggable(Level.INFO)) logger.info(format("Starting server on %s:%d...", host(), port()));

      try {
        server.start();
        this.running = true;
        logger.info("Server started.");
      } catch (Exception e) {
        throw new ServerException("Unable to start server", e);
      }
    }
  }

  @Override
  public void stop() throws ServerException {
    synchronized (lifecycleLock) {
      if (!isRunning()) throw new ServerException("Server is already stopped");

      logger.info("Stopping server...");

      try {
        server.stop();
        logger.info("Server stopped.");
      } catch (Exception e) {
        throw new ServerException("An error occurred while stopping server", e);
      } finally {
        this.running = false;
      }
    }
  }

  @Override
  public boolean isRunning() {
    synchronized (lifecycleLock) {
      return this.running;
    }
  }

  protected org.eclipse.jetty.server.Server createServer() {
    InstanceProvider instanceProvider = instanceProvider();
    org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server();

    WebAppContext webAppContext = createWebAppContext();

    List<FilterConfiguration> filterConfigurations = new ArrayList<>(filterConfigurations());

    // Put SokletFilter at the front of the list...
    filterConfigurations.add(0, new FilterConfiguration(SokletFilter.class, "/*", new HashMap<String, String>() {
      {
        put(SokletFilter.STATIC_FILES_URL_PATTERN_PARAM, staticFilesConfiguration.isPresent() ? staticFilesConfiguration
          .get().urlPattern() : null);
      }
    }));

    // ...and RequestContextSyncFilter at the back
    filterConfigurations.add(new FilterConfiguration(RequestContextSyncFilter.class, "/*"));

    installFilters(filterConfigurations, instanceProvider, webAppContext);

    List<ServletConfiguration> servletConfigurations = new ArrayList<>(servletConfigurations());

    // Add the routing servlet
    servletConfigurations.add(0, new ServletConfiguration(RoutingServlet.class, "/*"));

    // Add the static file servlet
    if (staticFilesConfiguration().isPresent())
      servletConfigurations.add(0, new ServletConfiguration(SokletDefaultServlet.class, staticFilesConfiguration()
        .get().urlPattern(), new HashMap<String, String>() {
        {
          put("resourceBase", staticFilesConfiguration().get().rootDirectory().toAbsolutePath().toString());
          put(SokletDefaultServlet.CACHE_STRATEGY_PARAM, staticFilesConfiguration.get().cacheStrategy().name());
        }
      }));

    installServlets(servletConfigurations, instanceProvider, webAppContext);

    ServerConnector serverConnector = new ServerConnector(server);
    serverConnector.setHost(host());
    serverConnector.setPort(port());

    HandlerList handlers = new HandlerList();
    handlers.setHandlers(handlerConfigurationFunction.apply(server, Arrays.asList(new Handler[] { webAppContext }))
      .toArray(new Handler[0]));

    server.setHandler(handlers);
    server.setConnectors(connectorConfigurationFunction.apply(server, Collections.singletonList(serverConnector))
      .toArray(new Connector[0]));

    installWebSockets(webSocketConfigurations, instanceProvider, webAppContext);

    webAppContextConfigurationFunction.accept(webAppContext);

    return server;
  }

  protected static class SokletDefaultServlet extends DefaultServlet {
    static final String CACHE_STRATEGY_PARAM = "CACHE_STRATEGY";

    private CacheStrategy cacheStrategy;

    @Override
    public void init() throws UnavailableException {
      super.init();
      this.cacheStrategy = CacheStrategy.valueOf(getInitParameter(CACHE_STRATEGY_PARAM));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      if (this.cacheStrategy == CacheStrategy.FOREVER) {
        response.setHeader("Cache-Control", "max-age=31536000");
      } else if (this.cacheStrategy == CacheStrategy.NEVER) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Expires", "0");
        response.setHeader("Pragma", "no-cache");
      }

      super.doGet(request, response);
    }
  }

  protected WebAppContext createWebAppContext() {
    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath("/");
    webAppContext.setWar("/");
    webAppContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
    webAppContext.setErrorHandler(new ErrorHandler() {
      @Override
      public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
          throws IOException {
        // Special handling for 404s from static file servlet
        if (response.getStatus() == 404 && staticFilesConfiguration().isPresent()) {
          ResponseHandler responseHandler = instanceProvider().provide(ResponseHandler.class);
          responseHandler.handleResponse(request, response, Optional.empty(), Optional.empty(), Optional.empty());
        } else {
          // If it's not a 404 from the static file servlet, fall back to the default handling
          super.handle(target, baseRequest, request, response);
        }
      }
    });

    return webAppContext;
  }

  protected void installFilters(List<FilterConfiguration> filterConfigurations, InstanceProvider instanceProvider,
      WebAppContext webAppContext) {
    requireNonNull(filterConfigurations);
    requireNonNull(instanceProvider);
    requireNonNull(webAppContext);

    for (FilterConfiguration filterConfiguration : filterConfigurations) {
      FilterHolder filterHolder = new FilterHolder(instanceProvider.provide(filterConfiguration.filterClass()));
      filterHolder.setAsyncSupported(true);
      filterHolder.setInitParameters(filterConfiguration.initParameters());

      webAppContext.addFilter(filterHolder, filterConfiguration.urlPattern(),
        EnumSet.copyOf(filterConfiguration.dispatcherTypes()));
    }
  }

  protected void installServlets(List<ServletConfiguration> servletConfigurations, InstanceProvider instanceProvider,
      WebAppContext webAppContext) {
    requireNonNull(servletConfigurations);
    requireNonNull(instanceProvider);
    requireNonNull(webAppContext);

    for (ServletConfiguration servletConfiguration : servletConfigurations) {
      ServletHolder servletHolder = new ServletHolder(instanceProvider.provide(servletConfiguration.servletClass()));
      servletHolder.setAsyncSupported(true);
      servletHolder.setInitParameters(servletConfiguration.initParameters());

      webAppContext.addServlet(servletHolder, servletConfiguration.urlPattern());
    }
  }

  protected void installWebSockets(List<WebSocketConfiguration> webSocketConfigurations, InstanceProvider instanceProvider,
                                 WebAppContext webAppContext) {
    requireNonNull(webSocketConfigurations);
    requireNonNull(instanceProvider);
    requireNonNull(webAppContext);

    if(webSocketConfigurations.size() == 0)
      return;

    try {
			ServerContainer serverContainer = WebSocketServerContainerInitializer.configureContext(webAppContext);

			for (WebSocketConfiguration webSocketConfiguration : webSocketConfigurations) {
				String url = webSocketConfiguration.url().orElse(webSocketConfiguration.webSocketClass().getAnnotation(ServerEndpoint.class).value());
				ServerEndpointConfig serverEndpointConfig = ServerEndpointConfig.Builder.create(webSocketConfiguration.webSocketClass(), url)
						.configurator(new ServerEndpointConfig.Configurator() {
							@Override
							public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
								return (T) instanceProvider.provide(webSocketConfiguration.webSocketClass());
							}
						})
						.build();

				serverContainer.addEndpoint(serverEndpointConfig);
			}
		} catch(Exception e) {
    	throw new RuntimeException("Unable to initialize WebSockets", e);
		}
  }

  public InstanceProvider instanceProvider() {
    return instanceProvider;
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }

  public Optional<StaticFilesConfiguration> staticFilesConfiguration() {
    return staticFilesConfiguration;
  }

  public List<FilterConfiguration> filterConfigurations() {
    return filterConfigurations;
  }

  public List<ServletConfiguration> servletConfigurations() {
    return servletConfigurations;
  }

	public List<WebSocketConfiguration> webSocketConfigurations() {
		return webSocketConfigurations;
	}

  @FunctionalInterface
  public interface HandlerConfigurationFunction extends
      BiFunction<org.eclipse.jetty.server.Server, List<Handler>, List<Handler>> {}

  @FunctionalInterface
  public interface ConnectorConfigurationFunction extends
      BiFunction<org.eclipse.jetty.server.Server, List<Connector>, List<Connector>> {}
}