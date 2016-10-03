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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** variant of Catalog*ItemDto objects for JS/JSON serialization;
 * see also, subclasses */
@JsonIgnoreProperties(ignoreUnknown = true)
// ignore unknown, ie properties from subclasses (entity)
public class CatalogItemSummary implements HasId, HasName, Serializable {

    private static final long serialVersionUID = -823483595879417681L;

    private final String id;
    private final String symbolicName;
    private final String version;

    //needed for backwards compatibility only (json serializer works on fields, not getters)
    @Deprecated
    private final String type;

    private final String javaType;

    private final String name;
    private final String description;
    private final String iconUrl;
    private final String planYaml;
    private final List<Object> tags;
    private final boolean deprecated;

    private final Map<String, URI> links;

    public CatalogItemSummary(
            @JsonProperty("symbolicName") String symbolicName,
            @JsonProperty("version") String version,
            @JsonProperty("name") String displayName,
            @JsonProperty("javaType") String javaType,
            @JsonProperty("planYaml") String planYaml,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("tags") Set<Object> tags,
            @JsonProperty("deprecated") boolean deprecated,
            @JsonProperty("links") Map<String, URI> links
            ) {
        this.id = symbolicName + ":" + version;
        this.symbolicName = symbolicName;
        this.type = symbolicName;
        this.version = version;
        this.name = displayName;
        this.javaType = javaType;
        this.planYaml = planYaml;
        this.description = description;
        this.iconUrl = iconUrl;
        this.tags = (tags == null) ? ImmutableList.of() : ImmutableList.copyOf(tags);
        this.links = (links == null) ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
        this.deprecated = deprecated;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public String getVersion() {
        return version;
    }

    public String getJavaType() {
        return javaType;
    }

    public String getType() {
        return type;
    }

    public String getPlanYaml() {
        return planYaml;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public Collection<Object> getTags() {
        return tags;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CatalogItemSummary that = (CatalogItemSummary) o;
        return deprecated == that.deprecated &&
                Objects.equals(id, that.id) &&
                Objects.equals(symbolicName, that.symbolicName) &&
                Objects.equals(version, that.version) &&
                Objects.equals(type, that.type) &&
                Objects.equals(javaType, that.javaType) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(iconUrl, that.iconUrl) &&
                Objects.equals(planYaml, that.planYaml) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(links, that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, symbolicName, version, type, javaType, name, description, iconUrl, planYaml, tags, deprecated, links);
    }

    @Override
    public String toString() {
        return "CatalogItemSummary{" +
                "id='" + id + '\'' +
                ", symbolicName='" + symbolicName + '\'' +
                ", version='" + version + '\'' +
                ", type='" + type + '\'' +
                ", javaType='" + javaType + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", iconUrl='" + iconUrl + '\'' +
                ", planYaml='" + planYaml + '\'' +
                ", tags=" + tags +
                ", deprecated=" + deprecated +
                ", links=" + links +
                '}';
    }
}
