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
package brooklyn.management;

import java.util.List;

import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;

/**
 * Marks a place where tasks can be added, e.g. a task which allows children to be added (including after it is activated);
 * if the implementer of this is also a task, then it may be picked up by hierarchical methods (e.g. in DynamicTasks).
 * 
 * @since 0.6.0
 */
@Beta
public interface TaskQueueingContext {

    /** queues the task for submission as part of this queueing context; should mark it as submitted */
    public void queue(Task<?> t);
    
    /** returns a list of queued tasks (immutable copy) */
    public List<Task<?>> getQueue();

    /** returns the last task in the queue, or null if none 
     * @deprecated since 0.7.0 this method is misleading if the caller attempts to block on the task and the queue aborts */
    @Deprecated
    public Task<?> last();

    /** Drains the task queue for this context to complete, ie waits for this context to complete (or terminate early)
     * @param optionalTimeout null to run forever
     * @param includePrimary whether the parent (this context) should also be joined on;
     *   should only be true if invoking this from another task, as otherwise it will be waiting for itself!
     * @param throwFirstError whether to throw the first exception encountered
     * <p>
     * Also note that this waits on tasks so that blocking details on the caller are meaningful.
     */
    public void drain(Duration optionalTimeout, boolean includePrimaryThread, boolean throwFirstError);

    /** Returns the task which is this queueing context */
    public Task<?> asTask();

    /** causes subsequent children failures not to fail the parent */
    public void swallowChildrenFailures();
    
}
