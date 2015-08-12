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
package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.brooklyn.mementos.BrooklynMementoManifest;
import org.apache.brooklyn.mementos.CatalogItemMemento;

import brooklyn.entity.rebind.BrooklynObjectType;

import com.google.common.collect.Maps;

public class BrooklynMementoManifestImpl implements BrooklynMementoManifest, Serializable {

    private static final long serialVersionUID = -7424713724226824486L;

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String brooklynVersion;
        protected final Map<String, EntityMementoManifest> entityIdToManifest = Maps.newConcurrentMap();
        protected final Map<String, String> locationIdToType = Maps.newConcurrentMap();
        protected final Map<String, String> policyIdToType = Maps.newConcurrentMap();
        protected final Map<String, String> enricherIdToType = Maps.newConcurrentMap();
        protected final Map<String, String> feedIdToType = Maps.newConcurrentMap();
        protected final Map<String, CatalogItemMemento> catalogItems = Maps.newConcurrentMap();
        
        public Builder brooklynVersion(String val) {
            brooklynVersion = val; return this;
        }
        public Builder entity(String id, String type, String parent, String catalogItemId) {
            entityIdToManifest.put(id, new EntityMementoManifestImpl(id, type, parent, catalogItemId));
            return this;
        }
        public Builder location(String id, String type) {
            locationIdToType.put(id, type); return this;
        }
        public Builder locations(Map<String, String> vals) {
            locationIdToType.putAll(vals); return this;
        }
        public Builder policy(String id, String type) {
            policyIdToType.put(id, type); return this;
        }
        public Builder policies(Map<String, String> vals) {
            policyIdToType.putAll(vals); return this;
        }
        public Builder enricher(String id, String type) {
            enricherIdToType.put(id, type); return this;
        }
        public Builder enrichers(Map<String, String> vals) {
            enricherIdToType.putAll(vals); return this;
        }
        public Builder feed(String id, String type) {
            feedIdToType.put(id, type); return this;
        }
        public Builder feed(Map<String, String> vals) {
            feedIdToType.putAll(vals); return this;
        }
        public Builder catalogItems(Map<String, CatalogItemMemento> vals) {
            catalogItems.putAll(vals); return this;
        }
        public Builder catalogItem(CatalogItemMemento val) {
            catalogItems.put(val.getId(), val); return this;
        }

        public Builder putType(BrooklynObjectType type, String id, String javaType) {
            switch (type) {
            case ENTITY: throw new IllegalArgumentException(type.toCamelCase()+" requires additional parameters");
            case LOCATION: return location(id, javaType);
            case POLICY: return policy(id, javaType);
            case ENRICHER: return enricher(id, javaType);
            case FEED: return feed(id, javaType);
            case CATALOG_ITEM: throw new IllegalArgumentException(type.toCamelCase()+" requires different parameters");
            case UNKNOWN: 
            default: 
                throw new IllegalArgumentException(type.toCamelCase()+" not supported");
            }
        }

        public BrooklynMementoManifest build() {
            return new BrooklynMementoManifestImpl(this);
        }
    }

    private final Map<String, EntityMementoManifest> entityIdToManifest;
    private final Map<String, String> locationIdToType;
    private final Map<String, String> policyIdToType;
    private final Map<String, String> enricherIdToType;
    private final Map<String, String> feedIdToType;
    private Map<String, CatalogItemMemento> catalogItems;
    
    private BrooklynMementoManifestImpl(Builder builder) {
        entityIdToManifest = builder.entityIdToManifest;
        locationIdToType = builder.locationIdToType;
        policyIdToType = builder.policyIdToType;
        enricherIdToType = builder.enricherIdToType;
        feedIdToType = builder.feedIdToType;
        catalogItems = builder.catalogItems;
    }

    @Override
    public Map<String, EntityMementoManifest> getEntityIdToManifest() {
        return Collections.unmodifiableMap(entityIdToManifest);
    }

    @Override
    public Map<String, String> getLocationIdToType() {
        return Collections.unmodifiableMap(locationIdToType);
    }

    @Override
    public Map<String, String> getPolicyIdToType() {
        return Collections.unmodifiableMap(policyIdToType);
    }

    @Override
    public Map<String, String> getEnricherIdToType() {
        return Collections.unmodifiableMap(enricherIdToType);
    }

    @Override
    public Map<String, String> getFeedIdToType() {
        return Collections.unmodifiableMap(feedIdToType);
    }
    
    @Override
    public CatalogItemMemento getCatalogItemMemento(String id) {
        return catalogItems.get(id);
    }

    @Override
    public Collection<String> getCatalogItemIds() {
        return Collections.unmodifiableSet(catalogItems.keySet());
    }

    @Override
    public Map<String, CatalogItemMemento> getCatalogItemMementos() {
        return Collections.unmodifiableMap(catalogItems);
    }

    @Override
    public boolean isEmpty() {
        return entityIdToManifest.isEmpty() &&
                locationIdToType.isEmpty() &&
                policyIdToType.isEmpty() &&
                enricherIdToType.isEmpty() &&
                feedIdToType.isEmpty() &&
                catalogItems.isEmpty();
    }
    
}
