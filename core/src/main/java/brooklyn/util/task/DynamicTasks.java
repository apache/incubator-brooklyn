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
package brooklyn.util.task;

import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.management.ExecutionContext;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.management.TaskQueueingContext;
import brooklyn.management.TaskWrapper;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

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
        public void andWaitForSuccess() {
            task.getUnchecked();
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
            Preconditions.checkState(!Tasks.isQueuedOrSubmitted(task), "Task to queue must not yet be submitted: %s", task);
            TaskQueueingContext adder = getTaskQueuingContext();
            if (adder==null) 
                throw new IllegalStateException("Task "+task+" cannot be queued here; no queueing context available");
            adder.queue(task.asTask());
            return task;
        } catch (Throwable e) {
            log.warn("Error queueing "+task+" (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
    }

    /** @see #queue(brooklyn.management.TaskAdaptable)  */
    public static void queue(TaskAdaptable<?> task1, TaskAdaptable<?> task2, TaskAdaptable<?> ...tasks) {
        queue(task1);
        queue(task2);
        for (TaskAdaptable<?> task: tasks) queue(task);
    }

    /** @see #queue(brooklyn.management.TaskAdaptable)  */
    public static <T extends TaskAdaptable<?>> T queue(TaskFactory<T> taskFactory) {
        return queue(taskFactory.newTask());
    }

    /** @see #queue(brooklyn.management.TaskAdaptable)  */
    public static void queue(TaskFactory<?> task1, TaskFactory<?> task2, TaskFactory<?> ...tasks) {
        queue(task1.newTask());
        queue(task2.newTask());
        for (TaskFactory<?> task: tasks) queue(task.newTask());
    }

    /** @see #queue(brooklyn.management.TaskAdaptable)  */
    public static <T> Task<T> queue(String name, Callable<T> job) {
        return DynamicTasks.queue(Tasks.<T>builder().name(name).body(job).build());
    }

    /** @see #queue(brooklyn.management.TaskAdaptable)  */
    public static <T> Task<T> queue(String name, Runnable job) {
        return DynamicTasks.queue(Tasks.<T>builder().name(name).body(job).build());
    }

    public static <T extends TaskAdaptable<?>> T queueIfNeeded(T task) {
        if (!Tasks.isQueuedOrSubmitted(task))
            queue(task);
        return task;
    }
    
    /** submits the given task if needed, and gets the result (unchecked) 
     * only permitted in a queueing context (ie a DST main job) */
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
    
    /** Calls {@link TaskQueueingContext#drain(Duration, boolean)} on the current task context */
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
    
}
