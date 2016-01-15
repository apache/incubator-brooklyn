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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.ExecutionManager;
import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.api.mgmt.entitlement.EntitlementContext;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags.WrappedEntity;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/**
 * A means of executing tasks against an ExecutionManager with a given bucket/set of tags pre-defined
 * (so that it can look like an {@link Executor} and also supply {@link ExecutorService#submit(Callable)}
 */
public class BasicExecutionContext extends AbstractExecutionContext {
    
    private static final Logger log = LoggerFactory.getLogger(BasicExecutionContext.class);
    
    static final ThreadLocal<BasicExecutionContext> perThreadExecutionContext = new ThreadLocal<BasicExecutionContext>();
    
    public static BasicExecutionContext getCurrentExecutionContext() { return perThreadExecutionContext.get(); }

    final ExecutionManager executionManager;
    final Set<Object> tags = new LinkedHashSet<Object>();

    public BasicExecutionContext(ExecutionManager executionManager) {
        this(Collections.emptyMap(), executionManager);
    }
    
    /**
     * Supported flags are {@code tag} and {@code tags}
     * 
     * @see ExecutionManager#submit(Map, Task)
     */
    public BasicExecutionContext(Map<?, ?> flags, ExecutionManager executionManager) {
        this.executionManager = executionManager;

        if (flags.get("tag") != null) tags.add(flags.remove("tag"));
        if (flags.containsKey("tags")) tags.addAll((Collection<?>)flags.remove("tags"));

        // FIXME brooklyn-specific check, just for sanity
        // the context tag should always be a non-proxy entity, because that is what is passed to effector tasks
        // which may require access to internal methods
        for (Object tag: tags) {
            if (tag instanceof BrooklynTaskTags.WrappedEntity) {
                if (Proxy.isProxyClass(((WrappedEntity)tag).entity.getClass())) {
                    log.warn(""+this+" has entity proxy in "+tag);
                }
            }
        }
    }

    public ExecutionManager getExecutionManager() {
        return executionManager;
    }
    
    /** returns tasks started by this context (or tasks which have all the tags on this object) */
    public Set<Task<?>> getTasks() { return executionManager.getTasksWithAllTags((Set<?>)tags); }
     
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected <T> Task<T> submitInternal(Map<?,?> propertiesQ, final Object task) {
        if (task instanceof TaskAdaptable<?> && !(task instanceof Task<?>)) 
            return submitInternal(propertiesQ, ((TaskAdaptable<?>)task).asTask());
        
        Map properties = propertiesQ;
        if (properties.get("tags")==null) properties.put("tags", new ArrayList()); 
        Collection taskTags = (Collection)properties.get("tags");
        
        // FIXME some of this is brooklyn-specific logic, should be moved to a BrooklynExecContext subclass;
        // the issue is that we want to ensure that cross-entity calls switch execution contexts;
        // previously it was all very messy how that was handled (and it didn't really handle it in many cases)
        if (task instanceof Task<?>) taskTags.addAll( ((Task<?>)task).getTags() ); 
        Entity target = BrooklynTaskTags.getWrappedEntityOfType(taskTags, BrooklynTaskTags.TARGET_ENTITY);
        
        if (target!=null && !tags.contains(BrooklynTaskTags.tagForContextEntity(target))) {
            // task is switching execution context boundaries
            /* 
             * longer notes:
             * you fall in to this block if the caller requests a target entity different to the current context 
             * (e.g. where entity X is invoking an effector on Y, it will start in X's context, 
             * but the effector should run in Y's context).
             * 
             * if X is invoking an effector on himself in his own context, or a sensor or other task, it will not come in to this block.
             */
            final ExecutionContext tc = ((EntityInternal)target).getExecutionContext();
            if (log.isDebugEnabled())
                log.debug("Switching task context on execution of "+task+": from "+this+" to "+target+" (in "+Tasks.current()+")");
            
            if (task instanceof Task<?>) {
                final Task<T> t = (Task<T>)task;
                if (!Tasks.isQueuedOrSubmitted(t) && (!(Tasks.current() instanceof HasTaskChildren) || 
                        !Iterables.contains( ((HasTaskChildren)Tasks.current()).getChildren(), t ))) {
                    // this task is switching execution context boundaries _and_ it is not a child and not yet queued,
                    // so wrap it in a task running in this context to keep a reference to the child
                    // (this matters when we are navigating in the GUI; without it we lose the reference to the child 
                    // when browsing in the context of the parent)
                    return submit(Tasks.<T>builder().displayName("Cross-context execution: "+t.getDescription()).dynamic(true).body(new Callable<T>() {
                        public T call() { 
                            return DynamicTasks.get(t); 
                        }
                    }).build());
                } else {
                    // if we are already tracked by parent, just submit it 
                    return tc.submit(t);
                }
            } else {
                // as above, but here we are definitely not a child (what we are submitting isn't even a task)
                // (will only come here if properties defines tags including a target entity, which probably never happens) 
                submit(Tasks.<T>builder().displayName("Cross-context execution").dynamic(true).body(new Callable<T>() {
                    public T call() {
                        if (task instanceof Callable) {
                            return DynamicTasks.queue( Tasks.<T>builder().dynamic(false).body((Callable<T>)task).build() ).getUnchecked();
                        } else if (task instanceof Runnable) {
                            return DynamicTasks.queue( Tasks.<T>builder().dynamic(false).body((Runnable)task).build() ).getUnchecked();
                        } else {
                            throw new IllegalArgumentException("Unhandled task type: "+task+"; type="+(task!=null ? task.getClass() : "null"));
                        }
                    }
                }).build());
            }
        }
        
        EntitlementContext entitlementContext = BrooklynTaskTags.getEntitlement(taskTags);
        if (entitlementContext==null)
        entitlementContext = Entitlements.getEntitlementContext();
        if (entitlementContext!=null) {
            taskTags.add(BrooklynTaskTags.tagForEntitlement(entitlementContext));
        }

        taskTags.addAll(tags);
        
        if (Tasks.current()!=null && BrooklynTaskTags.isTransient(Tasks.current()) 
                && !taskTags.contains(BrooklynTaskTags.NON_TRANSIENT_TASK_TAG) && !taskTags.contains(BrooklynTaskTags.TRANSIENT_TASK_TAG)) {
            // tag as transient if submitter is transient, unless explicitly tagged as non-transient
            taskTags.add(BrooklynTaskTags.TRANSIENT_TASK_TAG);
        }
        
        final Object startCallback = properties.get("newTaskStartCallback");
        properties.put("newTaskStartCallback", new Function<Object,Void>() {
            public Void apply(Object it) {
                registerPerThreadExecutionContext();
                if (startCallback!=null) ExecutionUtils.invoke(startCallback, it);
                return null;
            }});
        
        final Object endCallback = properties.get("newTaskEndCallback");
        properties.put("newTaskEndCallback", new Function<Object,Void>() {
            public Void apply(Object it) {
                try {
                    if (endCallback!=null) ExecutionUtils.invoke(endCallback, it);
                } finally {
                    clearPerThreadExecutionContext();
                }
                return null;
            }});
        
        if (task instanceof Task) {
            return executionManager.submit(properties, (Task)task);
        } else if (task instanceof Callable) {
            return executionManager.submit(properties, (Callable)task);
        } else if (task instanceof Runnable) {
            return (Task<T>) executionManager.submit(properties, (Runnable)task);
        } else {
            throw new IllegalArgumentException("Unhandled task type: task="+task+"; type="+(task!=null ? task.getClass() : "null"));
        }
    }
    
    private void registerPerThreadExecutionContext() { perThreadExecutionContext.set(this); }

    private void clearPerThreadExecutionContext() { perThreadExecutionContext.remove(); }

    @Override
    public boolean isShutdown() {
        return getExecutionManager().isShutdown();
    }

    @Override
    public String toString() {
        return super.toString()+"("+tags+")";
    }
}
