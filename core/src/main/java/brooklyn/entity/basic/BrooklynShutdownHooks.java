package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.javalang.Threads;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

public class BrooklynShutdownHooks {

    private static final Logger log = LoggerFactory.getLogger(BrooklynShutdownHooks.class);

    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.TWO_MINUTES;
    
    private static final AtomicBoolean isShutdownHookRegistered = new AtomicBoolean();
    private static final List<Entity> entitiesToStopOnShutdown = Lists.newArrayList();
    private static final List<ManagementContextInternal> managementContextsToTerminateOnShutdown = Lists.newArrayList();

    private static final Object mutex = new Object();
    
    /**
     * Max time to wait for shutdown to complete, when stopping the entities from {@link #invokeStopOnShutdown(Entity)}.
     * Default is two minutes - deliberately long because stopping cloud VMs can often take a minute.
     */
    private static volatile Duration shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
    
    public static void setShutdownTimeout(Duration val) {
        shutdownTimeout = val;
    }
    
    public static void invokeStopOnShutdown(Entity entity) {
        if (!(entity instanceof Startable)) {
            log.warn("Not adding entity {} for stop-on-shutdown as not an instance of {}", entity, Startable.class.getSimpleName());
            return;
        }
        synchronized (mutex) {
            entitiesToStopOnShutdown.add(entity);
        }
        addShutdownHookIfNotAlready();
    }
    
    public static void invokeTerminateOnShutdown(ManagementContext managementContext) {
        synchronized (mutex) {
            managementContextsToTerminateOnShutdown.add((ManagementContextInternal) managementContext);
        }
        addShutdownHookIfNotAlready();
    }

    private static void addShutdownHookIfNotAlready() {
        if (isShutdownHookRegistered.compareAndSet(false, true)) {
            Threads.addShutdownHook(new BrooklynShutdownHookJob());
        }
    }
    
    @VisibleForTesting
    public static class BrooklynShutdownHookJob implements Runnable {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public void run() {
            // First stop entities; on interrupt, abort waiting for tasks - but let shutdown hook continue
            synchronized (mutex) {
                log.info("Brooklyn stopOnShutdown shutdown-hook invoked: stopping entities: "+entitiesToStopOnShutdown);
                List<Task> stops = new ArrayList<Task>();
                for (Entity entity: entitiesToStopOnShutdown) {
                    try {
                        stops.add(entity.invoke(Startable.STOP, new MutableMap()));
                    } catch (RuntimeException exc) {
                        if (log.isDebugEnabled()) log.debug("stopOnShutdown of "+entity+" returned error (continuing): "+exc, exc);
                    }
                }
                long endTime = System.currentTimeMillis() + shutdownTimeout.toMilliseconds();
                for (Task t: stops) {
                    Duration remainingTime = Duration.max(Duration.untilUtc(endTime), Duration.ONE_MILLISECOND);
                    try {
                        Object result = t.getUnchecked(remainingTime);
                        if (log.isDebugEnabled()) log.debug("stopOnShutdown of {} completed: {}", t, result);
                    } catch (RuntimeInterruptedException e) {
                        Thread.currentThread().interrupt();
                        endTime = System.currentTimeMillis();
                        if (log.isDebugEnabled()) log.debug("stopOnShutdown of "+t+" interrupted; (continuing with immediate timeout): "+e);
                    } catch (RuntimeException e) {
                        if (log.isDebugEnabled()) log.debug("stopOnShutdown of "+t+" returned error (continuing): "+e, e);
                        Exceptions.propagateIfFatal(e);
                    }
                }
            
                // Then terminate management contexts
                log.info("Brooklyn terminateOnShutdown shutdown-hook invoked: terminating management contexts: "+managementContextsToTerminateOnShutdown);
                for (ManagementContextInternal managementContext: managementContextsToTerminateOnShutdown) {
                    try {
                        managementContext.terminate();
                    } catch (RuntimeException e) {
                        log.info("terminateOnShutdown of "+managementContext+" returned error (continuing): "+e, e);
                    }
                }
            }
        }
    }
}
