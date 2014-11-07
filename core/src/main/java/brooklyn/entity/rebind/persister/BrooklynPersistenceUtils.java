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

import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.transformer.CompoundTransformer;
import brooklyn.entity.rebind.transformer.CompoundTransformerLoader;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersisterToObjectStore;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.util.ResourceUtils;
import brooklyn.util.text.Strings;

public class BrooklynPersistenceUtils {

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
        locationContainer = BrooklynServerConfig.resolvePersistencePath(locationContainer, managementContext.getConfig(), locationSpec);

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

}
