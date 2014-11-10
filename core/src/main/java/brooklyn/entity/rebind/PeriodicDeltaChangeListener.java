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
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;
import brooklyn.basic.BrooklynObjectInternal;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.persister.BrooklynPersistenceUtils;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.location.Location;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.api.client.util.Lists;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * A "simple" implementation that periodically persists all entities/locations/policies that have changed
 * since the last periodic persistence.
 * 
 * TODO A better implementation would look at a per-entity basis. When the entity was modified, then  
 * schedule a write for that entity in X milliseconds time (if not already scheduled). That would
 * prevent hammering the persister when a bunch of entity attributes change (e.g. when the entity
 * has just polled over JMX/http/etc). Such a scheduled-write approach would be similar to the 
 * Nagle buffering algorithm in TCP (see tcp_nodelay).
 * 
 * @author aled
 *
 */
public class PeriodicDeltaChangeListener implements ChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(PeriodicDeltaChangeListener.class);

    private static class DeltaCollector {
        private Set<Location> locations = Sets.newLinkedHashSet();
        private Set<Entity> entities = Sets.newLinkedHashSet();
        private Set<Policy> policies = Sets.newLinkedHashSet();
        private Set<Enricher> enrichers = Sets.newLinkedHashSet();
        private Set<Feed> feeds = Sets.newLinkedHashSet();
        private Set<CatalogItem<?, ?>> catalogItems = Sets.newLinkedHashSet();
        
        private Set<String> removedLocationIds = Sets.newLinkedHashSet();
        private Set<String> removedEntityIds = Sets.newLinkedHashSet();
        private Set<String> removedPolicyIds = Sets.newLinkedHashSet();
        private Set<String> removedEnricherIds = Sets.newLinkedHashSet();
        private Set<String> removedFeedIds = Sets.newLinkedHashSet();
        private Set<String> removedCatalogItemIds = Sets.newLinkedHashSet();

        public boolean isEmpty() {
            return locations.isEmpty() && entities.isEmpty() && policies.isEmpty() && 
                    enrichers.isEmpty() && feeds.isEmpty() &&
                    catalogItems.isEmpty() &&
                    removedEntityIds.isEmpty() && removedLocationIds.isEmpty() && removedPolicyIds.isEmpty() && 
                    removedEnricherIds.isEmpty() && removedFeedIds.isEmpty() &&
                    removedCatalogItemIds.isEmpty();
        }
        
        public void add(BrooklynObject instance) {
            BrooklynObjectType type = BrooklynObjectType.of(instance);
            getUnsafeCollectionOfType(type).add(instance);
        }
        
        public void addIfNotRemoved(BrooklynObject instance) {
            BrooklynObjectType type = BrooklynObjectType.of(instance);
            if (!getRemovedIdsOfType(type).contains(instance.getId())) {
                getUnsafeCollectionOfType(type).add(instance);
            }
        }

        public void remove(BrooklynObject instance) {
            BrooklynObjectType type = BrooklynObjectType.of(instance);
            getUnsafeCollectionOfType(type).remove(instance);
            getRemovedIdsOfType(type).add(instance.getId());
        }

        @SuppressWarnings("unchecked")
        private Set<BrooklynObject> getUnsafeCollectionOfType(BrooklynObjectType type) {
            return (Set<BrooklynObject>)getCollectionOfType(type);
        }

        private Set<? extends BrooklynObject> getCollectionOfType(BrooklynObjectType type) {
            switch (type) {
            case ENTITY: return entities;
            case LOCATION: return locations;
            case ENRICHER: return enrichers;
            case FEED: return feeds;
            case POLICY: return policies;
            case CATALOG_ITEM: return catalogItems;
            case UNKNOWN: break;
            }
            throw new IllegalStateException("No collection for type "+type);
        }
        
        private Set<String> getRemovedIdsOfType(BrooklynObjectType type) {
            switch (type) {
            case ENTITY: return removedEntityIds;
            case LOCATION: return removedLocationIds;
            case ENRICHER: return removedEnricherIds;
            case FEED: return removedFeedIds;
            case POLICY: return removedPolicyIds;
            case CATALOG_ITEM: return removedCatalogItemIds;
            case UNKNOWN: break;
            }
            throw new IllegalStateException("No removed ids for type "+type);
        }

    }
    
    private final ExecutionContext executionContext;
    
    private final BrooklynMementoPersister persister;

    private final PersistenceExceptionHandler exceptionHandler;
    
    private final Duration period;
    
    private final AtomicLong writeCount = new AtomicLong();
    
    private DeltaCollector deltaCollector = new DeltaCollector();

    private volatile boolean running = false;

    private volatile boolean stopped = false;

    private volatile ScheduledTask scheduledTask;

    private final boolean persistPoliciesEnabled;
    private final boolean persistEnrichersEnabled;
    private final boolean persistFeedsEnabled;
    
    private final Semaphore persistingMutex = new Semaphore(1);
    private final Object startMutex = new Object();
    
    /** @deprecated since 0.7.0 pass in an {@link ExecutionContext} and a {@link Duration} */
    @Deprecated
    public PeriodicDeltaChangeListener(ExecutionManager executionManager, BrooklynMementoPersister persister, PersistenceExceptionHandler exceptionHandler, long periodMillis) {
        this(new BasicExecutionContext(executionManager), persister, exceptionHandler, Duration.of(periodMillis, TimeUnit.MILLISECONDS));
    }
    
    public PeriodicDeltaChangeListener(ExecutionContext executionContext, BrooklynMementoPersister persister, PersistenceExceptionHandler exceptionHandler, Duration period) {
        this.executionContext = executionContext;
        this.persister = persister;
        this.exceptionHandler = exceptionHandler;
        this.period = period;
        
        this.persistPoliciesEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_POLICY_PERSISTENCE_PROPERTY);
        this.persistEnrichersEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_ENRICHER_PERSISTENCE_PROPERTY);
        this.persistFeedsEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_FEED_PERSISTENCE_PROPERTY);
    }
    
    @SuppressWarnings("unchecked")
    public void start() {
        synchronized (startMutex) {
            if (running || (scheduledTask!=null && !scheduledTask.isDone())) {
                LOG.warn("Request to start "+this+" when already running - "+scheduledTask+"; ignoring");
                return;
            }
            stopped = false;
            running = true;

            Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
                @Override public Task<Void> call() {
                    return Tasks.<Void>builder().dynamic(false).name("periodic-persister").body(new Callable<Void>() {
                        public Void call() {
                            try {
                                persistNow();
                                return null;
                            } catch (RuntimeInterruptedException e) {
                                LOG.debug("Interrupted persisting change-delta (rethrowing)", e);
                                Thread.currentThread().interrupt();
                                return null;
                            } catch (Exception e) {
                                // Don't rethrow: the behaviour of executionManager is different from a scheduledExecutorService,
                                // if we throw an exception, then our task will never get executed again
                                LOG.error("Problem persisting change-delta", e);
                                return null;
                            } catch (Throwable t) {
                                LOG.warn("Problem persisting change-delta (rethrowing)", t);
                                throw Exceptions.propagate(t);
                            }
                        }}).build();
                }
            };
            scheduledTask = (ScheduledTask) executionContext.submit(new ScheduledTask(MutableMap.of("displayName", "scheduled[periodic-persister]",
                "tags", MutableSet.of(BrooklynTaskTags.TRANSIENT_TASK_TAG)), taskFactory).period(period));
        }
    }

    /** stops persistence, waiting for it to complete */
    void stop() {
        stop(Duration.TEN_SECONDS, Duration.ONE_SECOND);
    }
    void stop(Duration timeout, Duration graceTimeoutForSubsequentOperations) {
        stopped = true;
        running = false;
        
        if (scheduledTask != null) {
            CountdownTimer expiry = timeout.countdownTimer();
            scheduledTask.cancel(false);
            try {
                waitForPendingComplete(expiry.getDurationRemaining().lowerBound(Duration.ZERO).add(graceTimeoutForSubsequentOperations));
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
            scheduledTask.blockUntilEnded(expiry.getDurationRemaining().lowerBound(Duration.ZERO).add(graceTimeoutForSubsequentOperations));
            scheduledTask.cancel(true);
            boolean reallyEnded = Tasks.blockUntilInternalTasksEnded(scheduledTask, expiry.getDurationRemaining().lowerBound(Duration.ZERO).add(graceTimeoutForSubsequentOperations));
            if (!reallyEnded) {
                LOG.warn("Persistence tasks took too long to complete when stopping persistence (ignoring): "+scheduledTask);
            }
            scheduledTask = null;
        }


        // Discard all state that was waiting to be persisted
        synchronized (this) {
            deltaCollector = new DeltaCollector();
        }
    }
    
    /**
     * This method must only be used for testing. If required in production, then revisit implementation!
     * @deprecated since 0.7.0, use {@link #waitForPendingComplete(Duration)}
     */
    @VisibleForTesting
    public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        waitForPendingComplete(Duration.of(timeout, unit));
    }
    @VisibleForTesting
    public void waitForPendingComplete(Duration timeout) throws InterruptedException, TimeoutException {
        // Every time we finish writing, we increment a counter. We note the current val, and then
        // wait until we can guarantee that a complete additional write has been done. Not sufficient
        // to wait for `writeCount > origWriteCount` because we might have read the value when almost 
        // finished a write.
        
        long startTime = System.currentTimeMillis();
        long maxEndtime = timeout.isPositive() ? startTime + timeout.toMillisecondsRoundingUp() : Long.MAX_VALUE;
        long origWriteCount = writeCount.get();
        while (true) {
            if (!isActive()) {
                return; // no pending activity;
            } else if (writeCount.get() > (origWriteCount+1)) {
                return;
            }
            
            if (System.currentTimeMillis() > maxEndtime) {
                throw new TimeoutException("Timeout waiting for pending complete of rebind-periodic-delta, after "+Time.makeTimeStringRounded(timeout));
            }
            Thread.sleep(1);
        }
    }

    /**
     * Indicates whether to persist things now. Even when not active, we will still store what needs
     * to be persisted unless {@link #isStopped()}.
     */
    private boolean isActive() {
        return running && persister != null && !isStopped();
    }

    /**
     * Whether we have been stopped, in which case will not persist or store anything.
     */
    private boolean isStopped() {
        return stopped || executionContext.isShutdown();
    }
    
    private void addReferencedObjects(DeltaCollector deltaCollector) {
        Set<BrooklynObject> referencedObjects = Sets.newLinkedHashSet();
        
        // collect references
        for (Entity entity : deltaCollector.entities) {
            // FIXME How to let the policy/location tell us about changes? Don't do this every time!
            for (Location location : entity.getLocations()) {
                Collection<Location> findLocationsInHierarchy = TreeUtils.findLocationsInHierarchy(location);
                referencedObjects.addAll(findLocationsInHierarchy);
            }
            if (persistPoliciesEnabled) {
                referencedObjects.addAll(entity.getPolicies());
            }
            if (persistEnrichersEnabled) {
                referencedObjects.addAll(entity.getEnrichers());
            }
            if (persistFeedsEnabled) {
                referencedObjects.addAll(((EntityInternal)entity).feeds().getFeeds());
            }
        }
        
        for (BrooklynObject instance : referencedObjects) {
            deltaCollector.addIfNotRemoved(instance);
        }
    }
    
    @VisibleForTesting
    public void persistNow() {
        if (!isActive()) {
            return;
        }
        try {
            persistingMutex.acquire();
            if (!isActive()) return;
            
            // Atomically switch the delta, so subsequent modifications will be done in the
            // next scheduled persist
            DeltaCollector prevDeltaCollector;
            synchronized (this) {
                prevDeltaCollector = deltaCollector;
                deltaCollector = new DeltaCollector();
            }
            
            if (LOG.isDebugEnabled()) LOG.debug("Checkpointing delta of memento: "
                    + "updating entities={}, locations={}, policies={}, enrichers={}, catalog items={}; "
                    + "removing entities={}, locations={}, policies={}, enrichers={}, catalog items={}",
                    new Object[] {
                        limitedCountString(prevDeltaCollector.entities), limitedCountString(prevDeltaCollector.locations), limitedCountString(prevDeltaCollector.policies), limitedCountString(prevDeltaCollector.enrichers), limitedCountString(prevDeltaCollector.catalogItems), 
                        limitedCountString(prevDeltaCollector.removedEntityIds), limitedCountString(prevDeltaCollector.removedLocationIds), limitedCountString(prevDeltaCollector.removedPolicyIds), limitedCountString(prevDeltaCollector.removedEnricherIds), limitedCountString(prevDeltaCollector.removedCatalogItemIds)});

            addReferencedObjects(prevDeltaCollector);

            if (LOG.isTraceEnabled()) LOG.trace("Checkpointing delta of memento with references: "
                    + "updating {} entities, {} locations, {} policies, {} enrichers, {} catalog items; "
                    + "removing {} entities, {} locations, {} policies, {} enrichers, {} catalog items",
                    new Object[] {
                        prevDeltaCollector.entities.size(), prevDeltaCollector.locations.size(), prevDeltaCollector.policies.size(), prevDeltaCollector.enrichers.size(), prevDeltaCollector.catalogItems.size(),
                        prevDeltaCollector.removedEntityIds.size(), prevDeltaCollector.removedLocationIds.size(), prevDeltaCollector.removedPolicyIds.size(), prevDeltaCollector.removedEnricherIds.size(), prevDeltaCollector.removedCatalogItemIds.size()});

            // Generate mementos for everything that has changed in this time period
            if (prevDeltaCollector.isEmpty()) {
                if (LOG.isTraceEnabled()) LOG.trace("No changes to persist since last delta");
            } else {
                PersisterDeltaImpl persisterDelta = new PersisterDeltaImpl();
                
                for (BrooklynObjectType type: BrooklynPersistenceUtils.STANDARD_BROOKLYN_OBJECT_TYPE_PERSISTENCE_ORDER) {
                    for (BrooklynObject instance: prevDeltaCollector.getCollectionOfType(type)) {
                        try {
                            persisterDelta.add(type, ((BrooklynObjectInternal)instance).getRebindSupport().getMemento());
                        } catch (Exception e) {
                            exceptionHandler.onGenerateMementoFailed(type, instance, e);
                        }
                    }
                }
                for (BrooklynObjectType type: BrooklynPersistenceUtils.STANDARD_BROOKLYN_OBJECT_TYPE_PERSISTENCE_ORDER) {
                    persisterDelta.removed(type, prevDeltaCollector.getRemovedIdsOfType(type));
                }

                /*
                 * Need to guarantee "happens before", with any thread that subsequently reads
                 * the mementos.
                 * 
                 * See MementoFileWriter.writeNow for the corresponding synchronization,
                 * that guarantees its thread has values visible for reads.
                 */
                synchronized (new Object()) {}

                // Tell the persister to persist it
                persister.delta(persisterDelta, exceptionHandler);
            }
        } catch (Exception e) {
            if (isActive()) {
                throw Exceptions.propagate(e);
            } else {
                Exceptions.propagateIfFatal(e);
                LOG.debug("Problem persisting, but no longer active (ignoring)", e);
            }
        } finally {
            writeCount.incrementAndGet();
            persistingMutex.release();
        }
    }
    
    private static String limitedCountString(Collection<?> items) {
        if (items==null) return null;
        int size = items.size();
        if (size==0) return "[]";
        
        int MAX = 12;
        
        if (size<=MAX) return items.toString();
        List<Object> itemsTruncated = Lists.newArrayList(Iterables.limit(items, MAX));
        if (items.size()>itemsTruncated.size()) itemsTruncated.add("... ("+(size-MAX)+" more)");
        return itemsTruncated.toString();
    }

    @Override
    public synchronized void onManaged(BrooklynObject instance) {
        if (LOG.isTraceEnabled()) LOG.trace("onManaged: {}", instance);
        onChanged(instance);
    }

    @Override
    public synchronized void onUnmanaged(BrooklynObject instance) {
        if (LOG.isTraceEnabled()) LOG.trace("onUnmanaged: {}", instance);
        if (!isStopped()) {
            removeFromCollector(instance);
            if (instance instanceof Entity) {
                Entity entity = (Entity) instance;
                for (BrooklynObject adjunct : entity.getPolicies()) removeFromCollector(adjunct);
                for (BrooklynObject adjunct : entity.getEnrichers()) removeFromCollector(adjunct);
                for (BrooklynObject adjunct : ((EntityInternal)entity).feeds().getFeeds()) removeFromCollector(adjunct);
            }
        }
    }
    
    private void removeFromCollector(BrooklynObject instance) {
        deltaCollector.remove(instance);
    }

    @Override
    public synchronized void onChanged(BrooklynObject instance) {
        if (LOG.isTraceEnabled()) LOG.trace("onChanged: {}", instance);
        if (!isStopped()) {
            deltaCollector.add(instance);
        }
    }
    
    public PersistenceExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }
    
}
