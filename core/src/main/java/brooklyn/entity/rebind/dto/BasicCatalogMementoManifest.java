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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import brooklyn.mementos.BrooklynCatalogMementoManifest;
import brooklyn.mementos.CatalogItemMemento;

import com.google.common.collect.Maps;

public class BasicCatalogMementoManifest implements BrooklynCatalogMementoManifest {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected final Map<String, CatalogItemMemento> catalogItems = Maps.newConcurrentMap();
        public Builder catalogItem(CatalogItemMemento val) {
            catalogItems.put(val.getId(), val); return this;
        }
        public BasicCatalogMementoManifest build() {
            return new BasicCatalogMementoManifest(catalogItems);
        }
    }

    private Map<String, CatalogItemMemento> mementos;

    private BasicCatalogMementoManifest(Map<String, CatalogItemMemento> mementos) {
        this.mementos = mementos;
    }

    @Override
    public CatalogItemMemento getCatalogItemMemento(String id) {
        return mementos.get(id);
    }

    @Override
    public Collection<String> getCatalogItemIds() {
        return Collections.unmodifiableSet(mementos.keySet());
    }

    @Override
    public Map<String, CatalogItemMemento> getCatalogItemMementos() {
        return Collections.unmodifiableMap(mementos);
    }

    @Override
    public boolean isEmpty() {
        return mementos.isEmpty();
    }

}
