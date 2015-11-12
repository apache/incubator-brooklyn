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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.util.collections.Jsonya;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.google.common.collect.ImmutableList;

public class CatalogEntitySummary extends CatalogItemSummary {

    private static final long serialVersionUID = 1063908984191424539L;

    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final List<Object> tags;

    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final Set<EntityConfigSummary> config;
    
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final Set<SensorSummary> sensors;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
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
        super(symbolicName, version, name, javaType, planYaml, description, iconUrl, deprecated, links);
        this.tags = (tags == null) ? ImmutableList.of() : ImmutableList.<Object> copyOf(tags);
        this.config = config;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    public Collection<Object> getTags() {
        List<Object> result = new ArrayList<Object>();
        for (Object t : tags) {
            // TODO if we had access to a mapper we could use it to give better json
            result.add(Jsonya.convertToJsonPrimitive(t));
        }
        return result;
    }

    @JsonIgnore
    public Collection<Object> getRawTags() {
        return tags;
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
    public String toString() {
        return super.toString()+"["+
                "tags="+getRawTags()+"; " +
                "config="+getConfig()+"; " +
                "sensors="+getSensors()+"; "+
                "effectors="+getEffectors()+"; "+
                "deprecated="+isDeprecated()+"]";
    }
}
