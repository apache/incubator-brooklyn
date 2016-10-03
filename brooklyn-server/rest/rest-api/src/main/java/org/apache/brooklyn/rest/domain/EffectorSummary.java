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

import java.io.Serializable;
import java.net.URI;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

public class EffectorSummary implements HasName, Serializable {

    private static final long serialVersionUID = 8103535211378449509L;

    public static class ParameterSummary<T> implements HasName, Serializable {
        private static final long serialVersionUID = -6393686096290306153L;

        private final String name;
        private final String type;
        private final String description;
        private final T defaultValue;

        public ParameterSummary (
                @JsonProperty("name") String name,
                @JsonProperty("type") String type,
                @JsonProperty("description") String description,
                @JsonProperty("defaultValue") T defaultValue) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.defaultValue = defaultValue;
        }

        @Override
        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public T getDefaultValue() {
            return defaultValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

          ParameterSummary<?> that = (ParameterSummary<?>) o;

          return Objects.equal(this.name, that.name)
                  && Objects.equal(this.type, that.type)
                  && Objects.equal(this.description, that.description)
                  && Objects.equal(this.defaultValue, that.defaultValue);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name, type, description, defaultValue);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .omitNullValues()
                    .add("name", name)
                    .add("type", type)
                    .add("description", description)
                    .add("defaultValue", defaultValue)
                    .toString();
        }
    }

    private final String name;
    private final String returnType;
    private final Set<ParameterSummary<?>> parameters;
    private final String description;
    private final Map<String, URI> links;

    public EffectorSummary(
            @JsonProperty("name") String name,
            @JsonProperty("returnType") String returnType,
            @JsonProperty("parameters") Set<ParameterSummary<?>> parameters,
            @JsonProperty("description") String description,
            @JsonProperty("links") Map<String, URI> links) {
        this.name = name;
        this.description = description;
        this.returnType = returnType;
        this.parameters = parameters;
        this.links = links != null ? ImmutableMap.copyOf(links) : null;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getReturnType() {
        return returnType;
    }

    public Set<ParameterSummary<?>> getParameters() {
        return parameters;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EffectorSummary that = (EffectorSummary) o;
        return java.util.Objects.equals(name, that.name) &&
                java.util.Objects.equals(returnType, that.returnType) &&
                java.util.Objects.equals(parameters, that.parameters) &&
                java.util.Objects.equals(description, that.description) &&
                java.util.Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, returnType, parameters, description, links);
    }

    @Override
    public String toString() {
        return "EffectorSummary{"
                + "name='" + name + '\''
                + ", description='" + description + '\''
                + ", returnType='" + returnType + '\''
                + ", parameters=" + parameters
                + ", links=" + links
                + '}';
    }
}
