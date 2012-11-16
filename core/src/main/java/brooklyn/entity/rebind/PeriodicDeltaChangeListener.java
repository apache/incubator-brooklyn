package brooklyn.entity.rebind;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.policy.Policy;
import brooklyn.util.Time;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ScheduledTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
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
        Set<String> removedLocationIds = Sets.newLinkedHashSet();
        Set<String> removedEntityIds = Sets.newLinkedHashSet();
        Set<String> removedPolicyIds = Sets.newLinkedHashSet();
        
        public boolean isEmpty() {
            return locations.isEmpty() && entities.isEmpty() && policies.isEmpty() && 
                    removedEntityIds.isEmpty() && removedLocationIds.isEmpty() && removedPolicyIds.isEmpty();
        }
    }
    
    private final ExecutionManager executionManager;
    
    private final BrooklynMementoPersister persister;

    private final AtomicLong writeCount = new AtomicLong();
    
    private DeltaCollector deltaCollector = new DeltaCollector();

    private volatile boolean running = true;

    public PeriodicDeltaChangeListener(ExecutionManager executionManager, BrooklynMementoPersister persister, long periodMillis) {
        this.executionManager = executionManager;
        this.persister = persister;
        
        Callable<Task> taskFactory = new Callable<Task>() {
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
                            throw Throwables.propagate(t);
                        }
                    }});
            }
        };
        ScheduledTask scheduledTask = new ScheduledTask(taskFactory).period(periodMillis);
        executionManager.submit(scheduledTask);
    }
    
    void stop() {
        running = false;
    }
    
    /**
     * This method must only be used for testing. If required in production, then revisit implementation!
     */
    @VisibleForTesting
    public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        // Every time we finish writing, we increment a counter. We note the current val, and then
        // wait until we can guarantee that a complete additional write has been done. Not sufficient
        // to wait for `writeCount > origWriteCount` because we might have read the value when almost 
        // finished a write.
        
        long startTime = System.currentTimeMillis();
        long maxEndtime = (timeout > 0) ? (startTime + unit.toMillis(timeout)) : Long.MAX_VALUE;
        long origWriteCount = writeCount.get();
        while (true) {
            if (!isActive()) {
                return; // no pending activity;
            } else if (writeCount.get() > (origWriteCount+1)) {
                return;
            }
            
            if (System.currentTimeMillis() > maxEndtime) {
                throw new TimeoutException("Timeout waiting for pending complete of rebind-periodic-delta, after "+Time.makeTimeString(timeout, unit));
            }
            Thread.sleep(1);
        }
    }

    private boolean isActive() {
        return running && persister != null && !executionManager.isShutdown();
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
                        persisterDelta.locations.add(location.getRebindSupport().getMemento());
                    }
                    for (Entity entity : prevDeltaCollector.entities) {
                        persisterDelta.entities.add(entity.getRebindSupport().getMemento());
                    }
                    for (Policy policy : prevDeltaCollector.policies) {
                        persisterDelta.policies.add(policy.getRebindSupport().getMemento());
                    }
                    persisterDelta.removedLocationIds = prevDeltaCollector.removedLocationIds;
                    persisterDelta.removedEntityIds = prevDeltaCollector.removedEntityIds;
                    persisterDelta.removedPolicyIds = prevDeltaCollector.removedPolicyIds;
                    
                    // Tell the persister to persist it
                    persister.delta(persisterDelta);
                }

            } finally {
                writeCount.incrementAndGet();
            }
        }
    }
    
    @Override
    public synchronized void onManaged(Entity entity) {
        if (isActive()) {
            onChanged(entity);
        }
    }

    @Override
    public synchronized void onManaged(Location location) {
        if (isActive()) {
            onChanged(location);
        }
    }
    
    @Override
    public synchronized void onChanged(Entity entity) {
        if (isActive()) {
            deltaCollector.entities.add(entity);

            // FIXME How to let the policy/location tell us about changes? Don't do this every time!
            for (Location location : entity.getLocations()) {
                deltaCollector.locations.addAll(TreeUtils.findLocationsInHierarchy(location));
            }

            // FIXME Not including policies, because lots of places regiser anonymous inner class policies
            // (e.g. AbstractController registering a AbstractMembershipTrackingPolicy)
            // Also, the entity constructor often re-creates the policy.
            // Also see MementosGenerator.newEntityMementoBuilder()
//            for (Policy policy : entity.getPolicies()) {
//                delta.policies.add(policy);
//            }
        }
    }
    
    @Override
    public synchronized void onUnmanaged(Entity entity) {
        if (isActive()) {
            deltaCollector.removedEntityIds.add(entity.getId());
            deltaCollector.entities.remove(entity);
        }
    }

    @Override
    public synchronized void onUnmanaged(Location location) {
        if (isActive()) {
            deltaCollector.removedLocationIds.add(location.getId());
            deltaCollector.locations.remove(location);
        }
    }

    @Override
    public synchronized void onChanged(Location location) {
        if (isActive()) {
            deltaCollector.locations.add(location);
        }
    }
    
    @Override
    public synchronized void onChanged(Policy policy) {
        if (isActive()) {
            deltaCollector.policies.add(policy);
        }
    }
}
