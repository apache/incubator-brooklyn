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

import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.CatalogItemMemento;

import com.google.common.collect.Maps;

public class BrooklynMementoManifestImpl implements BrooklynMementoManifest, Serializable {

    private static final long serialVersionUID = -7424713724226824486L;

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String brooklynVersion;
        protected final Map<String, MementoManifest> entityIdToManifest = Maps.newConcurrentMap();
        protected final Map<String, MementoManifest> locationIdToType = Maps.newConcurrentMap();
        protected final Map<String, MementoManifest> policyIdToType = Maps.newConcurrentMap();
        protected final Map<String, MementoManifest> enricherIdToType = Maps.newConcurrentMap();
        protected final Map<String, MementoManifest> feedIdToType = Maps.newConcurrentMap();
        protected final Map<String, CatalogItemMemento> catalogItems = Maps.newConcurrentMap();
        
        public Builder brooklynVersion(String val) {
            brooklynVersion = val; return this;
        }
        public Builder entity(String id, String type, String parent, String catalogItemId) {
            entityIdToManifest.put(id, new MementoManifestImpl(id, type, parent, catalogItemId));
            return this;
        }
        public Builder location(String id, String type, String catalogItemId) {
            locationIdToType.put(id, new MementoManifestImpl(id, type, null, catalogItemId)); return this;
        }
        public Builder locations(Map<String, MementoManifest> vals) {
            locationIdToType.putAll(vals); return this;
        }
        public Builder policy(String id, String type, String catalogItemId) {
            policyIdToType.put(id, new MementoManifestImpl(id, type, null, catalogItemId)); return this;
        }
        public Builder policies(Map<String, MementoManifest> vals) {
            policyIdToType.putAll(vals); return this;
        }
        public Builder enricher(String id, String type, String catalogItemId) {
            enricherIdToType.put(id, new MementoManifestImpl(id, type, null, catalogItemId)); return this;
        }
        public Builder enrichers(Map<String, MementoManifest> vals) {
            enricherIdToType.putAll(vals); return this;
        }
        public Builder feed(String id, String type, String catalogItemId) {
            feedIdToType.put(id, new MementoManifestImpl(id, type, null, catalogItemId)); return this;
        }
        public Builder feed(Map<String, MementoManifest> vals) {
            feedIdToType.putAll(vals); return this;
        }
        public Builder catalogItems(Map<String, CatalogItemMemento> vals) {
            catalogItems.putAll(vals); return this;
        }
        public Builder catalogItem(CatalogItemMemento val) {
            catalogItems.put(val.getId(), val); return this;
        }

        public Builder putType(BrooklynObjectType type, String id, String javaType, String catalogItemId) {
            switch (type) {
            case ENTITY: throw new IllegalArgumentException(type.toCamelCase()+" requires additional parameters");
            case LOCATION: return location(id, javaType, catalogItemId);
            case POLICY: return policy(id, javaType, catalogItemId);
            case ENRICHER: return enricher(id, javaType, catalogItemId);
            case FEED: return feed(id, javaType, catalogItemId);
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

    private final Map<String, MementoManifest> entityIdToManifest;
    private final Map<String, MementoManifest> locationIdToManifest;
    private final Map<String, MementoManifest> policyIdToManifest;
    private final Map<String, MementoManifest> enricherIdToManifest;
    private final Map<String, MementoManifest> feedIdToManifest;
    private Map<String, CatalogItemMemento> catalogItems;
    
    private BrooklynMementoManifestImpl(Builder builder) {
        entityIdToManifest = builder.entityIdToManifest;
        locationIdToManifest = builder.locationIdToType;
        policyIdToManifest = builder.policyIdToType;
        enricherIdToManifest = builder.enricherIdToType;
        feedIdToManifest = builder.feedIdToType;
        catalogItems = builder.catalogItems;
    }

    @Override
    public Map<String, MementoManifest> getEntityIdToManifest() {
        return Collections.unmodifiableMap(entityIdToManifest);
    }

    @Override
    public Map<String, MementoManifest> getLocationIdToManifest() {
        return Collections.unmodifiableMap(locationIdToManifest);
    }

    @Override
    public Map<String, MementoManifest> getPolicyIdToManifest() {
        return Collections.unmodifiableMap(policyIdToManifest);
    }

    @Override
    public Map<String, MementoManifest> getEnricherIdToManifest() {
        return Collections.unmodifiableMap(enricherIdToManifest);
    }

    @Override
    public Map<String, MementoManifest> getFeedIdToManifest() {
        return Collections.unmodifiableMap(feedIdToManifest);
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
                locationIdToManifest.isEmpty() &&
                policyIdToManifest.isEmpty() &&
                enricherIdToManifest.isEmpty() &&
                feedIdToManifest.isEmpty() &&
                catalogItems.isEmpty();
    }
    
}
