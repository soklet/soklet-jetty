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

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.soklet.util.InstanceProvider;
import com.soklet.web.server.FilterConfiguration;
import com.soklet.web.server.ServletConfiguration;
import com.soklet.web.server.StaticFilesConfiguration;

/**
 * Specifies configuration for a {@link JettyServer}.
 * 
 * @author <a href="http://revetkn.com">Mark Allen</a>
 * @since 1.0.0
 */
public class JettyServerConfiguration {
  private final InstanceProvider instanceProvider;
  private final String host;
  private final int port;
  private final Optional<StaticFilesConfiguration> staticFilesConfiguration;
  private List<FilterConfiguration> filterConfigurations;
  private List<ServletConfiguration> servletConfigurations;

  private JettyServerConfiguration(InstanceProvider instanceProvider, String host, int port,
      Optional<StaticFilesConfiguration> staticFilesConfiguration, List<FilterConfiguration> filterConfigurations,
      List<ServletConfiguration> servletConfigurations) {
    this.instanceProvider = requireNonNull(instanceProvider);
    this.host = requireNonNull(host);
    this.port = port;
    this.staticFilesConfiguration = requireNonNull(staticFilesConfiguration);
    this.filterConfigurations = unmodifiableList(new ArrayList<>(requireNonNull(filterConfigurations)));
    this.servletConfigurations = unmodifiableList(new ArrayList<>(requireNonNull(servletConfigurations)));
  }

  public static Builder builder(InstanceProvider instanceProvider) {
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

    public JettyServerConfiguration build() {
      return new JettyServerConfiguration(instanceProvider, host, port, Optional.ofNullable(staticFilesConfiguration),
        filterConfigurations, servletConfigurations);
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