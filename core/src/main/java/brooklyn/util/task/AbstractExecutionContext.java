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

import java.util.Map;
import java.util.concurrent.Callable;

import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;

import com.google.common.collect.Maps;

public abstract class AbstractExecutionContext implements ExecutionContext {

    /**
     * Submits the given runnable/callable/task for execution (in a separate thread);
     * supported keys in the map include: tags (add'l tags to put on the resulting task), 
     * description (string), and others as described in the reference below
     *   
     * @see ExecutionManager#submit(Map, Task) 
     */
    public Task<?> submit(Map<?,?> properties, Runnable runnable) { return submitInternal(properties, runnable); }
    
    /** @see #submit(Map, Runnable) */
    public Task<?> submit(Runnable runnable) { return submitInternal(Maps.newLinkedHashMap(), runnable); }
 
    /** @see #submit(Map, Runnable) */
    public <T> Task<T> submit(Callable<T> callable) { return submitInternal(Maps.newLinkedHashMap(), callable); }
    
    /** @see #submit(Map, Runnable) */
    public <T> Task<T> submit(Map<?,?> properties, Callable<T> callable) { return submitInternal(properties, callable); }
 
    /** @see #submit(Map, Runnable) */
    public <T> Task<T> submit(TaskAdaptable<T> task) { return submitInternal(Maps.newLinkedHashMap(), task.asTask()); }

    /** @see #submit(Map, Runnable) */
    public <T> Task<T> submit(Map<?,?> properties, TaskAdaptable<T> task) { return submitInternal(properties, task.asTask()); }

    /**
     * Provided for compatibility
     * 
     * Submit is preferred if a handle on the resulting Task is desired (although a task can be passed in so this is not always necessary) 
     *
     * @see #submit(Map, Runnable) 
     */
    public void execute(Runnable r) { submit(r); }

    /** does the work internally of submitting the task; note that the return value may be a wrapper task even if a task is passed in,
     * if the execution context where the target should run is different (e.g. submitting an effector task cross-context) */
    protected abstract <T> Task<T> submitInternal(Map<?,?> properties, Object task);
    
}
