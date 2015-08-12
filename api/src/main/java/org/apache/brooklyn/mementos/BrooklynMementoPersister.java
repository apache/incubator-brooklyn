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
package org.apache.brooklyn.mementos;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import brooklyn.basic.BrooklynObject;

import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.policy.Policy;

import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.Location;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
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
        
        BrooklynObject lookup(BrooklynObjectType type, String objectId);
        /** like {@link #lookup(BrooklynObjectType, String)} but doesn't record an exception if not found */
        BrooklynObject peek(BrooklynObjectType type, String objectId);
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

    /**
     * Loads minimal manifest information (almost entirely *not* deserialized).
     * Implementations should load the raw data if {@link BrooklynMementoRawData} is not supplied,
     * but callers are encouraged to supply that for optimal performance.
     */
    BrooklynMementoManifest loadMementoManifest(@Nullable BrooklynMementoRawData mementoData, RebindExceptionHandler exceptionHandler) throws IOException;

     /**
      * Retrieves the memento class, containing deserialized objects (but not the {@link BrooklynObject} class).
      * Implementations should load the raw data if {@link BrooklynMementoRawData} is not supplied,
      * but callers are encouraged to supply that for optimal performance.
      * <p>
      * Note that this method is *not* thread safe.
      */
    BrooklynMemento loadMemento(@Nullable BrooklynMementoRawData mementoData, LookupContext lookupContext, RebindExceptionHandler exceptionHandler) throws IOException;

    /** applies a full checkpoint (write) of all state */  
    void checkpoint(BrooklynMementoRawData newMemento, PersistenceExceptionHandler exceptionHandler);
    /** applies a partial write of state delta */  
    void delta(Delta delta, PersistenceExceptionHandler exceptionHandler);
    /** inserts an additional delta to be written on the next delta request */
    @Beta
    void queueDelta(Delta delta);

    void enableWriteAccess();
    void disableWriteAccess(boolean graceful);
    /** permanently shuts down all access to the remote store */
    void stop(boolean graceful);

    @VisibleForTesting
    void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException;

    String getBackingStoreDescription();
    
    /** All methods on this interface are unmodifiable by the caller. Sub-interfaces may introduce modifiers. */
    // NB: the type-specific methods aren't actually used anymore; we could remove them to simplify the impl (and use a multiset there)
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
        Collection<String> getRemovedIdsOfType(BrooklynObjectType type);
    }
    
    @Beta
    public interface MutableDelta extends Delta {
        void add(BrooklynObjectType type, Memento memento);
        void addAll(BrooklynObjectType type, Iterable<? extends Memento> memento);
        void removed(BrooklynObjectType type, Set<String> removedIdsOfType);
    }

}
