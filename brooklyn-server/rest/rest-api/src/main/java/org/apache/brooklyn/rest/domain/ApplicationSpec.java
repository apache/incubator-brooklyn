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
package org.apache.brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ApplicationSpec implements HasName, Serializable {

    private static final long serialVersionUID = -7090404504233835343L;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String type;
        private Set<EntitySpec> entities;
        private Set<String> locations;
        private Map<String, String> config;

        public Builder from(ApplicationSpec spec) {
            this.name = spec.name;
            this.entities = spec.entities;
            this.locations = spec.locations;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder entities(Set<EntitySpec> entities) {
            this.entities = entities;
            return this;
        }

        public Builder locations(Set<String> locations) {
            this.locations = locations;
            return this;
        }

        public Builder config(Map<String, String> config) {
            this.config = config;
            return this;
        }

        public ApplicationSpec build() {
            return new ApplicationSpec(name, type, entities, locations, config);
        }
    }

    private final String name;
    private final String type;
    private final Set<EntitySpec> entities;
    private final Set<String> locations;
    private final Map<String, String> config;

  public ApplicationSpec(
          @JsonProperty("name") String name,
          @JsonProperty("type") String type,
          @JsonProperty("entities") Set<EntitySpec> entities,
          @JsonProperty("locations") Collection<String> locations,
          @JsonProperty("config") Map<String, String> config) {
      this.name = name;
      this.type = type;
      if (entities==null) {
          this.entities = null;
      } else {
          this.entities = (entities.isEmpty() && type!=null) ? null : ImmutableSet.copyOf(entities);
      }
      this.locations = ImmutableSet.copyOf(checkNotNull(locations, "locations must be provided for an application spec"));
      this.config = config == null ? Collections.<String, String>emptyMap() : ImmutableMap.<String, String>copyOf(config);
      if (this.entities!=null && this.type!=null) throw new IllegalStateException("cannot supply both type and entities for an application spec");
      // valid for both to be null, e.g. for an anonymous type
//      if (this.entities==null && this.type==null) throw new IllegalStateException("must supply either type or entities for an application spec");
  }

    @Override
    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Set<EntitySpec> getEntities() {
        return entities;
    }

    public Set<String> getLocations() {
        return locations;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationSpec that = (ApplicationSpec) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(entities, that.entities) &&
                Objects.equals(locations, that.locations) &&
                Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, entities, locations, config);
    }

    @Override
    public String toString() {
        return "ApplicationSpec{"
                + "name='" + name + '\''
                + ", type=" + type
                + ", entitySpecs=" + entities
                + ", locations=" + locations
                + ", config=" + config
                + '}';
  }
}
