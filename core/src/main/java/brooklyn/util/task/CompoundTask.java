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

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;


/**
 * A {@link Task} that is comprised of other units of work: possibly a heterogeneous mix of {@link Task},
 * {@link Runnable}, {@link Callable} and {@link Closure} instances.
 * 
 * This class holds the collection of child tasks, but subclasses have the responsibility of executing them in a
 * sensible manner by implementing the abstract {@link #runJobs} method.
 */
public abstract class CompoundTask<T> extends BasicTask<List<T>> implements HasTaskChildren {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(CompoundTask.class);
                
    protected final List<Task<? extends T>> children;
    protected final List<Object> result;
    
    /**
     * Constructs a new compound task containing the specified units of work.
     * 
     * @param jobs  A potentially heterogeneous mixture of {@link Runnable}, {@link Callable}, {@link Closure} and {@link Task} can be provided. 
     * @throws IllegalArgumentException if any of the passed child jobs is not one of the above types 
     */
    public CompoundTask(Object... jobs) {
        this( Arrays.asList(jobs) );
    }
    
    /**
     * Constructs a new compound task containing the specified units of work.
     * 
     * @param jobs  A potentially heterogeneous mixture of {@link Runnable}, {@link Callable}, {@link Closure} and {@link Task} can be provided. 
     * @throws IllegalArgumentException if any of the passed child jobs is not one of the above types 
     */
    public CompoundTask(Collection<?> jobs) {
        this(MutableMap.of("tag", "compound"), jobs);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public CompoundTask(Map<String,?> flags, Collection<?> jobs) {
        super(flags);
        super.job = new Callable<List<T>>() {
            @Override public List<T> call() throws Exception {
                return runJobs();
            }
        };
        
        this.result = new ArrayList<Object>(jobs.size());
        this.children = new ArrayList<Task<? extends T>>(jobs.size());
        for (Object job : jobs) {
            Task subtask;
            if (job instanceof TaskAdaptable) { subtask = ((TaskAdaptable)job).asTask(); }
            else if (job instanceof Closure)  { subtask = new BasicTask<T>((Closure) job); }
            else if (job instanceof Callable) { subtask = new BasicTask<T>((Callable) job); }
            else if (job instanceof Runnable) { subtask = new BasicTask<T>((Runnable) job); }
            
            else throw new IllegalArgumentException("Invalid child "+(job == null ? null : job.getClass() + " ("+job+")")+
                " passed to compound task; must be Runnable, Callable, Closure or Task");
            
            BrooklynTaskTags.addTagDynamically(subtask, ManagementContextInternal.SUB_TASK_TAG);
            children.add(subtask);
        }
        
        for (Task<?> t: getChildren()) {
            ((TaskInternal<?>)t).markQueued();
        }
    }

    /** return value needs to be specified by subclass; subclass should also setBlockingDetails 
     * @throws ExecutionException 
     * @throws InterruptedException */    
    protected abstract List<T> runJobs() throws InterruptedException, ExecutionException;
    
    protected void submitIfNecessary(TaskAdaptable<?> task) {
        if (!task.asTask().isSubmitted()) {
            if (BasicExecutionContext.getCurrentExecutionContext() == null) {
                throw new IllegalStateException("Compound task ("+task+") launched from "+this+" missing required execution context");
            } else {
                BasicExecutionContext.getCurrentExecutionContext().submit(task);
            }
        }
    }
    
    public List<Task<? extends T>> getChildrenTyped() {
        return children;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Task<?>> getChildren() {
        return (List) getChildrenTyped();
    }
    
}
