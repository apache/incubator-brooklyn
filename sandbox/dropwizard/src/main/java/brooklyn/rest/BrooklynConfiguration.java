/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.rest;

import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.auth.User;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.yammer.dropwizard.config.Configuration;
import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Values for the fields in this Configuration object are loaded from a YAML/JSON config file.
 */
public class BrooklynConfiguration extends Configuration {

  @Valid
  @NotNull
  private boolean stopApplicationsOnExit = true;

  @Valid
  @NotNull
  @JsonProperty
  private List<LocationSpec> locations = Lists.newLinkedList();

  @Valid
  @NotNull
  @JsonProperty
  private ExecutorConfiguration executor = new ExecutorConfiguration();

  @NotNull
  @JsonProperty
  private Set<User> users = Sets.newHashSet();

  @NotNull
  @JsonProperty
  private String authBasicRealm = "Brooklyn REST Server";

  @NotNull
  private String managementContextClass = LocalManagementContext.class.getCanonicalName();

  public List<LocationSpec> getLocations() {
    return locations;
  }

  public boolean isStopApplicationsOnExit() {
    return stopApplicationsOnExit;
  }

  public ExecutorConfiguration getExecutorConfiguration() {
    return executor;
  }

  public Set<User> getUsers() {
    return users;
  }

  public String getAuthBasicRealm() {
    return authBasicRealm;
  }
  
  @SuppressWarnings("unchecked")
  public Class<? extends ManagementContext> getManagementContextClass() {
      try {
          return (Class<? extends ManagementContext>) Class.forName(managementContextClass);
      } catch (ClassNotFoundException e) {
          throw Exceptions.propagate(e);
      }
  }
  
}