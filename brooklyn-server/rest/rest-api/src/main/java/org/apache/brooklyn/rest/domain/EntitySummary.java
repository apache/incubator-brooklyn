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

import com.google.common.collect.ImmutableMap;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

public class EntitySummary implements HasId, HasName, Serializable {

    private static final long serialVersionUID = 100490507982229165L;
    
    private final String id;
    private final String name;
    private final String type;
    @JsonSerialize(include = Inclusion.NON_NULL)
    private final String catalogItemId;
    private final Map<String, URI> links;

    public EntitySummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("catalogItemId") String catalogItemId,
            @JsonProperty("links") Map<String, URI> links) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.catalogItemId = catalogItemId;
        this.links = (links == null) ? ImmutableMap.<String, URI> of() : ImmutableMap.copyOf(links);
    }

    public String getType() {
        return type;
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

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof EntitySummary) && id.equals(((EntitySummary) o).getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "EntitySummary{"
                + "id='" + id + '\''
                + ", name=" + name
                + ", type=" + type
                + ", catalogItemId=" + catalogItemId
                + ", links=" + links
                + '}';
  }
}
