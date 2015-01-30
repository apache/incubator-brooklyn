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
package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

/** variant of Catalog*ItemDto objects for JS/JSON serialization;
 * see also, subclasses */
@JsonIgnoreProperties({"registeredType"})
public class CatalogItemSummary implements HasId, HasName {

    private final String id;
    private final String symbolicName;
    private final String version;

    //needed for backwards compatibility only (json serializer works on fields, not getters)
    @Deprecated
    private final String type;
    
    private final String javaType;
    
    private final String name;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String description;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String iconUrl;
    private final String planYaml;
    
    private final Map<String, URI> links;

    public CatalogItemSummary(
            @JsonProperty("symbolicName") String symbolicName,
            @JsonProperty("version") String version,
            @JsonProperty("name") String displayName,
            @JsonProperty("javaType") String javaType,
            @JsonProperty("planYaml") String planYaml,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
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
        this.links = (links == null) ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
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

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("id", symbolicName)
                .add("version", version)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(symbolicName, version, name, javaType);
    }
    
    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }
    
}
