package brooklyn.entity.rebind;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.entity.Application;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;

/**
 * Manages the persisting of brooklyn's state, and recreating that state, e.g. on
 * brooklyn restart.
 * 
 * Users are not expected to implement this class, or to call methods on it directly.
 */
public interface RebindManager {
    
    // FIXME Should we be calling managementContext.getRebindManager().rebind, using a
    // new empty instance of managementContext?
    //
    // Or is that a risky API because you could call it on a non-empty managementContext?
    
    public enum RebindFailureMode {
        FAIL_FAST,
        FAIL_AT_END,
        CONTINUE;
    }
    
    public void setPersister(BrooklynMementoPersister persister);

    @VisibleForTesting
    public BrooklynMementoPersister getPersister();

    /**
     * @deprecated since 0.7; use {@link #rebind(ClassLoader)}
     */
    @Deprecated
    public List<Application> rebind() throws IOException;
    
    public List<Application> rebind(ClassLoader classLoader) throws IOException;

    public List<Application> rebind(ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws IOException;

    public ChangeListener getChangeListener();

    /**
     * Starts the persisting of state (if persister is set; otherwise will start persisting as soon as
     * persister is set). Until {@link #start()} is called, no data will be persisted but entities can 
     * rebind.
     */
    public void start();

    public void stop();

    /** @deprecated since 0.7.0; use {@link #waitForPendingComplete(Duration)} */
    @VisibleForTesting
    @Deprecated
    public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException;
    @VisibleForTesting
    public void waitForPendingComplete(Duration duration) throws InterruptedException, TimeoutException;
}
