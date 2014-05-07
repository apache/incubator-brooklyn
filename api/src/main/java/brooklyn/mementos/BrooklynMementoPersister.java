package brooklyn.mementos;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.entity.Entity;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.Location;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;

/**
 * Controls the persisting and reading back of mementos. Used by {@link RebindManager} 
 * to support brooklyn restart.
 */
public interface BrooklynMementoPersister {

    public static interface LookupContext {
        Entity lookupEntity(Class<?> type, String id);
        Location lookupLocation(Class<?> type, String id);
    }
    
    /**
     * Note that this method is *not* thread safe.
     */
    BrooklynMemento loadMemento(LookupContext lookupContext) throws IOException;
    
    void checkpoint(BrooklynMemento memento);
    
    void delta(Delta delta);

    void stop();

    @VisibleForTesting
    void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException;

    /**
     * @deprecated since 0.7.0; use {@link #waitForWritesCompleted(Duration)}
     */
    @VisibleForTesting
    void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException;

    public interface Delta {
        Collection<LocationMemento> locations();
        Collection<EntityMemento> entities();
        Collection<PolicyMemento> policies();
        Collection<String> removedLocationIds();
        Collection<String> removedEntityIds();
        Collection<String> removedPolicyIds();
    }
}
