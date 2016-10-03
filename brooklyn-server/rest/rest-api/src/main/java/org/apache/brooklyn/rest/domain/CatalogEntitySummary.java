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

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CatalogEntitySummary extends CatalogItemSummary {

    private static final long serialVersionUID = 1063908984191424539L;

    private final Set<EntityConfigSummary> config;

    private final Set<SensorSummary> sensors;

    private final Set<EffectorSummary> effectors;

    public CatalogEntitySummary(
            @JsonProperty("symbolicName") String symbolicName,
            @JsonProperty("version") String version,
            @JsonProperty("name") String name,
            @JsonProperty("javaType") String javaType,
            @JsonProperty("planYaml") String planYaml,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("tags") Set<Object> tags,
            @JsonProperty("config") Set<EntityConfigSummary> config,
            @JsonProperty("sensors") Set<SensorSummary> sensors,
            @JsonProperty("effectors") Set<EffectorSummary> effectors,
            @JsonProperty("deprecated") boolean deprecated,
            @JsonProperty("links") Map<String, URI> links
        ) {
        super(symbolicName, version, name, javaType, planYaml, description, iconUrl, tags, deprecated, links);
        this.config = config;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    public Set<EntityConfigSummary> getConfig() {
        return config;
    }

    public Set<SensorSummary> getSensors() {
        return sensors;
    }

    public Set<EffectorSummary> getEffectors() {
        return effectors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CatalogEntitySummary that = (CatalogEntitySummary) o;
        return Objects.equals(config, that.config) &&
                Objects.equals(sensors, that.sensors) &&
                Objects.equals(effectors, that.effectors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), config, sensors, effectors);
    }

    @Override
    public String toString() {
        return "CatalogEntitySummary{" +
                "config=" + config +
                ", sensors=" + sensors +
                ", effectors=" + effectors +
                '}';
    }
}
