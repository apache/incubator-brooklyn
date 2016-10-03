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

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

public class LocationSummary extends LocationSpec implements HasName, HasId {

    private static final long serialVersionUID = -4559153719273573670L;

    private final String id;

    /** only intended for instantiated Locations, not definitions */
    private final String type;
    private final Map<String, URI> links;

    public LocationSummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("spec") String spec,
            @JsonProperty("type") String type,
            @JsonProperty("config") @Nullable Map<String, ?> config,
            @JsonProperty("links") Map<String, URI> links) {
        super(name, spec, config);
        this.id = checkNotNull(id);
        this.type = type;
        this.links = (links == null) ? ImmutableMap.<String, URI> of() : ImmutableMap.copyOf(links);
    }

    @Override
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        LocationSummary that = (LocationSummary) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(type, that.type) &&
                Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, type, links);
    }

    @Override
    public String toString() {
        return "LocationSummary{"
                + "id='" + getId() + '\''
                + "name='" + getName() + '\''
                + "spec='" + getSpec() + '\''
                + "type='" + getType() + '\''
                + ", config=" + getConfig()
                + ", links=" + links
                + '}';
  }

}
