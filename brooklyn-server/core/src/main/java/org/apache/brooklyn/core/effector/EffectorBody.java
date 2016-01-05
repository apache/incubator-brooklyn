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
package org.apache.brooklyn.core.effector;

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.task.DynamicSequentialTask;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;

import com.google.common.annotations.Beta;

/** Typical implementations override {@link #main(ConfigBag)} to do the work of the effector
 * <p>
 * See also {@link EffectorTasks}: possibly this will be deleted in preference for an approach based on {@link EffectorTasks}. 
 * 
 * @since 0.6.0
 **/
@Beta
public abstract class EffectorBody<T> {
    /** Does the work of the effector, either in place, or (better) by building up
     * subtasks, which can by added using {@link DynamicTasks} methods
     * (and various convenience methods which do that automatically; see subclasses of EffectorBody 
     * for more info on usage; or see {@link DynamicSequentialTask} for details of the threading model
     * by which added tasks are placed in a secondary thread)
     * <p>
     * The associated entity can be accessed through the {@link #entity()} method.
     */
    public abstract T call(ConfigBag parameters);
    
    // NB: we could also support an 'init' method which is done at creation,
    // as a place where implementers can describe the structure of the task before it executes
    // (and init gets invoked in EffectorBodyTaskFactory.newTask _before_ the task is submitted and main is called)
    
    
    // ---- convenience method(s) for implementers of main -- see subclasses and *Tasks statics for more
    
    protected EntityInternal entity() {
        return (EntityInternal) BrooklynTaskTags.getTargetOrContextEntity(Tasks.current());
    }
    
    protected <V extends TaskAdaptable<?>> V queue(V task) {
        return DynamicTasks.queue(task);
    }

    protected <V extends TaskAdaptable<?>> void queue(V task1, V task2, V ...tasks) {
        DynamicTasks.queue(task1);
        DynamicTasks.queue(task2);
        for (V task: tasks)
            DynamicTasks.queue(task);
    }

    protected <V extends TaskFactory<?>> void queue(V task1, V task2, V ...tasks) {
        DynamicTasks.queue(task1.newTask());
        DynamicTasks.queue(task2.newTask());
        for (V task: tasks)
            DynamicTasks.queue(task.newTask());
    }
    
    protected <U extends TaskAdaptable<?>> U queue(TaskFactory<U> task) {
        return DynamicTasks.queue(task.newTask());
    }
    
    /** see {@link DynamicTasks#waitForLast()} */
    protected Task<?> waitForLast() {
        return DynamicTasks.waitForLast();
    }

    /** Returns the result of the last task queued in this context, coerced to the given type */
    protected <V> V last(Class<V> type) {
        Task<?> last = waitForLast();
        if (last==null)
            throw new IllegalStateException("No last task available (in "+DynamicTasks.getTaskQueuingContext()+")");
        if (!Tasks.isQueuedOrSubmitted(last))
            throw new IllegalStateException("Last task "+last+" has not been queued or submitted; will not block on its result");
        
        return TypeCoercions.coerce(last.getUnchecked(), type);
    }
}
