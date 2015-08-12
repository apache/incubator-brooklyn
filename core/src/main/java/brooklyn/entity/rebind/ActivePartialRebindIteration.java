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
package brooklyn.entity.rebind;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.mementos.BrooklynMementoPersister;
import org.apache.brooklyn.mementos.BrooklynMementoRawData;
import org.apache.brooklyn.mementos.Memento;
import org.apache.brooklyn.mementos.BrooklynMementoRawData.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;
import brooklyn.basic.BrooklynObjectInternal;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.PersistenceActivityMetrics;
import brooklyn.entity.rebind.transformer.CompoundTransformer;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableSet;

import com.google.common.base.Preconditions;

/**
 * Replaces a set of existing entities (and their adjunts) and locations:
 * writes their state, applies a transformation, then reads the state back.
 */
public class ActivePartialRebindIteration extends RebindIteration {

    private static final Logger LOG = LoggerFactory.getLogger(ActivePartialRebindIteration.class);
    
    protected Iterator<BrooklynObject> objectsToRebindInitial;
    protected Collection<BrooklynObject> objectsToRebindFinal;
    protected List<CompoundTransformer> transformers = MutableList.of();
    
    public ActivePartialRebindIteration(RebindManagerImpl rebindManager, 
            ManagementNodeState mode,
            ClassLoader classLoader, RebindExceptionHandler exceptionHandler,
            Semaphore rebindActive, AtomicInteger readOnlyRebindCount, PersistenceActivityMetrics rebindMetrics, BrooklynMementoPersister persistenceStoreAccess
            ) {
        super(rebindManager, mode, classLoader, exceptionHandler, rebindActive, readOnlyRebindCount, rebindMetrics, persistenceStoreAccess);
    }

    @Override
    protected boolean isRebindingActiveAgain() {
        return true;
    }
    
    public void setObjectIterator(Iterator<BrooklynObject> objectsToRebind) {
        this.objectsToRebindInitial = objectsToRebind;
    }
    
    public void applyTransformer(CompoundTransformer transformer) {
        transformers.add(Preconditions.checkNotNull(transformer, "transformer"));
    }
    
    @Override
    protected void doRun() throws Exception {
        Preconditions.checkState(rebindManager.getRebindMode()==ManagementNodeState.MASTER, "Partial rebind only supported in master mode, not "+rebindManager.getRebindMode());
        Preconditions.checkState(readOnlyRebindCount.get()==Integer.MIN_VALUE, "Rebind count should be MIN when running in master mode");
        Preconditions.checkNotNull(objectsToRebindInitial, "Objects to rebind must be set");

        LOG.debug("Partial rebind Rebinding ("+mode+") from "+rebindManager.getPersister().getBackingStoreDescription()+"...");

        super.doRun();
    }
    
    /** Rather than loading from the remote persistence store (as {@link InitialFullRebindIteration} does),
     * this constructs the memento data by serializing the objects we are replacing. 
     * TODO: Currently this does not do any pausing or unmanagement or guarding write access,
     * so there is a short window for data loss between this write and the subsequent read.
     */
    @Override
    protected void loadManifestFiles() throws Exception {
        checkEnteringPhase(1);
        Builder mementoRawBuilder = BrooklynMementoRawData.builder();

        /*
         * Unmanagement is done as part of the "manage" call, entity by entity.
         */

        objectsToRebindFinal = MutableSet.of();
        while (objectsToRebindInitial.hasNext()) {
            BrooklynObject bo = objectsToRebindInitial.next();
            objectsToRebindFinal.add(bo);
            
            if (bo instanceof Entity) {
                // if it's an entity, add all adjuncts. (if doing some sort of pause, that's maybe not necessary...)
                objectsToRebindFinal.addAll( ((EntityInternal)bo).getPolicies() );
                objectsToRebindFinal.addAll( ((EntityInternal)bo).getEnrichers() );
                objectsToRebindFinal.addAll( ((EntityInternal)bo).feeds().getFeeds() );
            }
        }
        
        // get serialization
        for (BrooklynObject bo: objectsToRebindFinal) {
            Memento m = ((BrooklynObjectInternal)bo).getRebindSupport().getMemento();
            BrooklynMementoPersister p = rebindManager.getPersister();
            String mr = ((BrooklynMementoPersisterToObjectStore)p).getMementoSerializer().toString(m);
            mementoRawBuilder.put(BrooklynObjectType.of(bo), bo.getId(), mr);
        }
        // then rebuild
        mementoRawData = mementoRawBuilder.build();

        preprocessManifestFiles();
    }
    
    @Override
    protected void preprocessManifestFiles() throws Exception {
        for (CompoundTransformer transformer: transformers) {
            mementoRawData = transformer.transform(mementoRawData);
        }
        super.preprocessManifestFiles();
        overwritingMaster = true;
    }

    @Override
    protected void rebuildCatalog() {
        checkEnteringPhase(2);
        
        // skip; old catalog items should be re-used
    }
    
    @Override
    protected Collection<String> getMementoRootEntities() {
        // all entities are roots here, because we are not recursing
        return memento.getEntityIds();
    }
    
    @Override
    protected void cleanupOldLocations(Set<String> oldLocations) {
        // not applicable here
    }
    @Override
    protected void cleanupOldEntities(Set<String> oldEntities) {
        // not applicable here
    }

}
