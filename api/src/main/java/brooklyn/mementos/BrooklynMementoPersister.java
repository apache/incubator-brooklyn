package brooklyn.mementos;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.entity.Entity;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.Location;
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
        Collection<EnricherMemento> enrichers();
        Collection<String> removedLocationIds();
        Collection<String> removedEntityIds();
        Collection<String> removedPolicyIds();
        Collection<String> removedEnricherIds();
    }
}
