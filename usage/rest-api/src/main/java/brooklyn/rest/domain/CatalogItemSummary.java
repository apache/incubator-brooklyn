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
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

/** variant of Catalog*ItemDto objects for JS/JSON serialization;
 * see also, subclasses */
public class CatalogItemSummary implements HasId, HasName {

    private final String id;
    
    // TODO too many types, see in CatalogItem
    private final String type;
    private final String javaType;
    private final String registeredType;
    
    private final String name;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String description;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String iconUrl;
    private final String planYaml;
    
    private final Map<String, URI> links;

    public CatalogItemSummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("registeredType") String registeredType,
            @JsonProperty("javaType") String javaType,
            @JsonProperty("type") String highLevelType,
            @JsonProperty("planYaml") String planYaml,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("links") Map<String, URI> links
        ) {
        this.id = id;
        this.name = name;
        this.javaType = javaType;
        this.registeredType = registeredType;
        this.type = highLevelType;
        this.planYaml = planYaml;
        this.description = description;
        this.iconUrl = iconUrl;
        this.links = ImmutableMap.copyOf(links);
    }
    
    @Override
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getJavaType() {
        return javaType;
    }

    public String getRegisteredType() {
        return registeredType;
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
        return Objects.toStringHelper(this).add("id", id).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, name, type);
    }
    
    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }
    
}
