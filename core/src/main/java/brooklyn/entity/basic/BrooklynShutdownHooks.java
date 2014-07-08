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
package brooklyn.entity.basic;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.javalang.Threads;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

public class BrooklynShutdownHooks {

    private static final Logger log = LoggerFactory.getLogger(BrooklynShutdownHooks.class);

    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.TWO_MINUTES;
    
    private static final AtomicBoolean isShutdownHookRegistered = new AtomicBoolean();
    private static final List<Entity> entitiesToStopOnShutdown = Lists.newArrayList();
    private static final List<ManagementContext> managementContextsToStopAppsOnShutdown = Lists.newArrayList();
    private static final List<ManagementContext> managementContextsToTerminateOnShutdown = Lists.newArrayList();
    private static final AtomicBoolean isShutDown = new AtomicBoolean(false);

//    private static final Object mutex = new Object();
    private static final Semaphore semaphore = new Semaphore(1);
    
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
        try {
            semaphore.acquire();
            if (isShutDown.get()) {
                semaphore.release();
                try {
                    log.warn("Call to invokeStopOnShutdown for "+entity+" while system already shutting down; invoking stop now and throwing exception");
                    Entities.destroy(entity);
                    throw new IllegalStateException("Call to invokeStopOnShutdown for "+entity+" while system already shutting down");
                } catch (Exception e) {
                    throw new IllegalStateException("Call to invokeStopOnShutdown for "+entity+" while system already shutting down, had error: "+e, e);
                }
            }
            
            try {
                // TODO should be a weak reference in case it is destroyed before shutdown
                // (only applied to certain entities started via launcher so not a big leak)
                entitiesToStopOnShutdown.add(entity);
            } finally {
                semaphore.release();
            }
            addShutdownHookIfNotAlready();
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public static void invokeStopAppsOnShutdown(ManagementContext managementContext) {
        try {
            semaphore.acquire();
            if (isShutDown.get()) {
                semaphore.release();
                try {
                    log.warn("Call to invokeStopAppsOnShutdown for "+managementContext+" while system already shutting down; invoking stop now and throwing exception");
                    destroyAndWait(managementContext.getApplications(), shutdownTimeout);
                    
                    throw new IllegalStateException("Call to invokeStopAppsOnShutdown for "+managementContext+" while system already shutting down");
                } catch (Exception e) {
                    throw new IllegalStateException("Call to invokeStopAppsOnShutdown for "+managementContext+" while system already shutting down, had error: "+e, e);
                }
            }
            
            // TODO weak reference, as per above
            managementContextsToStopAppsOnShutdown.add(managementContext);
            semaphore.release();
            addShutdownHookIfNotAlready();
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public static void invokeTerminateOnShutdown(ManagementContext managementContext) {
        try {
            semaphore.acquire();
            if (isShutDown.get()) {
                semaphore.release();
                try {
                    log.warn("Call to invokeStopOnShutdown for "+managementContext+" while system already shutting down; invoking stop now and throwing exception");
                    ((ManagementContextInternal)managementContext).terminate();
                    throw new IllegalStateException("Call to invokeTerminateOnShutdown for "+managementContext+" while system already shutting down");
                } catch (Exception e) {
                    throw new IllegalStateException("Call to invokeTerminateOnShutdown for "+managementContext+" while system already shutting down, had error: "+e, e);
                }
            }
            
            // TODO weak reference, as per above
            managementContextsToTerminateOnShutdown.add(managementContext);
            semaphore.release();
            addShutdownHookIfNotAlready();
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    private static void addShutdownHookIfNotAlready() {
        if (isShutdownHookRegistered.compareAndSet(false, true)) {
            Threads.addShutdownHook(BrooklynShutdownHookJob.newInstanceForReal());
        }
    }
    
    @VisibleForTesting
    public static class BrooklynShutdownHookJob implements Runnable {
        
        final boolean setStaticShutDownFlag;
        
        private BrooklynShutdownHookJob(boolean setStaticShutDownFlag) {
            this.setStaticShutDownFlag = setStaticShutDownFlag;
        }
        
        public static BrooklynShutdownHookJob newInstanceForReal() {
            return new BrooklynShutdownHookJob(true);
        }
        
        /** testing instance does not actually set the `isShutDown` bit */
        public static BrooklynShutdownHookJob newInstanceForTesting() {
            return new BrooklynShutdownHookJob(false);
        }
        
        @Override
        public void run() {
            // First stop entities; on interrupt, abort waiting for tasks - but let shutdown hook continue
            Set<Entity> entitiesToStop = MutableSet.of();
            try {
                semaphore.acquire();
                if (setStaticShutDownFlag) 
                    isShutDown.set(true);
                semaphore.release();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
            entitiesToStop.addAll(entitiesToStopOnShutdown);
            for (ManagementContext mgmt: managementContextsToStopAppsOnShutdown) {
                entitiesToStop.addAll(mgmt.getApplications());
            }
            
            if (entitiesToStop.isEmpty()) {
                log.debug("Brooklyn shutdown: no entities to stop");
            } else {
                log.info("Brooklyn shutdown: stopping entities "+entitiesToStop);
                destroyAndWait(entitiesToStop, shutdownTimeout);
            }

            // Then terminate management contexts
            log.debug("Brooklyn terminateOnShutdown shutdown-hook invoked: terminating management contexts: "+managementContextsToTerminateOnShutdown);
            for (ManagementContext managementContext: managementContextsToTerminateOnShutdown) {
                try {
                    if (!managementContext.isRunning())
                        continue;
                    ((ManagementContextInternal)managementContext).terminate();
                } catch (RuntimeException e) {
                    log.info("terminateOnShutdown of "+managementContext+" returned error (continuing): "+e, e);
                }
            }
        }
    }
    
    protected static void destroyAndWait(Iterable<? extends Entity> entitiesToStop, Duration timeout) {
        MutableList<Task<?>> stops = MutableList.of();
        for (Entity entityToStop: entitiesToStop) {
            final Entity entity = entityToStop;
            if (!Entities.isManaged(entity)) continue;
            Task<Object> t = Tasks.builder().dynamic(false).name("destroying "+entity).body(new Runnable() {
                @Override public void run() { Entities.destroy(entity); }
            }).build();
            stops.add( ((EntityInternal)entity).getExecutionContext().submit(t) );
        }
        CountdownTimer timer = CountdownTimer.newInstanceStarted(timeout);
        for (Task<?> t: stops) {
            try {
                Duration durationRemaining = timer.getDurationRemaining();
                Object result = t.getUnchecked(durationRemaining.isPositive() ? durationRemaining : Duration.ONE_MILLISECOND);
                if (log.isDebugEnabled()) log.debug("stopOnShutdown of {} completed: {}", t, result);
            } catch (RuntimeInterruptedException e) {
                Thread.currentThread().interrupt();
                if (log.isDebugEnabled()) log.debug("stopOnShutdown of "+t+" interrupted: "+e);
                break;
            } catch (RuntimeException e) {
                Exceptions.propagateIfFatal(e);
                log.warn("Shutdown hook "+t+" returned error (continuing): "+e);
                if (log.isDebugEnabled()) log.debug("stopOnShutdown of "+t+" returned error (continuing to stop others): "+e, e);
            }
        }
    }

}
