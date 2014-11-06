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

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;

/**
 * Controls the persisting and reading back of mementos. Used by {@link RebindManager} 
 * to support brooklyn restart.
 */
public interface BrooklynMementoPersister {

    public static interface LookupContext {
        ManagementContext lookupManagementContext();
        Entity lookupEntity(String id);
        Location lookupLocation(String id);
        Policy lookupPolicy(String id);
        Enricher lookupEnricher(String id);
        Feed lookupFeed(String id);
        CatalogItem<?, ?> lookupCatalogItem(String id);
    }
    
    /**
     * Loads raw data contents of the mementos.
     * <p>
     * Some classes (esp deprecated ones) may return null here,
     * meaning that the {@link #loadMementoManifest(BrooklynMementoRawData, RebindExceptionHandler)}
     * and {@link #loadMemento(BrooklynMementoRawData, LookupContext, RebindExceptionHandler)} methods
     * will populate the raw data via another source.
     */
    BrooklynMementoRawData loadMementoRawData(RebindExceptionHandler exceptionHandler);

    /** @deprecated since 0.7.0 use {@link #loadMementoManifest(BrooklynMementoRawData, RebindExceptionHandler)} */
    BrooklynMementoManifest loadMementoManifest(RebindExceptionHandler exceptionHandler) throws IOException;
    
    /**
     * Loads minimal manifest information (almost entirely *not* deserialized).
     * Implementations should load the raw data if {@link BrooklynMementoRawData} is not supplied,
     * but callers are encouraged to supply that for optimal performance.
     */
    BrooklynMementoManifest loadMementoManifest(@Nullable BrooklynMementoRawData mementoData, RebindExceptionHandler exceptionHandler) throws IOException;

    /** @deprecated since 0.7.0 use {@link #loadMemento(RebindExceptionHandler)} */
    BrooklynMemento loadMemento(LookupContext lookupContext, RebindExceptionHandler exceptionHandler) throws IOException;
     /**
      * Retrieves the memento class, containing deserialized objects (but not the {@link BrooklynObject} class).
      * Implementations should load the raw data if {@link BrooklynMementoRawData} is not supplied,
      * but callers are encouraged to supply that for optimal performance.
      * <p>
      * Note that this method is *not* thread safe.
      */
    BrooklynMemento loadMemento(@Nullable BrooklynMementoRawData mementoData, LookupContext lookupContext, RebindExceptionHandler exceptionHandler) throws IOException;
    
    void checkpoint(BrooklynMemento memento, PersistenceExceptionHandler exceptionHandler);

    void delta(Delta delta, PersistenceExceptionHandler exceptionHandler);

    void enableWriteAccess();
    void disableWriteAccess(boolean graceful);
    /** permanently shuts down all access to the remote store */
    void stop(boolean graceful);

    @VisibleForTesting
    void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException;

    /**
     * @deprecated since 0.7.0; use {@link #waitForWritesCompleted(Duration)}
     */
    @VisibleForTesting
    void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException;

    String getBackingStoreDescription();
    
    public interface Delta {
        Collection<LocationMemento> locations();
        Collection<EntityMemento> entities();
        Collection<PolicyMemento> policies();
        Collection<EnricherMemento> enrichers();
        Collection<FeedMemento> feeds();
        Collection<CatalogItemMemento> catalogItems();
        
        Collection<String> removedLocationIds();
        Collection<String> removedEntityIds();
        Collection<String> removedPolicyIds();
        Collection<String> removedEnricherIds();
        Collection<String> removedFeedIds();
        Collection<String> removedCatalogItemIds();
        
        Collection<? extends Memento> getObjectsOfType(BrooklynObjectType type);
        Collection<String> getRemovedObjectsOfType(BrooklynObjectType type);
    }

}
