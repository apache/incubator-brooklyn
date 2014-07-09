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
package brooklyn.event.feed;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.DynamicSequentialTask;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;


/** 
 * For executing periodic polls.
 * Jobs are added to the schedule, and then the poller is started.
 * The jobs will then be executed periodically, and the handler called for the result/failure.
 * 
 * Assumes the schedule+start will be done single threaded, and that stop will not be done concurrently.
 */
public class Poller<V> {
    public static final Logger log = LoggerFactory.getLogger(Poller.class);

    private final EntityLocal entity;
    private final boolean onlyIfServiceUp;
    private final Set<Callable<?>> oneOffJobs = new LinkedHashSet<Callable<?>>();
    private final Set<PollJob<V>> pollJobs = new LinkedHashSet<PollJob<V>>();
    private final Set<Task<?>> oneOffTasks = new LinkedHashSet<Task<?>>();
    private final Set<ScheduledTask> tasks = new LinkedHashSet<ScheduledTask>();
    private volatile boolean running = false;
    
    private static class PollJob<V> {
        final PollHandler<? super V> handler;
        final Duration pollPeriod;
        final Runnable wrappedJob;
        private boolean loggedPreviousException = false;
        
        PollJob(final Callable<V> job, final PollHandler<? super V> handler, Duration period) {
            this.handler = handler;
            this.pollPeriod = period;
            
            wrappedJob = new Runnable() {
                public void run() {
                    try {
                        V val = job.call();
                        loggedPreviousException = false;
                        if (handler.checkSuccess(val)) {
                            handler.onSuccess(val);
                        } else {
                            handler.onFailure(val);
                        }
                    } catch (Exception e) {
                        // 2013-12-21 AH adding add'l logging because seeing strange scheduled task abortion from here
                        // even though all paths should be catching it
                        if (loggedPreviousException) {
                            if (log.isTraceEnabled()) log.trace("PollJob for {}, repeated consecutive failures, handling {} using {}", new Object[] {job, e, handler});
                        } else {
                            if (log.isDebugEnabled()) log.debug("PollJob for {} handling {} using {}", new Object[] {job, e, handler});
                            loggedPreviousException = true;
                        }
                        handler.onException(e);
                    }
                }
            };
        }
    }
    
    /** @deprecated since 0.7.0, pass in whether should run onlyIfServiceUp */
    @Deprecated
    public Poller(EntityLocal entity) {
        this(entity, false);
    }
    public Poller(EntityLocal entity, boolean onlyIfServiceUp) {
        this.entity = entity;
        this.onlyIfServiceUp = onlyIfServiceUp;
    }
    
    /** Submits a one-off poll job; recommended that callers supply to-String so that task has a decent description */
    public void submit(Callable<?> job) {
        if (running) {
            throw new IllegalStateException("Cannot submit additional tasks after poller has started");
        }
        oneOffJobs.add(job);
    }

    public void scheduleAtFixedRate(Callable<V> job, PollHandler<? super V> handler, long period) {
        scheduleAtFixedRate(job, handler, Duration.millis(period));
    }
    public void scheduleAtFixedRate(Callable<V> job, PollHandler<? super V> handler, Duration period) {
        if (running) {
            throw new IllegalStateException("Cannot schedule additional tasks after poller has started");
        }
        PollJob<V> foo = new PollJob<V>(job, handler, period);
        pollJobs.add(foo);
    }

    @SuppressWarnings({ "unchecked" })
    public void start() {
        // TODO Previous incarnation of this logged this logged polledSensors.keySet(), but we don't know that anymore
        // Is that ok, are can we do better?
        
        if (log.isDebugEnabled()) log.debug("Starting poll for {} (using {})", new Object[] {entity, this});
        if (running) { 
            throw new IllegalStateException(String.format("Attempt to start poller %s of entity %s when already running", 
                    this, entity));
        }
        
        running = true;
        
        for (final Callable<?> oneOffJob : oneOffJobs) {
            Task<?> task = Tasks.builder().dynamic(false).body((Callable<Object>) oneOffJob).name("Poll").description("One-time poll job "+oneOffJob).build();
            oneOffTasks.add(((EntityInternal)entity).getExecutionContext().submit(task));
        }
        
        for (final PollJob<V> pollJob : pollJobs) {
            final String scheduleName = pollJob.handler.getDescription();
            if (pollJob.pollPeriod.compareTo(Duration.ZERO) > 0) {
                Callable<Task<?>> pollingTaskFactory = new Callable<Task<?>>() {
                    public Task<?> call() {
                        DynamicSequentialTask<Void> task = new DynamicSequentialTask<Void>(MutableMap.of("displayName", scheduleName, "entity", entity), 
                            new Callable<Void>() { public Void call() {
                                if (onlyIfServiceUp && !Boolean.TRUE.equals(entity.getAttribute(Attributes.SERVICE_UP))) {
                                        return null;
                                }
                                pollJob.wrappedJob.run();
                                return null; 
                            } } );
                        BrooklynTaskTags.setTransient(task);
                        return task;
                    }
                };
                ScheduledTask task = new ScheduledTask(MutableMap.of("period", pollJob.pollPeriod), pollingTaskFactory);
                tasks.add((ScheduledTask)Entities.submit(entity, task));
            } else {
                if (log.isDebugEnabled()) log.debug("Activating poll (but leaving off, as period {}) for {} (using {})", new Object[] {pollJob.pollPeriod, entity, this});
            }
        }
    }
    
    public void stop() {
        if (log.isDebugEnabled()) log.debug("Stopping poll for {} (using {})", new Object[] {entity, this});
        if (!running) { 
            throw new IllegalStateException(String.format("Attempt to stop poller %s of entity %s when not running", 
                    this, entity));
        }
        
        running = false;
        for (Task<?> task : oneOffTasks) {
            task.cancel(true);
        }
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        oneOffTasks.clear();
        tasks.clear();
    }

    public boolean isRunning() {
        return running;
    }
    
    protected boolean isEmpty() {
        return pollJobs.isEmpty();
    }
    
    public String toString() {
        return Objects.toStringHelper(this).add("entity", entity).toString();
    }
}
