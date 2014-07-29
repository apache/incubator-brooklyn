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

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
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
    
    void initResult(ListenableFuture<T> result);

    /** if the job is queued for submission (e.g. by another task) it can indicate that fact (and time) here;
     * note tasks can (and often are) submitted without any queueing, in which case this value may be -1 */
    long getQueuedTimeUtc();
    
    Future<T> getResult();
    
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
}
