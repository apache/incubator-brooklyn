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

import brooklyn.entity.Entity;
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
    }

    BrooklynMementoManifest loadMementoManifest(RebindExceptionHandler exceptionHandler) throws IOException;

    /**
     * Note that this method is *not* thread safe.
     */
    BrooklynMemento loadMemento(LookupContext lookupContext, RebindExceptionHandler exceptionHandler) throws IOException;
    
    void checkpoint(BrooklynMemento memento, PersistenceExceptionHandler exceptionHandler);

    void delta(Delta delta, PersistenceExceptionHandler exceptionHandler);

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
        Collection<String> removedLocationIds();
        Collection<String> removedEntityIds();
        Collection<String> removedPolicyIds();
        Collection<String> removedEnricherIds();
    }

}
