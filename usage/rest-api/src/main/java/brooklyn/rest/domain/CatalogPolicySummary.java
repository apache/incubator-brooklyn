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
import java.util.Set;

import org.codehaus.jackson.annotate.JsonProperty;

public class CatalogPolicySummary extends CatalogItemSummary {

    private final Set<PolicyConfigSummary> config;

    public CatalogPolicySummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("config") Set<PolicyConfigSummary> config,
            @JsonProperty("links") Map<String, URI> links
        ) {
        super(id, name, type, type, type, null, description, iconUrl, links);
        // TODO expose config from policies
        this.config = config;
    }
    
    public Set<PolicyConfigSummary> getConfig() {
        return config;
    }

    @Override
    public String toString() {
        return super.toString()+"["+
                "config="+getConfig()+"]";
    }
}
