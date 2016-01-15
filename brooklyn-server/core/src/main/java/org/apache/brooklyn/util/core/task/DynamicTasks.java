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
package org.apache.brooklyn.util.core.task;

import java.util.List;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.api.mgmt.TaskQueueingContext;
import org.apache.brooklyn.api.mgmt.TaskWrapper;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

/** 
 * Contains static methods which detect and use the current {@link TaskQueueingContext} to execute tasks.
 * 
 * @since 0.6.0
 */
@Beta
public class DynamicTasks {

    private static final Logger log = LoggerFactory.getLogger(DynamicTasks.class);
    private static final ThreadLocal<TaskQueueingContext> taskQueueingContext = new ThreadLocal<TaskQueueingContext>();
    
    public static void setTaskQueueingContext(TaskQueueingContext newTaskQC) {
        taskQueueingContext.set(newTaskQC);
    }
    
    public static TaskQueueingContext getThreadTaskQueuingContext() {
        return taskQueueingContext.get();
    }
    
    public static TaskQueueingContext getTaskQueuingContext() {
        TaskQueueingContext adder = getThreadTaskQueuingContext();
        if (adder!=null) return adder;
        Task<?> t = Tasks.current();
        if (t instanceof TaskQueueingContext) return (TaskQueueingContext) t;
        return null;
    }

    
    public static void removeTaskQueueingContext() {
        taskQueueingContext.remove();
    }

    public static class TaskQueueingResult<T> implements TaskWrapper<T> {
        private final Task<T> task;
        private final boolean wasQueued;
        private ExecutionContext execContext = null;
        
        private TaskQueueingResult(TaskAdaptable<T> task, boolean wasQueued) {
            this.task = task.asTask();
            this.wasQueued = wasQueued;
        }
        @Override
        public Task<T> asTask() {
            return task;
        }
        @Override
        public Task<T> getTask() {
            return task;
        }
        /** returns true if the task was queued */
        public boolean wasQueued() {
            return wasQueued;
        }
        /** returns true if the task either is currently queued or has been submitted */
        public boolean isQueuedOrSubmitted() {
            return wasQueued || Tasks.isQueuedOrSubmitted(task);
        }
        /** specifies an execContext to use if the task has to be explicitly submitted;
         * if omitted it will attempt to find one based on the current thread's context */
        public TaskQueueingResult<T> executionContext(ExecutionContext execContext) {
            this.execContext = execContext;
            return this;
        }
        /** as {@link #executionContext(ExecutionContext)} but inferring from the entity */
        public TaskQueueingResult<T> executionContext(Entity entity) {
            this.execContext = ((EntityInternal)entity).getManagementSupport().getExecutionContext();
            return this;
        }
        private boolean orSubmitInternal() {
            if (!wasQueued()) {
                if (isQueuedOrSubmitted()) {
                    log.warn("Redundant call to execute "+getTask()+"; skipping");
                    return false;
                } else {
                    ExecutionContext ec = execContext;
                    if (ec==null)
                        ec = BasicExecutionContext.getCurrentExecutionContext();
                    if (ec==null)
                        throw new IllegalStateException("Cannot execute "+getTask()+" without an execution context; ensure caller is in an ExecutionContext");
                    ec.submit(getTask());
                    return true;
                }
            } else {
                return false;
            }
        }
        /** causes the task to be submitted (asynchronously) if it hasn't already been,
         * requiring an entity execution context (will try to find a default if not set) */
        public TaskQueueingResult<T> orSubmitAsync() {
            orSubmitInternal();
            return this;
        }
        /** convenience for setting {@link #executionContext(ExecutionContext)} then submitting async */
        public TaskQueueingResult<T> orSubmitAsync(Entity entity) {
            executionContext(entity);
            return orSubmitAsync();
        }
        /** causes the task to be submitted *synchronously* if it hasn't already been submitted;
         * useful in contexts such as libraries where callers may be either on a legacy call path 
         * (which assumes all commands complete immediately);
         * requiring an entity execution context (will try to find a default if not set) */
        public TaskQueueingResult<T> orSubmitAndBlock() {
            if (orSubmitInternal()) task.getUnchecked();
            return this;
        }
        /** convenience for setting {@link #executionContext(ExecutionContext)} then submitting blocking */
        public TaskQueueingResult<T> orSubmitAndBlock(Entity entity) {
            executionContext(entity);
            return orSubmitAndBlock();
        }
        /** blocks for the task to be completed
         * <p>
         * needed in any context where subsequent commands assume the task has completed.
         * not needed in a context where the task is simply being built up and queued.
         * <p>
         * throws if there are any errors
         */
        public T andWaitForSuccess() {
            return task.getUnchecked();
        }
        public void orCancel() {
            if (!wasQueued()) {
                task.cancel(false);
            }
        }
    }
    
    /**
     * Tries to add the task to the current addition context if there is one, otherwise does nothing.
     * <p/>
     * Call {@link TaskQueueingResult#orSubmitAsync() orSubmitAsync()} on the returned
     * {@link TaskQueueingResult TaskQueueingResult} to handle execution of tasks in a
     * {@link BasicExecutionContext}.
     */
    public static <T> TaskQueueingResult<T> queueIfPossible(TaskAdaptable<T> task) {
        TaskQueueingContext adder = getTaskQueuingContext();
        boolean result = false;
        if (adder!=null)
            result = Tasks.tryQueueing(adder, task);
        return new TaskQueueingResult<T>(task, result);
    }

    /** @see #queueIfPossible(TaskAdaptable) */
    public static <T> TaskQueueingResult<T> queueIfPossible(TaskFactory<? extends TaskAdaptable<T>> task) {
        return queueIfPossible(task.newTask());
    }

    /** adds the given task to the nearest task addition context,
     * either set as a thread-local, or in the current task, or the submitter of the task, etc
     * <p>
     * throws if it cannot add */
    public static <T> Task<T> queueInTaskHierarchy(Task<T> task) {
        Preconditions.checkNotNull(task, "Task to queue cannot be null");
        Preconditions.checkState(!Tasks.isQueuedOrSubmitted(task), "Task to queue must not yet be submitted: {}", task);
        
        TaskQueueingContext adder = getTaskQueuingContext();
        if (adder!=null) { 
            if (Tasks.tryQueueing(adder, task)) {
                log.debug("Queued task {} at context {} (no hierarchy)", task, adder);
                return task;
            }
        }
        
        Task<?> t = Tasks.current();
        Preconditions.checkState(t!=null || adder!=null, "No task addition context available for queueing task "+task);
        
        while (t!=null) {
            if (t instanceof TaskQueueingContext) {
                if (Tasks.tryQueueing((TaskQueueingContext)t, task)) {
                    log.debug("Queued task {} at hierarchical context {}", task, t);
                    return task;
                }
            }
            t = t.getSubmittedByTask();
        }
        
        throw new IllegalStateException("No task addition context available in current task hierarchy for adding task "+task);
    }

    /**
     * Queues the given task.
     * <p/>
     * This method is only valid within a dynamic task. Use {@link #queueIfPossible(TaskAdaptable)}
     * and {@link TaskQueueingResult#orSubmitAsync()} if the calling context is a basic task.
     *
     * @param task The task to queue
     * @throws IllegalStateException if no task queueing context is available
     * @return The queued task
     */
    public static <V extends TaskAdaptable<?>> V queue(V task) {
        try {
            Preconditions.checkNotNull(task, "Task to queue cannot be null");
            Preconditions.checkState(!Tasks.isQueued(task), "Task to queue must not yet be queued: %s", task);
            TaskQueueingContext adder = getTaskQueuingContext();
            if (adder==null) {
                throw new IllegalStateException("Task "+task+" cannot be queued here; no queueing context available");
            }
            adder.queue(task.asTask());
            return task;
        } catch (Throwable e) {
            log.warn("Error queueing "+task+" (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
    }

    /** @see #queue(org.apache.brooklyn.api.mgmt.TaskAdaptable)  */
    public static void queue(TaskAdaptable<?> task1, TaskAdaptable<?> task2, TaskAdaptable<?> ...tasks) {
        queue(task1);
        queue(task2);
        for (TaskAdaptable<?> task: tasks) queue(task);
    }

    /** @see #queue(org.apache.brooklyn.api.mgmt.TaskAdaptable)  */
    public static <T extends TaskAdaptable<?>> T queue(TaskFactory<T> taskFactory) {
        return queue(taskFactory.newTask());
    }

    /** @see #queue(org.apache.brooklyn.api.mgmt.TaskAdaptable)  */
    public static void queue(TaskFactory<?> task1, TaskFactory<?> task2, TaskFactory<?> ...tasks) {
        queue(task1.newTask());
        queue(task2.newTask());
        for (TaskFactory<?> task: tasks) queue(task.newTask());
    }

    /** @see #queue(org.apache.brooklyn.api.mgmt.TaskAdaptable)  */
    public static <T> Task<T> queue(String name, Callable<T> job) {
        return DynamicTasks.queue(Tasks.<T>builder().displayName(name).body(job).build());
    }

    /** @see #queue(org.apache.brooklyn.api.mgmt.TaskAdaptable)  */
    public static <T> Task<T> queue(String name, Runnable job) {
        return DynamicTasks.queue(Tasks.<T>builder().displayName(name).body(job).build());
    }

    /** queues the task if needed, i.e. if it is not yet submitted (so it will run), 
     * or if it is submitted but not queued and we are in a queueing context (so it is available for informational purposes) */
    public static <T extends TaskAdaptable<?>> T queueIfNeeded(T task) {
        if (!Tasks.isQueued(task)) {
            if (Tasks.isSubmitted(task) && getTaskQueuingContext()==null) {
                // already submitted and not in a queueing context, don't try to queue
            } else {
                // needs submitting, put it in the queue
                // (will throw an error if we are not a queueing context)
                queue(task);
            }
        }
        return task;
    }
    
    /** submits/queues the given task if needed, and gets the result (unchecked) 
     * only permitted in a queueing context (ie a DST main job) if the task is not yet submitted */
    // things get really confusing if you try to queueInTaskHierarchy -- easy to cause deadlocks!
    public static <T> T get(TaskAdaptable<T> t) {
        return queueIfNeeded(t).asTask().getUnchecked();
    }

    /** As {@link #drain(Duration, boolean)} waiting forever and throwing the first error 
     * (excluding errors in inessential tasks),
     * then returning the last task in the queue (which is guaranteed to have finished without error,
     * if this method returns without throwing) */
    public static Task<?> waitForLast() {
        drain(null, true);
        // this call to last is safe, as the above guarantees everything will have run
        // (on errors the above will throw so we won't come here)
        List<Task<?>> q = DynamicTasks.getTaskQueuingContext().getQueue();
        return q.isEmpty() ? null : Iterables.getLast(q);
    }
    
    /** Calls {@link TaskQueueingContext#drain(Duration, boolean, boolean)} on the current task context */
    public static TaskQueueingContext drain(Duration optionalTimeout, boolean throwFirstError) {
        TaskQueueingContext qc = DynamicTasks.getTaskQueuingContext();
        Preconditions.checkNotNull(qc, "Cannot drain when there is no queueing context");
        qc.drain(optionalTimeout, false, throwFirstError);
        return qc;
    }

    /** as {@link Tasks#swallowChildrenFailures()} but requiring a {@link TaskQueueingContext}. */
    @Beta
    public static void swallowChildrenFailures() {
        Preconditions.checkNotNull(DynamicTasks.getTaskQueuingContext(), "Task queueing context required here");
        Tasks.swallowChildrenFailures();
    }

    /** same as {@link Tasks#markInessential()}
     * (but included here for convenience as it is often used in conjunction with {@link DynamicTasks}) */
    public static void markInessential() {
        Tasks.markInessential();
    }

    /** queues the task if possible, otherwise submits it asynchronously; returns the task for callers to 
     * {@link Task#getUnchecked()} or {@link Task#blockUntilEnded()} */
    public static <T> Task<T> submit(TaskAdaptable<T> task, Entity entity) {
        return queueIfPossible(task).orSubmitAsync(entity).asTask();
    }

    /** Breaks the parent-child relation between Tasks.current() and the task passed,
     *  making the new task a top-level one at the target entity.
     *  To make it visible in the UI, also tag the task with:
     *    .tag(BrooklynTaskTags.tagForContextEntity(entity))
     *    .tag(BrooklynTaskTags.NON_TRANSIENT_TASK_TAG)
     */
    public static <T> Task<T> submitTopLevelTask(TaskAdaptable<T> task, Entity entity) {
        Task<?> currentTask = BasicExecutionManager.getPerThreadCurrentTask().get();
        BasicExecutionManager.getPerThreadCurrentTask().set(null);
        try {
            return Entities.submit(entity, task).asTask();
        } finally {
            BasicExecutionManager.getPerThreadCurrentTask().set(currentTask);
        }
    }

}
