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

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.brooklyn.api.mgmt.ExecutionManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * All tasks being passed to the {@link ExecutionManager} should implement this.
 * Users are strongly encouraged to use (or extend) {@link BasicTask}, rather than
 * implementing a task from scratch.
 * 
 * The methods on this interface will change in subsequent releases. Because this is
 * marked as beta, the normal deprecation policy for these methods does not apply.
 * 
 * @author aled
 */
@Beta
public interface TaskInternal<T> extends Task<T> {
    
    /** sets the internal future object used to record the association to a job submitted to an {@link ExecutorService} */
    void initInternalFuture(ListenableFuture<T> result);

    /** returns the underlying future where this task's results will come in; see {@link #initInternalFuture(ListenableFuture)} */
    Future<T> getInternalFuture();
    
    /** if the job is queued for submission (e.g. by another task) it can indicate that fact (and time) here;
     * note tasks can (and often are) submitted without any queueing, in which case this value may be -1 */
    long getQueuedTimeUtc();
    
    boolean isQueuedOrSubmitted();
    boolean isQueuedAndNotSubmitted();
    boolean isQueued();

    /** marks the task as queued for execution */
    void markQueued();

    boolean cancel();
    
    boolean blockUntilStarted(Duration timeout);

    /** allows a task user to specify why a task is blocked; for use immediately before a blocking/wait,
     * and typically cleared immediately afterwards; referenced by management api to inspect a task
     * which is blocking
     * <p>
     * returns previous details, in case caller wishes to recall and restore it (e.g. if it is doing a sub-blocking)
     */
    String setBlockingDetails(String blockingDetails);

    /** as {@link #setBlockingDetails(String)} but records a task which is blocking,
     * for use e.g. in a gui to navigate to the current active subtask
     * <p>
     * returns previous blocking task, in case caller wishes to recall and restore it
     */
    Task<?> setBlockingTask(Task<?> blockingTask);
    
    void resetBlockingDetails();
    
    void resetBlockingTask();

    /** returns a textual message giving details while the task is blocked */
    String getBlockingDetails();
    
    /** returns a task that this task is blocked on */
    Task<?> getBlockingTask();
    
    void setExtraStatusText(Object extraStatus);
    
    Object getExtraStatusText();

    /** On task completion (or cancellation) runs the listeners which have been registered using 
     * {@link #addListener(Runnable, java.util.concurrent.Executor)}. */
    void runListeners();

    void setEndTimeUtc(long val);

    void setThread(Thread thread);

    Callable<T> getJob();
    
    void setJob(Callable<T> job);

    ExecutionList getListeners();

    void setSubmitTimeUtc(long currentTimeMillis);

    void setSubmittedByTask(Task<?> task);
    
    Set<Object> getMutableTags();

    void setStartTimeUtc(long currentTimeMillis);

    void applyTagModifier(Function<Set<Object>,Void> modifier);
    
    /** if a task is a proxy for another one (used mainly for internal tasks),
     * this returns the "real" task represented by this one */
    Task<?> getProxyTarget();

    /** clearer semantics around cancellation; may be promoted to {@link Task} if we  */
    @Beta
    public boolean cancel(TaskCancellationMode mode);
    
    @Beta
    public static class TaskCancellationMode {
        public static final TaskCancellationMode DO_NOT_INTERRUPT = new TaskCancellationMode(false, false, false);
        public static final TaskCancellationMode INTERRUPT_TASK_BUT_NOT_SUBMITTED_TASKS = new TaskCancellationMode(true, false, false);
        public static final TaskCancellationMode INTERRUPT_TASK_AND_DEPENDENT_SUBMITTED_TASKS = new TaskCancellationMode(true, true, false);
        public static final TaskCancellationMode INTERRUPT_TASK_AND_ALL_SUBMITTED_TASKS = new TaskCancellationMode(true, true, true);
        
        private final boolean allowedToInterruptTask, 
            allowedToInterruptDependentSubmittedTasks, 
            allowedToInterruptAllSubmittedTasks;
        
        private TaskCancellationMode(boolean mayInterruptIfRunning, boolean interruptSubmittedTransients, boolean interruptAllSubmitted) {
            this.allowedToInterruptTask = mayInterruptIfRunning;
            this.allowedToInterruptDependentSubmittedTasks = interruptSubmittedTransients;
            this.allowedToInterruptAllSubmittedTasks = interruptAllSubmitted;
        }
        
        public boolean isAllowedToInterruptTask() { return allowedToInterruptTask; }
        /** Implementation-dependent what "dependent" means in this context, 
         * e.g. may be linked to a "transient" tag (that's what Brooklyn does) */ 
        public boolean isAllowedToInterruptDependentSubmittedTasks() { return allowedToInterruptDependentSubmittedTasks; }
        public boolean isAllowedToInterruptAllSubmittedTasks() { return allowedToInterruptAllSubmittedTasks; }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("interruptTask", allowedToInterruptTask)
                .add("interruptDependentSubmitted", allowedToInterruptDependentSubmittedTasks)
                .add("interruptAllSubmitted", allowedToInterruptAllSubmittedTasks)
                .toString();
        }
    }
    
}
