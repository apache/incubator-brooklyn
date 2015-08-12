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

import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.mementos.BrooklynMementoPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynLogging;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.persister.PersistenceActivityMetrics;
import brooklyn.management.internal.BrooklynObjectManagementMode;
import brooklyn.management.internal.EntityManagerInternal;
import brooklyn.management.internal.LocationManagerInternal;
import brooklyn.management.internal.ManagementTransitionMode;
import brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

/**
 * Does an un-bind (if necessary) and re-bind for a subset of items.  
 */
public class InitialFullRebindIteration extends RebindIteration {

    private static final Logger LOG = LoggerFactory.getLogger(InitialFullRebindIteration.class);
    
    public InitialFullRebindIteration(RebindManagerImpl rebindManager, 
            ManagementNodeState mode,
            ClassLoader classLoader, RebindExceptionHandler exceptionHandler,
            Semaphore rebindActive, AtomicInteger readOnlyRebindCount, PersistenceActivityMetrics rebindMetrics, BrooklynMementoPersister persistenceStoreAccess
            ) {
        super(rebindManager, mode, classLoader, exceptionHandler, rebindActive, readOnlyRebindCount, rebindMetrics, persistenceStoreAccess);
    }

    @Override
    protected boolean isRebindingActiveAgain() {
        return false;
    }
    
    @Override
    protected void doRun() throws Exception {
        LOG.debug("Rebinding ("+mode+
            (readOnlyRebindCount.get()>Integer.MIN_VALUE ? ", iteration "+readOnlyRebindCount : "")+
            ") from "+rebindManager.getPersister().getBackingStoreDescription()+"...");

        super.doRun();
    }

    @Override
    protected void loadManifestFiles() throws Exception {
        checkEnteringPhase(1);
        Preconditions.checkState(mementoRawData==null, "Memento raw data should not yet be set when calling this");
        mementoRawData = persistenceStoreAccess.loadMementoRawData(exceptionHandler);
        
        preprocessManifestFiles();
        
        if (!isEmpty) {
            if (!ManagementNodeState.isHotProxy(mode) || readOnlyRebindCount.get()==1) {
                LOG.info("Rebinding from "+getPersister().getBackingStoreDescription()+" for "+Strings.toLowerCase(Strings.toString(mode))+" "+managementContext.getManagementNodeId()+"...");
            }
        } else {
            if (!ManagementNodeState.isHotProxy(mode)) {
                LOG.info("Rebind check: no existing state; will persist new items to "+getPersister().getBackingStoreDescription());
            }
        }
        if (!ManagementNodeState.isHotProxy(mode)) {
            if (!managementContext.getEntityManager().getEntities().isEmpty() || !managementContext.getLocationManager().getLocations().isEmpty()) {
                // this is discouraged if we were already master
                Entity anEntity = Iterables.getFirst(managementContext.getEntityManager().getEntities(), null);
                if (anEntity!=null && !((EntityInternal)anEntity).getManagementSupport().isReadOnly()) {
                    // NB: there is some complexity which can happen in this situation; "initial-full" rebind really expected everything is being
                    // initially bound from persisted state, and completely so; "active-partial" is much more forgiving.
                    // one big difference is in behaviour of management: it is recursive for most situations, and initial-full assumes recursive,
                    // but primary-primary is *not* recursive;
                    // as a result some "new" entities created during rebind might be left unmanaged; they should get GC'd,
                    // but it's possible the new entity impl or even a new proxy could leak.
                    // see HighAvailabilityManagerInMemoryTest.testLocationsStillManagedCorrectlyAfterDoublePromotion
                    // (i've plugged the known such leaks, but still something to watch out for); -Alex 2015-02
                    overwritingMaster = true;
                    LOG.warn("Rebind requested for "+mode+" node "+managementContext.getManagementNodeId()+" "
                        + "when it already has active state; discouraged, "
                        + "will likely overwrite: "+managementContext.getEntityManager().getEntities()+" and "+managementContext.getLocationManager().getLocations()+" and more");
                }
            }
        }
    }

    @Override
    protected void cleanupOldLocations(Set<String> oldLocations) {
        LocationManagerInternal locationManager = (LocationManagerInternal)managementContext.getLocationManager();
        if (!oldLocations.isEmpty()) BrooklynLogging.log(LOG, overwritingMaster ? BrooklynLogging.LoggingLevel.WARN : BrooklynLogging.LoggingLevel.DEBUG, 
            "Destroying unused locations on rebind: "+oldLocations);
        for (String oldLocationId: oldLocations) {
           locationManager.unmanage(locationManager.getLocation(oldLocationId), ManagementTransitionMode.guessing(
               BrooklynObjectManagementMode.MANAGED_PRIMARY, BrooklynObjectManagementMode.NONEXISTENT)); 
        }
    }

    @Override
    protected void cleanupOldEntities(Set<String> oldEntities) {
        EntityManagerInternal entityManager = (EntityManagerInternal)managementContext.getEntityManager();
        if (!oldEntities.isEmpty()) BrooklynLogging.log(LOG, overwritingMaster ? BrooklynLogging.LoggingLevel.WARN : BrooklynLogging.LoggingLevel.DEBUG, 
            "Destroying unused entities on rebind: "+oldEntities);
        for (String oldEntityId: oldEntities) {
           entityManager.unmanage(entityManager.getEntity(oldEntityId), ManagementTransitionMode.guessing(
               BrooklynObjectManagementMode.MANAGED_PRIMARY, BrooklynObjectManagementMode.NONEXISTENT));
        }
    }

}
