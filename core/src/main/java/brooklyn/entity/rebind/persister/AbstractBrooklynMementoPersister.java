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
package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.dto.BasicCatalogMementoManifest;
import brooklyn.entity.rebind.dto.BrooklynMementoManifestImpl;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.mementos.BrooklynCatalogMementoManifest;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.mementos.CatalogItemMemento;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;

/**
 * @deprecated since 0.7.0 for production use {@link BrooklynMementoPersisterToObjectStore} instead 
 */
@Deprecated
public abstract class AbstractBrooklynMementoPersister implements BrooklynMementoPersister {

    private static final Logger log = LoggerFactory.getLogger(AbstractBrooklynMementoPersister.class);
    
    protected volatile MutableBrooklynMemento memento = new MutableBrooklynMemento();
    
    @Override
    public BrooklynMementoRawData loadMementoRawData(RebindExceptionHandler exceptionHandler) {
        return null;
    }
    
    @Override
    public BrooklynMemento loadMemento(BrooklynMementoRawData mementoData, LookupContext lookupContext, RebindExceptionHandler exceptionHandler) {
        // Trusting people not to cast+modify, because the in-memory persister wouldn't be used in production code
        return memento;
    }
    
    @Override
    public BrooklynMemento loadMemento(LookupContext lookupContext, RebindExceptionHandler exceptionHandler) {
        return loadMemento(null, lookupContext, exceptionHandler);
    }
    
    @Override
    public BrooklynMementoManifest loadMementoManifest(RebindExceptionHandler exceptionHandler) {
        return loadMementoManifest(null, exceptionHandler);
    }
    
    @Override
    public BrooklynCatalogMementoManifest loadCatalogMementos(BrooklynMementoRawData mementoData, RebindExceptionHandler exceptionHandler) {
        BasicCatalogMementoManifest.Builder builder = BasicCatalogMementoManifest.builder();
        for (CatalogItemMemento entity : memento.getCatalogItemMementos().values()) {
            builder.catalogItem(entity);
        }
        return builder.build();
    }

    @Override
    public BrooklynMementoManifest loadMementoManifest(BrooklynMementoRawData mementoData, RebindExceptionHandler exceptionHandler) {
        BrooklynMementoManifestImpl.Builder builder = BrooklynMementoManifestImpl.builder();
        for (EntityMemento entity : memento.getEntityMementos().values()) {
            builder.entity(entity.getId(), entity.getType(), entity.getParent(), entity.getCatalogItemId());
        }
        for (LocationMemento entity : memento.getLocationMementos().values()) {
            builder.location(entity.getId(), entity.getType());
        }
        for (PolicyMemento entity : memento.getPolicyMementos().values()) {
            builder.policy(entity.getId(), entity.getType());
        }
        for (EnricherMemento entity : memento.getEnricherMementos().values()) {
            builder.enricher(entity.getId(), entity.getType());
        }
        for (CatalogItemMemento entity : memento.getCatalogItemMementos().values()) {
            builder.catalogItem(entity);
        }
        return builder.build();
    }

    @Override public void enableWriteAccess() {}
    @Override public void disableWriteAccess(boolean graceful) {}
    @Override public void stop(boolean graceful) {}
    
    @Override
    public void checkpoint(BrooklynMemento newMemento, PersistenceExceptionHandler exceptionHandler) {
        memento.reset(checkNotNull(newMemento, "memento"));
    }
    
    public void checkpoint(BrooklynMementoRawData newMemento, PersistenceExceptionHandler exceptionHandler) {
        throw new IllegalStateException("Not supported; use "+BrooklynMementoPersisterToObjectStore.class);
    }
    
    @Override
    public void delta(Delta delta, PersistenceExceptionHandler exceptionHanlder) {
        memento.removeEntities(delta.removedEntityIds());
        memento.removeLocations(delta.removedLocationIds());
        memento.removePolicies(delta.removedPolicyIds());
        memento.removeEnrichers(delta.removedEnricherIds());
        memento.removeCatalogItems(delta.removedCatalogItemIds());
        memento.updateEntityMementos(delta.entities());
        memento.updateLocationMementos(delta.locations());
        memento.updatePolicyMementos(delta.policies());
        memento.updateEnricherMementos(delta.enrichers());
        memento.updateCatalogItemMementos(delta.catalogItems());
    }

    @Override
    public void queueDelta(Delta delta) {
        log.warn("Legacy persister ignoring queued delta: "+delta);
    }
    
    @Override
    public String getBackingStoreDescription() {
        return toString();
    }
    
}
