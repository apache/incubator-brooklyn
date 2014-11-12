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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.CatalogItem;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.BrooklynServerPaths;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.entity.rebind.transformer.CompoundTransformer;
import brooklyn.entity.rebind.transformer.CompoundTransformerLoader;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersisterToObjectStore;
import brooklyn.management.ha.MementoCopyMode;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.mementos.Memento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

public class BrooklynPersistenceUtils {

    private static final Logger log = LoggerFactory.getLogger(BrooklynPersistenceUtils.class);
    
    @Beta
    public static final List<BrooklynObjectType> STANDARD_BROOKLYN_OBJECT_TYPE_PERSISTENCE_ORDER = ImmutableList.of( 
        BrooklynObjectType.ENTITY, BrooklynObjectType.LOCATION, BrooklynObjectType.POLICY,
        BrooklynObjectType.ENRICHER, BrooklynObjectType.FEED, BrooklynObjectType.CATALOG_ITEM);

    /** Creates a {@link PersistenceObjectStore} for general-purpose use. */
    public static PersistenceObjectStore newPersistenceObjectStore(ManagementContext managementContext,
            String locationSpec, String locationContainer) {
        
        return newPersistenceObjectStore(managementContext, locationSpec, locationContainer,
            PersistMode.AUTO, HighAvailabilityMode.STANDBY);
    }
    
    /** Creates a {@link PersistenceObjectStore} for use with a specified set of modes. */
    public static PersistenceObjectStore newPersistenceObjectStore(ManagementContext managementContext,
            String locationSpec, String locationContainer, PersistMode persistMode, HighAvailabilityMode highAvailabilityMode) {
        PersistenceObjectStore destinationObjectStore;
        locationContainer = BrooklynServerPaths.newMainPersistencePathResolver(managementContext).location(locationSpec).dir(locationContainer).resolve();

        Location location = null;
        try {
            if (Strings.isBlank(locationSpec)) {
                location = managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
            } else {
                location = managementContext.getLocationRegistry().resolve(locationSpec);
                if (!(location instanceof LocationWithObjectStore)) {
                    throw new IllegalArgumentException("Destination location "+location+" does not offer a persistent store");
                }
            }
        } finally {
            if (location!=null) managementContext.getLocationManager().unmanage(location);
        }
        destinationObjectStore = ((LocationWithObjectStore)location).newPersistenceObjectStore(locationContainer);
        
        destinationObjectStore.injectManagementContext(managementContext);
        destinationObjectStore.prepareForSharedUse(persistMode, highAvailabilityMode);
        return destinationObjectStore;
    }

    public static void writeMemento(ManagementContext managementContext, BrooklynMementoRawData memento,
            PersistenceObjectStore destinationObjectStore) {
        BrooklynMementoPersisterToObjectStore persister = new BrooklynMementoPersisterToObjectStore(
            destinationObjectStore,
            ((ManagementContextInternal)managementContext).getBrooklynProperties(),
            managementContext.getCatalog().getRootClassLoader());
        PersistenceExceptionHandler exceptionHandler = PersistenceExceptionHandlerImpl.builder().build();
        persister.enableWriteAccess();
        persister.checkpoint(memento, exceptionHandler);
    }

    public static void writeManagerMemento(ManagementContext managementContext, ManagementPlaneSyncRecord optionalPlaneRecord,
            PersistenceObjectStore destinationObjectStore) {
        if (optionalPlaneRecord != null) {
            ManagementPlaneSyncRecordPersisterToObjectStore managementPersister = new ManagementPlaneSyncRecordPersisterToObjectStore(
                    managementContext, destinationObjectStore, managementContext.getCatalog().getRootClassLoader());
            managementPersister.checkpoint(optionalPlaneRecord);
        }
    }

    public static CompoundTransformer loadTransformer(ResourceUtils resources, String transformationsFileUrl) {
        if (Strings.isBlank(transformationsFileUrl)) {
            return CompoundTransformer.NOOP; 
        } else {
            String contents = resources.getResourceAsString(transformationsFileUrl);
            return CompoundTransformerLoader.load(contents);
        }
    }

    public static Memento newObjectMemento(BrooklynObject instance) {
        return MementosGenerators.newMemento(instance);
    }
    
    public static BrooklynMementoRawData newStateMemento(ManagementContext mgmt, MementoCopyMode source) {
        switch (source) {
        case LOCAL: 
            return newStateMementoFromLocal(mgmt); 
        case REMOTE: 
            return mgmt.getRebindManager().retrieveMementoRawData(); 
        case AUTO: 
            throw new IllegalStateException("Copy mode AUTO not supported here");
        }
        throw new IllegalStateException("Should not come here, unknown mode "+source);
    }
    
    public static ManagementPlaneSyncRecord newManagerMemento(ManagementContext mgmt, MementoCopyMode source) {
        switch (source) {
        case LOCAL: 
            return mgmt.getHighAvailabilityManager().getLastManagementPlaneSyncRecord();
        case REMOTE: 
            return mgmt.getHighAvailabilityManager().loadManagementPlaneSyncRecord(true);
        case AUTO: 
            throw new IllegalStateException("Copy mode AUTO not supported here");
        }
        throw new IllegalStateException("Should not come here, unknown mode "+source);
    }
    

    private static BrooklynMementoRawData newStateMementoFromLocal(ManagementContext mgmt) {
        BrooklynMementoRawData.Builder result = BrooklynMementoRawData.builder();
        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(mgmt.getClass().getClassLoader());
        RetryingMementoSerializer<Object> serializer = new RetryingMementoSerializer<Object>(rawSerializer, 1);
        
        for (Location instance: mgmt.getLocationManager().getLocations())
            result.location(instance.getId(), serializer.toString(newObjectMemento(instance)));
        for (Entity instance: mgmt.getEntityManager().getEntities()) {
            instance = Entities.deproxy(instance);
            result.entity(instance.getId(), serializer.toString(newObjectMemento(instance)));
            for (Feed instanceAdjunct: ((EntityInternal)instance).feeds().getFeeds())
                result.feed(instanceAdjunct.getId(), serializer.toString(newObjectMemento(instanceAdjunct)));
            for (Enricher instanceAdjunct: instance.getEnrichers())
                result.enricher(instanceAdjunct.getId(), serializer.toString(newObjectMemento(instanceAdjunct)));
            for (Policy instanceAdjunct: instance.getPolicies())
                result.policy(instanceAdjunct.getId(), serializer.toString(newObjectMemento(instanceAdjunct)));
        }
        for (CatalogItem<?,?> instance: mgmt.getCatalog().getCatalogItems())
            result.catalogItem(instance.getId(), serializer.toString(newObjectMemento(instance)));
        
        return result.build();
    }

    /** generates and writes mementos for the given mgmt context to the given targetStore;
     * this may be taken from {@link MementoCopyMode#LOCAL} current state 
     * or {@link MementoCopyMode#REMOTE} persisted state, or the default {@link MementoCopyMode#AUTO} detected
     */
    public static void writeMemento(ManagementContext mgmt, PersistenceObjectStore targetStore, MementoCopyMode source) {
        if (source==null || source==MementoCopyMode.AUTO) 
            source = (mgmt.getHighAvailabilityManager().getNodeState()==ManagementNodeState.MASTER ? MementoCopyMode.LOCAL : MementoCopyMode.REMOTE);

        Stopwatch timer = Stopwatch.createStarted();
        
        BrooklynMementoRawData dataRecord = newStateMemento(mgmt, source); 
        ManagementPlaneSyncRecord mgmtRecord = newManagerMemento(mgmt, source);

        writeMemento(mgmt, dataRecord, targetStore);
        writeManagerMemento(mgmt, mgmtRecord, targetStore);
        
        log.debug("Wrote full memento to "+targetStore+" in "+Time.makeTimeStringRounded(Duration.of(timer)));
    }

    public static void createBackup(ManagementContext managementContext, String label, MementoCopyMode source) {
        if (source==null || source==MementoCopyMode.AUTO) {
            if ("promotion".equalsIgnoreCase(label)) source = MementoCopyMode.REMOTE;
            else if ("demotion".equalsIgnoreCase(label)) source = MementoCopyMode.LOCAL;
            else throw new IllegalArgumentException("Cannot detect copy mode for "+label+"/"+source);
        }
        BrooklynMementoRawData memento = null;
        ManagementPlaneSyncRecord planeState = null;
        
        try {
            log.debug("Loading persisted state on "+label+" for backup purposes");
            memento = newStateMemento(managementContext, source);
            try {
                planeState = newManagerMemento(managementContext, source);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.warn("Unable to access management plane sync state on "+label+" (ignoring): "+e, e);
            }
        
            PersistenceObjectStore destinationObjectStore = null;
            String backupSpec = managementContext.getConfig().getConfig(BrooklynServerConfig.PERSISTENCE_BACKUPS_LOCATION_SPEC);
            try {
                String backupContainer = BrooklynServerPaths.newBackupPersistencePathResolver(managementContext).location(backupSpec)
                    .resolveWithSubpathFor(managementContext, label);
                destinationObjectStore = BrooklynPersistenceUtils.newPersistenceObjectStore(managementContext, backupSpec, backupContainer);
                log.debug("Backing up persisted state on "+label+", to "+destinationObjectStore.getSummaryName());
                BrooklynPersistenceUtils.writeMemento(managementContext, memento, destinationObjectStore);
                BrooklynPersistenceUtils.writeManagerMemento(managementContext, planeState, destinationObjectStore);
                log.info("Back-up of persisted state created on "+label+", in "+destinationObjectStore.getSummaryName());
                
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                PersistenceObjectStore failedStore = destinationObjectStore;
                if (!Strings.isBlank(backupSpec) && !"localhost".equals(backupSpec)) {
                    backupSpec = "localhost";
                    String backupContainer = BrooklynServerPaths.newBackupPersistencePathResolver(managementContext).location(backupSpec)
                        .resolveWithSubpathFor(managementContext, label);
                    destinationObjectStore = BrooklynPersistenceUtils.newPersistenceObjectStore(managementContext, backupSpec, backupContainer);
                    log.warn("Persisted state back-up to "+failedStore.getSummaryName()+" failed with "+e, e);
                    
                    log.debug("Backing up persisted state on "+label+", locally because remote failed, to "+destinationObjectStore.getSummaryName());
                    BrooklynPersistenceUtils.writeMemento(managementContext, memento, destinationObjectStore);
                    BrooklynPersistenceUtils.writeManagerMemento(managementContext, planeState, destinationObjectStore);
                    log.info("Back-up of persisted state created on "+label+", locally because remote failed, in "+destinationObjectStore.getSummaryName());
                }
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Unable to backup management plane sync state on "+label+" (ignoring): "+e, e);
        }
    }
}
