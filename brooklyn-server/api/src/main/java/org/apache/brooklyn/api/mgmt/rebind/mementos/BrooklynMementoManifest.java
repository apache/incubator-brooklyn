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
package org.apache.brooklyn.api.mgmt.rebind.mementos;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import org.apache.brooklyn.api.objs.Identifiable;

/**
 * Represents a manifest of the entities etc in the overall memento.
 * 
 * @author aled
 */
public interface BrooklynMementoManifest extends Serializable {
    public interface EntityMementoManifest extends Identifiable{
        public String getId();
        public String getType();
        public String getParent();
        public String getCatalogItemId();
    }

    public Map<String, EntityMementoManifest> getEntityIdToManifest();

    public Map<String, String> getLocationIdToType();

    public Map<String, String> getPolicyIdToType();

    public Map<String, String> getEnricherIdToType();

    public Map<String, String> getFeedIdToType();
    
    public CatalogItemMemento getCatalogItemMemento(String id);

    public Collection<String> getCatalogItemIds();

    public Map<String, CatalogItemMemento> getCatalogItemMementos();

    public boolean isEmpty();
    
}
