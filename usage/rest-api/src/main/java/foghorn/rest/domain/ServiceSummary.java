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
package foghorn.rest.domain;

import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.HasId;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;
import java.util.Map;

public class ServiceSummary implements HasId {

    @JsonIgnore
    private EntitySummary entitySummary;

    public ServiceSummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("catalogItemId") String catalogItemId,
            @JsonProperty("links") Map<String, URI> links
    ) {
        entitySummary = new EntitySummary(id, name, type, catalogItemId, links);
    }

    @JsonProperty
    public String getType() {
        return entitySummary.getType();
    }

    @JsonProperty
    public String getId() {
        return entitySummary.getId();
    }

    @JsonProperty
    public String getName() {
        return entitySummary.getName();
    }

    @JsonProperty
    public String getCatalogItemId() {
        return entitySummary.getCatalogItemId();
    }

    @JsonProperty
    public Map<String, URI> getLinks() {
        return entitySummary.getLinks();
    }

    @Override
    public boolean equals(Object o) {
        return entitySummary.equals(o);
    }

    @Override
    public int hashCode() {
        return entitySummary.hashCode();
    }

    @Override
    public String toString() {
        return "Service<" + entitySummary.toString() + ">";
    }
}
