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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.management.TaskQueueingContext;
import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

import com.google.common.collect.Iterables;

/** Convenience for creating tasks; note that DynamicSequentialTask is the default */
public class TaskBuilder<T> {

    String name = null;
    String description = null;
    Callable<T> body = null;
    Boolean swallowChildrenFailures = null;
    List<TaskAdaptable<?>> children = MutableList.of();
    Set<Object> tags = MutableSet.of();
    Map<String,Object> flags = MutableMap.of();
    Boolean dynamic = null;
    boolean parallel = false;
    
    public static <T> TaskBuilder<T> builder() {
        return new TaskBuilder<T>();
    }
    
    public TaskBuilder<T> name(String name) {
        this.name = name;
        return this;
    }
    
    public TaskBuilder<T> description(String description) {
        this.description = description;
        return this;
    }
    
    /** whether task that is built has been explicitly specified to be a dynamic task 
     * (ie a Task which is also a {@link TaskQueueingContext}
     * whereby new tasks can be added after creation */
    public TaskBuilder<T> dynamic(boolean dynamic) {
        this.dynamic = dynamic;
        return this;
    }
    
    /** whether task that is built should be parallel; cannot (currently) also be dynamic */
    public TaskBuilder<T> parallel(boolean parallel) {
        this.parallel = parallel;
        return this;
    }
    
    public TaskBuilder<T> body(Callable<T> body) {
        this.body = body;
        return this;
    }
    
    /** sets up a dynamic task not to fail even if children fail */
    public TaskBuilder<T> swallowChildrenFailures(boolean swallowChildrenFailures) {
        this.swallowChildrenFailures = swallowChildrenFailures;
        return this;
    }
    
    public TaskBuilder<T> body(Runnable body) {
        this.body = JavaGroovyEquivalents.<T>toCallable(body);
        return this;
    }

    /** adds a child to the given task; the semantics of how the child is executed is set using
     * {@link #dynamic(boolean)} and {@link #parallel(boolean)} */
    public TaskBuilder<T> add(TaskAdaptable<?> child) {
        children.add(child);
        return this;
    }

    public TaskBuilder<T> addAll(Iterable<? extends TaskAdaptable<?>> additionalChildren) {
        Iterables.addAll(children, additionalChildren);
        return this;
    }

    public TaskBuilder<T> add(TaskAdaptable<?>... additionalChildren) {
        children.addAll(Arrays.asList(additionalChildren));
        return this;
    }

    /** adds a tag to the given task */
    public TaskBuilder<T> tag(Object tag) {
        tags.add(tag);
        return this;
    }
    
    /** adds a flag to the given task */
    public TaskBuilder<T> flag(String flag, Object value) {
        flags.put(flag, value);
        return this;
    }

    /** adds the given flags to the given task */
    public TaskBuilder<T> flags(Map<String,Object> flags) {
        this.flags.putAll(flags);
        return this;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Task<T> build() {
        MutableMap<String, Object> taskFlags = MutableMap.copyOf(flags);
        if (name!=null) taskFlags.put("displayName", name);
        if (description!=null) taskFlags.put("description", description);
        if (!tags.isEmpty()) taskFlags.put("tags", tags);
        
        if (Boolean.FALSE.equals(dynamic) && children.isEmpty()) {
            if (swallowChildrenFailures!=null)
                throw new IllegalArgumentException("Cannot set swallowChildrenFailures for non-dynamic task: "+this);
            return new BasicTask<T>(taskFlags, body);
        }
        
        // prefer dynamic set unless (a) user has said not dynamic, or (b) it's parallel (since there is no dynamic parallel yet)
        // dynamic has better cancel (will interrupt the thread) and callers can submit tasks flexibly;
        // however dynamic uses an extra thread and task and is noisy for contexts which don't need it
        if (Boolean.TRUE.equals(dynamic) || (dynamic==null && !parallel)) {
            if (parallel)
                throw new UnsupportedOperationException("No implementation of parallel dynamic aggregate task available");
            DynamicSequentialTask<T> result = new DynamicSequentialTask<T>(taskFlags, body);
            if (swallowChildrenFailures!=null && swallowChildrenFailures.booleanValue()) result.swallowChildrenFailures();
            for (TaskAdaptable t: children)
                result.queue(t.asTask());
            return result;
        }
        
        // T must be of type List<V> for these to be valid
        if (body != null) {
            throw new UnsupportedOperationException("No implementation of non-dynamic task with both body and children");
        }
        if (swallowChildrenFailures!=null) {
            throw new IllegalArgumentException("Cannot set swallowChildrenFailures for non-dynamic task: "+this);
        }
        
        if (parallel)
            return new ParallelTask(taskFlags, children);
        else
            return new SequentialTask(taskFlags, children);
    }

    /** returns a a factory based on this builder */
    public TaskFactory<Task<T>> buildFactory() {
        return new TaskFactory<Task<T>>() {
            public Task<T> newTask() {
                return build();
            }
        };
    }
    
    @Override
    public String toString() {
        return super.toString()+"["+name+"]";
    }
}
