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
package brooklyn.mementos;

import java.util.Collections;
import java.util.Map;

import com.google.common.annotations.Beta;
import com.google.common.collect.Maps;

/**
 * Represents the raw persisted data.
 */
@Beta
public class BrooklynMementoRawData {

    // TODO Should this be on an interface?
    // The file-based (or object-store based) structure for storing data may well change; is this representation sufficient?

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String brooklynVersion;
        protected final Map<String, String> entities = Maps.newConcurrentMap();
        protected final Map<String, String> locations = Maps.newConcurrentMap();
        protected final Map<String, String> policies = Maps.newConcurrentMap();
        protected final Map<String, String> enrichers = Maps.newConcurrentMap();
        
        public Builder brooklynVersion(String val) {
            brooklynVersion = val; return this;
        }
        public Builder entity(String id, String val) {
            entities.put(id, val); return this;
        }
        public Builder entities(Map<String, String> vals) {
            entities.putAll(vals); return this;
        }
        public Builder location(String id, String val) {
            locations.put(id, val); return this;
        }
        public Builder locations(Map<String, String> vals) {
            locations.putAll(vals); return this;
        }
        public Builder policy(String id, String val) {
            policies.put(id, val); return this;
        }
        public Builder policies(Map<String, String> vals) {
            policies.putAll(vals); return this;
        }
        public Builder enricher(String id, String val) {
            enrichers.put(id, val); return this;
        }
        public Builder enrichers(Map<String, String> vals) {
            enrichers.putAll(vals); return this;
        }
        public BrooklynMementoRawData build() {
            return new BrooklynMementoRawData(this);
        }
    }

    private final Map<String, String> entities;
    private final Map<String, String> locations;
    private final Map<String, String> policies;
    private final Map<String, String> enrichers;
    
    private BrooklynMementoRawData(Builder builder) {
        entities = builder.entities;
        locations = builder.locations;
        policies = builder.policies;
        enrichers = builder.enrichers;
    }

    public Map<String, String> getEntities() {
        return Collections.unmodifiableMap(entities);
    }

    public Map<String, String> getLocations() {
        return Collections.unmodifiableMap(locations);
    }

    public Map<String, String> getPolicies() {
        return Collections.unmodifiableMap(policies);
    }

    public Map<String, String> getEnrichers() {
        return Collections.unmodifiableMap(enrichers);
    }
    
    public boolean isEmpty() {
        return entities.isEmpty() && locations.isEmpty() && policies.isEmpty() && enrichers.isEmpty();
    }
}
