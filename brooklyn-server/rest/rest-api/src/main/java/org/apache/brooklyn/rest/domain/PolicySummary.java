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
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

public class PolicySummary implements HasName, HasId, Serializable {

    private static final long serialVersionUID = -5086680835225136768L;

    private final String id;
    private final String name;
    private final String catalogItemId;
    private final Status state;
    private final Map<String, URI> links;

    public PolicySummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("catalogItemId") String catalogItemId,
            @JsonProperty("state") Status state,
            @JsonProperty("links") Map<String, URI> links) {
        this.id = id;
        this.name = name;
        this.catalogItemId = catalogItemId;
        this.state = state;
        this.links = (links == null) ? ImmutableMap.<String, URI> of() : ImmutableMap.copyOf(links);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getCatalogItemId() {
        return catalogItemId;
    }

    public Status getState() {
        return state;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicySummary)) return false;
        PolicySummary that = (PolicySummary) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(catalogItemId, that.catalogItemId) &&
                state == that.state &&
                Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, catalogItemId, state, links);
    }

    @Override
    public String toString() {
        return "ConfigSummary{"
                + "name='" + name + '\''
                + ", id='" + id + '\''
                + ", catalogItemId='" + catalogItemId + '\''
                + ", links=" + links
                + '}';
    }
}
