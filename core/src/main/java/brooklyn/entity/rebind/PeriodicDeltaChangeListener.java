package brooklyn.entity.rebind;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
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
        Set<String> removedLocationIds = Sets.newLinkedHashSet();
        Set<String> removedEntityIds = Sets.newLinkedHashSet();
        Set<String> removedPolicyIds = Sets.newLinkedHashSet();
        Set<String> removedEnricherIds = Sets.newLinkedHashSet();
        
        public boolean isEmpty() {
            return locations.isEmpty() && entities.isEmpty() && policies.isEmpty() && enrichers.isEmpty() &&
                    removedEntityIds.isEmpty() && removedLocationIds.isEmpty() && removedPolicyIds.isEmpty() && removedEnricherIds.isEmpty();
        }
    }
    
    private final ExecutionManager executionManager;
    
    private final BrooklynMementoPersister persister;

    private final Duration period;
    
    private final AtomicLong writeCount = new AtomicLong();
    
    private DeltaCollector deltaCollector = new DeltaCollector();

    private volatile boolean running = false;

    private volatile boolean stopped = false;

    private volatile ScheduledTask scheduledTask;

    private final boolean persistPoliciesEnabled;
    private final boolean persistEnrichersEnabled;
    
    public PeriodicDeltaChangeListener(ExecutionManager executionManager, BrooklynMementoPersister persister, long periodMillis) {
        this.executionManager = executionManager;
        this.persister = persister;
        this.period = Duration.of(periodMillis, TimeUnit.MILLISECONDS);
        
        this.persistPoliciesEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_POLICY_PERSISTENCE_PROPERTY);
        this.persistEnrichersEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_ENRICHER_PERSISTENCE_PROPERTY);
    }
    
    public void start() {
        running = true;
        
        Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
            @Override public Task<Void> call() {
                return new BasicTask<Void>(new Callable<Void>() {
                    public Void call() {
                        try {
                            persistNow();
                            return null;
                        } catch (Exception e) {
                            // Don't rethrow: the behaviour of executionManager is different from a scheduledExecutorService,
                            // if we throw an exception, then our task will never get executed again
                            LOG.warn("Problem persisting change-delta", e);
                            return null;
                        } catch (Throwable t) {
                            LOG.warn("Problem persisting change-delta (rethrowing)", t);
                            throw Exceptions.propagate(t);
                        }
                    }});
            }
        };
        scheduledTask = (ScheduledTask) executionManager.submit(new ScheduledTask(taskFactory).period(period));
    }
    
    void stop() {
        stopped = true;
        running = false;
        if (scheduledTask != null) scheduledTask.cancel();

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
        return stopped || executionManager.isShutdown();
    }
    
    private void persistNow() {
        if (isActive()) {
            try {
                // Atomically switch the delta, so subsequent modifications will be done in the
                // next scheduled persist
                DeltaCollector prevDeltaCollector;
                synchronized (this) {
                    prevDeltaCollector = deltaCollector;
                    deltaCollector = new DeltaCollector();
                }
                
                // Generate mementos for everything that has changed in this time period
                if (prevDeltaCollector.isEmpty()) {
                    if (LOG.isTraceEnabled()) LOG.trace("No changes to persist since last delta");
                } else {
                    PersisterDeltaImpl persisterDelta = new PersisterDeltaImpl();
                    for (Location location : prevDeltaCollector.locations) {
                        try {
                            persisterDelta.locations.add(((LocationInternal)location).getRebindSupport().getMemento());
                        } catch (Exception e) {
                            handleGenerateMementoException(e, "location "+location.getClass().getSimpleName()+"("+location.getId()+")");
                        }
                    }
                    for (Entity entity : prevDeltaCollector.entities) {
                        try {
                            persisterDelta.entities.add(((EntityInternal)entity).getRebindSupport().getMemento());
                        } catch (Exception e) {
                            handleGenerateMementoException(e, "entity "+entity.getEntityType().getSimpleName()+"("+entity.getId()+")");
                        }
                    }
                    for (Policy policy : prevDeltaCollector.policies) {
                        try {
                            persisterDelta.policies.add(policy.getRebindSupport().getMemento());
                        } catch (Exception e) {
                            handleGenerateMementoException(e, "policy "+policy.getClass().getSimpleName()+"("+policy.getId()+")");
                        }
                    }
                    for (Enricher enricher : prevDeltaCollector.enrichers) {
                        try {
                            persisterDelta.enrichers.add(enricher.getRebindSupport().getMemento());
                        } catch (Exception e) {
                            handleGenerateMementoException(e, "enricher "+enricher.getClass().getSimpleName()+"("+enricher.getId()+")");
                        }
                    }
                    persisterDelta.removedLocationIds = prevDeltaCollector.removedLocationIds;
                    persisterDelta.removedEntityIds = prevDeltaCollector.removedEntityIds;
                    persisterDelta.removedPolicyIds = prevDeltaCollector.removedPolicyIds;
                    persisterDelta.removedEnricherIds = prevDeltaCollector.removedEnricherIds;
                    
                    /*
                     * Need to guarantee "happens before", with any thread that subsequently reads
                     * the mementos.
                     * 
                     * See MementoFileWriter.writeNow for the corresponding synchronization,
                     * that guarantees its thread has values visible for reads.
                     */
                    synchronized (new Object()) {}

                    // Tell the persister to persist it
                    persister.delta(persisterDelta);
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
            }
        }
    }
    
    protected void handleGenerateMementoException(Exception e, String context) {
        Exceptions.propagateIfFatal(e);
        if (isActive()) {
            LOG.warn("Problem generating memento for "+context, e);
        } else {
            LOG.debug("Problem generating memento for "+context+", but no longer active (ignoring)", e);
        }
    }
    
    @Override
    public synchronized void onManaged(Entity entity) {
        if (LOG.isTraceEnabled()) LOG.trace("onManaged: {}", entity);
        onChanged(entity);
    }

    @Override
    public synchronized void onManaged(Location location) {
        if (LOG.isTraceEnabled()) LOG.trace("onManaged: {}", location);
        onChanged(location);
    }
    
    @Override
    public synchronized void onManaged(Policy policy) {
        if (LOG.isTraceEnabled()) LOG.trace("onManaged: {}", policy);
        onChanged(policy);
    }
    
    @Override
    public synchronized void onManaged(Enricher enricher) {
        if (LOG.isTraceEnabled()) LOG.trace("onManaged: {}", enricher);
        onChanged(enricher);
    }
    
    @Override
    public synchronized void onChanged(Entity entity) {
        if (LOG.isTraceEnabled()) LOG.trace("onChanged: {}", entity);
        if (!isStopped()) {
            deltaCollector.entities.add(entity);

            // FIXME How to let the policy/location tell us about changes? Don't do this every time!
            for (Location location : entity.getLocations()) {
                deltaCollector.locations.addAll(TreeUtils.findLocationsInHierarchy(location));
            }

            if (persistPoliciesEnabled) {
                for (Policy policy : entity.getPolicies()) {
                    deltaCollector.policies.add(policy);
                }
            }

            if (persistEnrichersEnabled) {
                for (Enricher enricher : entity.getEnrichers()) {
                    deltaCollector.enrichers.add(enricher);
                }
            }
        }
    }
    
    @Override
    public synchronized void onUnmanaged(Entity entity) {
        if (LOG.isTraceEnabled()) LOG.trace("onUnmanaged: {}", entity);
        if (!isStopped()) {
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
        }
    }

    @Override
    public synchronized void onUnmanaged(Location location) {
        if (LOG.isTraceEnabled()) LOG.trace("onUnmanaged: {}", location);
        if (!isStopped()) {
            deltaCollector.removedLocationIds.add(location.getId());
            deltaCollector.locations.remove(location);
        }
    }

    @Override
    public synchronized void onUnmanaged(Policy policy) {
        if (LOG.isTraceEnabled()) LOG.trace("onUnmanaged: {}", policy);
        if (!isStopped()) {
            deltaCollector.removedPolicyIds.add(policy.getId());
            deltaCollector.policies.remove(policy);
        }
    }

    @Override
    public synchronized void onUnmanaged(Enricher enricher) {
        if (LOG.isTraceEnabled()) LOG.trace("onUnmanaged: {}", enricher);
        if (!isStopped()) {
            deltaCollector.removedEnricherIds.add(enricher.getId());
            deltaCollector.enrichers.remove(enricher);
        }
    }

    @Override
    public synchronized void onChanged(Location location) {
        if (LOG.isTraceEnabled()) LOG.trace("onChanged: {}", location);
        if (!isStopped()) {
            deltaCollector.locations.add(location);
        }
    }
    
    @Override
    public synchronized void onChanged(Policy policy) {
        if (LOG.isTraceEnabled()) LOG.trace("onChanged: {}", policy);
        if (!isStopped()) {
            deltaCollector.policies.add(policy);
        }
    }
    
    @Override
    public synchronized void onChanged(Enricher enricher) {
        if (LOG.isTraceEnabled()) LOG.trace("onChanged: {}", enricher);
        if (!isStopped()) {
            deltaCollector.enrichers.add(enricher);
        }
    }
}
