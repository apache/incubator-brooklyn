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
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationInternal;
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
        Set<Location> locations = Sets.newLinkedHashSet();
        Set<Entity> entities = Sets.newLinkedHashSet();
        Set<Policy> policies = Sets.newLinkedHashSet();
        Set<Enricher> enrichers = Sets.newLinkedHashSet();
        Set<Feed> feeds = Sets.newLinkedHashSet();
        Set<CatalogItem<?, ?>> catalogItems = Sets.newLinkedHashSet();
        Set<String> removedLocationIds = Sets.newLinkedHashSet();
        Set<String> removedEntityIds = Sets.newLinkedHashSet();
        Set<String> removedPolicyIds = Sets.newLinkedHashSet();
        Set<String> removedEnricherIds = Sets.newLinkedHashSet();
        Set<String> removedFeedIds = Sets.newLinkedHashSet();
        Set<String> removedCatalogItemIds = Sets.newLinkedHashSet();

        public boolean isEmpty() {
            return locations.isEmpty() && entities.isEmpty() && policies.isEmpty() && 
                    enrichers.isEmpty() && feeds.isEmpty() &&
                    catalogItems.isEmpty() &&
                    removedEntityIds.isEmpty() && removedLocationIds.isEmpty() && removedPolicyIds.isEmpty() && 
                    removedEnricherIds.isEmpty() && removedFeedIds.isEmpty() &&
                    removedCatalogItemIds.isEmpty();
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
        Set<Location> referencedLocations = Sets.newLinkedHashSet();
        Set<Policy> referencedPolicies = Sets.newLinkedHashSet();
        Set<Enricher> referencedEnrichers = Sets.newLinkedHashSet();
        Set<Feed> referencedFeeds = Sets.newLinkedHashSet();
        
        for (Entity entity : deltaCollector.entities) {
            // FIXME How to let the policy/location tell us about changes? Don't do this every time!
            for (Location location : entity.getLocations()) {
                Collection<Location> findLocationsInHierarchy = TreeUtils.findLocationsInHierarchy(location);
                referencedLocations.addAll(findLocationsInHierarchy);
            }
            if (persistPoliciesEnabled) {
                referencedPolicies.addAll(entity.getPolicies());
            }
            if (persistEnrichersEnabled) {
                referencedEnrichers.addAll(entity.getEnrichers());
            }
            if (persistFeedsEnabled) {
                referencedFeeds.addAll(((EntityInternal)entity).feeds().getFeeds());
            }
        }
        
        for (Location loc : referencedLocations) {
            if (!deltaCollector.removedLocationIds.contains(loc.getId())) {
                deltaCollector.locations.add(loc);
            }
        }
        for (Policy pol : referencedPolicies) {
            if (!deltaCollector.removedPolicyIds.contains(pol.getId())) {
                deltaCollector.policies.add(pol);
            }
        }
        for (Enricher enr : referencedEnrichers) {
            if (!deltaCollector.removedEnricherIds.contains(enr.getId())) {
                deltaCollector.enrichers.add(enr);
            }
        }
        for (Feed feed : referencedFeeds) {
            if (!deltaCollector.removedFeedIds.contains(feed.getId())) {
                deltaCollector.feeds.add(feed);
            }
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
                for (Location location : prevDeltaCollector.locations) {
                    try {
                        persisterDelta.locations.add(((LocationInternal)location).getRebindSupport().getMemento());
                    } catch (Exception e) {
                        exceptionHandler.onGenerateMementoFailed(BrooklynObjectType.LOCATION, location, e);
                    }
                }
                for (Entity entity : prevDeltaCollector.entities) {
                    try {
                        persisterDelta.entities.add(((EntityInternal)entity).getRebindSupport().getMemento());
                    } catch (Exception e) {
                        exceptionHandler.onGenerateMementoFailed(BrooklynObjectType.ENTITY, entity, e);
                    }
                }
                for (Policy policy : prevDeltaCollector.policies) {
                    try {
                        persisterDelta.policies.add(policy.getRebindSupport().getMemento());
                    } catch (Exception e) {
                        exceptionHandler.onGenerateMementoFailed(BrooklynObjectType.POLICY, policy, e);
                    }
                }
                for (Enricher enricher : prevDeltaCollector.enrichers) {
                    try {
                        persisterDelta.enrichers.add(enricher.getRebindSupport().getMemento());
                    } catch (Exception e) {
                        exceptionHandler.onGenerateMementoFailed(BrooklynObjectType.ENRICHER, enricher, e);
                    }
                }
                for (Feed feed : prevDeltaCollector.feeds) {
                    try {
                        persisterDelta.feeds.add(feed.getRebindSupport().getMemento());
                    } catch (Exception e) {
                        exceptionHandler.onGenerateMementoFailed(BrooklynObjectType.FEED, feed, e);
                    }
                }
                for (CatalogItem<?, ?> catalogItem : prevDeltaCollector.catalogItems) {
                    try {
                        persisterDelta.catalogItems.add(catalogItem.getRebindSupport().getMemento());
                    } catch (Exception e) {
                        exceptionHandler.onGenerateMementoFailed(BrooklynObjectType.CATALOG_ITEM, catalogItem, e);
                    }
                }
                persisterDelta.removedLocationIds = prevDeltaCollector.removedLocationIds;
                persisterDelta.removedEntityIds = prevDeltaCollector.removedEntityIds;
                persisterDelta.removedPolicyIds = prevDeltaCollector.removedPolicyIds;
                persisterDelta.removedEnricherIds = prevDeltaCollector.removedEnricherIds;
                persisterDelta.removedFeedIds = prevDeltaCollector.removedFeedIds;
                persisterDelta.removedCatalogItemIds = prevDeltaCollector.removedCatalogItemIds;

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
            if (instance instanceof Entity) {
                Entity entity = (Entity) instance;
                deltaCollector.removedEntityIds.add(entity.getId());
                deltaCollector.entities.remove(entity);
                
                for (Policy policy : entity.getPolicies()) {
                    deltaCollector.removedPolicyIds.add(policy.getId());
                    deltaCollector.policies.remove(policy);
                }
                for (Enricher enricher : entity.getEnrichers()) {
                    deltaCollector.removedEnricherIds.add(enricher.getId());
                    deltaCollector.enrichers.remove(enricher);
                }
                for (Feed feed : ((EntityInternal)entity).feeds().getFeeds()) {
                    deltaCollector.removedFeedIds.add(feed.getId());
                    deltaCollector.feeds.remove(feed);
                }
            } else if (instance instanceof Location) {
                deltaCollector.removedLocationIds.add(instance.getId());
                deltaCollector.locations.remove(instance);
            } else if (instance instanceof Policy) {
                deltaCollector.removedPolicyIds.add(instance.getId());
                deltaCollector.policies.remove(instance);
            } else if (instance instanceof Enricher) {
                deltaCollector.removedEnricherIds.add(instance.getId());
                deltaCollector.enrichers.remove(instance);
            } else if (instance instanceof Feed) {
                deltaCollector.removedFeedIds.add(instance.getId());
                deltaCollector.feeds.remove(instance);
            } else if (instance instanceof CatalogItem) {
                deltaCollector.removedCatalogItemIds.add(instance.getId());
                deltaCollector.catalogItems.remove(instance);
            } else {
                throw new IllegalStateException("Unexpected brooklyn type: "+instance);
            }
        }
    }

    @Override
    public synchronized void onChanged(BrooklynObject instance) {
        if (LOG.isTraceEnabled()) LOG.trace("onChanged: {}", instance);
        if (!isStopped()) {
            if (instance instanceof Entity) {
                deltaCollector.entities.add((Entity)instance);
            } else if (instance instanceof Location) {
                deltaCollector.locations.add((Location) instance);
            } else if (instance instanceof Policy) {
                deltaCollector.policies.add((Policy) instance);
            } else if (instance instanceof Enricher) {
                deltaCollector.enrichers.add((Enricher) instance);
            } else if (instance instanceof Feed) {
                deltaCollector.feeds.add((Feed) instance);
            } else if (instance instanceof CatalogItem) {
                deltaCollector.catalogItems.add((CatalogItem<?,?>) instance);
            } else {
                throw new IllegalStateException("Unexpected brooklyn type: "+instance);
            }
        }
    }
}
