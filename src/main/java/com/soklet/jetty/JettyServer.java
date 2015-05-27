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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import com.soklet.web.response.ResponseHandler;
import com.soklet.web.routing.RoutingServlet;
import com.soklet.web.server.FilterConfiguration;
import com.soklet.web.server.Server;
import com.soklet.web.server.ServerException;
import com.soklet.web.server.ServletConfiguration;
import com.soklet.web.server.StaticFilesConfiguration;

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
  private List<FilterConfiguration> filterConfigurations;
  private List<ServletConfiguration> servletConfigurations;
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
    this.filterConfigurations = builder.filterConfigurations;
    this.servletConfigurations = builder.servletConfigurations;
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

    private Builder(InstanceProvider instanceProvider) {
      this.instanceProvider = requireNonNull(instanceProvider);
      this.host = "0.0.0.0";
      this.port = 8888;
      this.filterConfigurations = emptyList();
      this.servletConfigurations = emptyList();
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

    public JettyServer build() {
      return new JettyServer(this);
    }
  }

  @Override
  public void start() throws ServerException {
    synchronized (lifecycleLock) {
      if (isRunning())
        throw new ServerException("Server is already running");

      if (logger.isLoggable(Level.INFO))
        logger.info(format("Starting server on %s:%d...", host(), port()));

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
      if (!isRunning())
        throw new ServerException("Server is already stopped");

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

    installFilters(filterConfigurations(), instanceProvider, webAppContext);

    List<ServletConfiguration> servletConfigurations = new ArrayList<>(servletConfigurations());

    // Add the routing servlet
    servletConfigurations.add(new ServletConfiguration(RoutingServlet.class, "/*"));

    // Add the static file servlet
    if (staticFilesConfiguration().isPresent())
      servletConfigurations.add(new ServletConfiguration(DefaultServlet.class, staticFilesConfiguration().get()
        .urlPattern(), new HashMap<String, String>() {
        {
          put("resourceBase", staticFilesConfiguration().get().rootDirectory().toAbsolutePath().toString());
        }
      }));

    installServlets(servletConfigurations, instanceProvider, webAppContext);

    ServerConnector serverConnector = new ServerConnector(server);
    serverConnector.setHost(host());
    serverConnector.setPort(port());

    HandlerList handlers = new HandlerList();
    handlers.setHandlers(new Handler[] { webAppContext });

    server.setHandler(handlers);
    server.addConnector(serverConnector);

    return server;
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
}