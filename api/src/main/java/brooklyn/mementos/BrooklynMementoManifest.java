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

import java.io.Serializable;
import java.util.Map;

import brooklyn.entity.trait.Identifiable;

/**
 * Represents a manifest of the entities etc in the overall memento.
 * 
 * @author aled
 */
public interface BrooklynMementoManifest extends BrooklynCatalogMementoManifest, Serializable {
    public interface MementoManifest extends Identifiable {
        @Override
        public String getId();
        public String getType();
        public String getParent();
        public String getCatalogItemId();
    }

    public Map<String, MementoManifest> getEntityIdToManifest();

    public Map<String, MementoManifest> getLocationIdToManifest();

    public Map<String, MementoManifest> getPolicyIdToManifest();

    public Map<String, MementoManifest> getEnricherIdToManifest();

    public Map<String, MementoManifest> getFeedIdToManifest();
    
    @Override
    public boolean isEmpty();
    
}
