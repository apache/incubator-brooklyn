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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

public class BrooklynShutdownHooks {

    private static final Logger log = LoggerFactory.getLogger(BrooklynShutdownHooks.class);

    private static final AtomicBoolean isShutdownHookRegistered = new AtomicBoolean();
    private static final List<Entity> entitiesToStopOnShutdown = Lists.newArrayList();
    private static final List<ManagementContextInternal> managementContextsToTermianteOnShutdown = Lists.newArrayList();

    public static void invokeStopOnShutdown(Entity entity) {
        synchronized (entitiesToStopOnShutdown) {
            entitiesToStopOnShutdown.add(entity);
        }
        addShutdownHookIfNotAlready();
    }
    
    public static void invokeTerminateOnShutdown(ManagementContext managementContext) {
        synchronized (entitiesToStopOnShutdown) {
            managementContextsToTermianteOnShutdown.add((ManagementContextInternal) managementContext);
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
            synchronized (entitiesToStopOnShutdown) {
                log.info("Brooklyn stopOnShutdown shutdown-hook invoked: stopping entities: "+entitiesToStopOnShutdown);
                List<Task> stops = new ArrayList<Task>();
                for (Entity entity: entitiesToStopOnShutdown) {
                    try {
                        stops.add(entity.invoke(Startable.STOP, new MutableMap()));
                    } catch (RuntimeException exc) {
                        log.debug("stopOnShutdown of "+entity+" returned error (continuing): "+exc, exc);
                    }
                }
                try {
                    for (Task t: stops) {
                        try {
                            log.debug("stopOnShutdown of {} completed: {}", t, t.get());
                        } catch (Exception e) {
                            log.debug("stopOnShutdown of "+t+" returned error (continuing): "+e, e);
                            Exceptions.propagateIfFatal(e);
                        }
                    }
                } catch (RuntimeInterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.debug("stopOnShutdown interrupted while waiting for entity-stop tasks; continuing: "+e);
                }
            }
            
            // Then terminate management contexts
            synchronized (managementContextsToTermianteOnShutdown) {
                log.info("Brooklyn terminateOnShutdown shutdown-hook invoked: terminating management contexts: "+managementContextsToTermianteOnShutdown);
                for (ManagementContextInternal managementContext: managementContextsToTermianteOnShutdown) {
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
