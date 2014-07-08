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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ExecutionContext;
import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.management.TaskQueueingContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class Tasks {
    
    private static final Logger log = LoggerFactory.getLogger(Tasks.class);
    
    /** convenience for setting "blocking details" on any task where the current thread is running;
     * typically invoked prior to a wait, for transparency to a user;
     * then invoked with 'null' just after the wait */
    public static String setBlockingDetails(String description) {
        Task<?> current = current();
        if (current instanceof TaskInternal)
            return ((TaskInternal<?>)current).setBlockingDetails(description);
        return null;
    }
    public static void resetBlockingDetails() {
        Task<?> current = current();
        if (current instanceof TaskInternal)
            ((TaskInternal<?>)current).resetBlockingDetails(); 
    }
    public static Task<?> setBlockingTask(Task<?> blocker) {
        Task<?> current = current();
        if (current instanceof TaskInternal)
            return ((TaskInternal<?>)current).setBlockingTask(blocker);
        return null;
    }
    public static void resetBlockingTask() {
        Task<?> current = current();
        if (current instanceof TaskInternal)
            ((TaskInternal<?>)current).resetBlockingTask(); 
    }
    
    /** convenience for setting "blocking details" on any task where the current thread is running,
     * while the passed code is executed; often used from groovy as
     * <pre>{@code withBlockingDetails("sleeping 5s") { Thread.sleep(5000); } }</pre>
     * If code block is null, the description is set until further notice (not cleareed). */
    @SuppressWarnings("rawtypes")
    public static <T> T withBlockingDetails(String description, Callable<T> code) throws Exception {
        Task current = current();
        if (code==null) {
            log.warn("legacy invocation of withBlockingDetails with null code block, ignoring");
            return null;
        }
        String prevBlockingDetails = null;
        if (current instanceof TaskInternal) {
            prevBlockingDetails = ((TaskInternal)current).setBlockingDetails(description);
        } 
        try {
            return code.call();
        } finally {
            if (current instanceof TaskInternal)
                ((TaskInternal)current).setBlockingDetails(prevBlockingDetails); 
        }
    }

    /** the {@link Task} where the current thread is executing, if executing in a Task, otherwise null */
    @SuppressWarnings("rawtypes")
    public static Task current() { return BasicExecutionManager.getPerThreadCurrentTask().get(); }

    /** @see #resolveValue(Object, Class, ExecutionContext, String) */
    public static <T> T resolveValue(Object v, Class<T> type, ExecutionContext exec) throws ExecutionException, InterruptedException {
        return resolveValue(v, type, exec, null);
    }
    
    /** attempt to resolve the given value as the given type, waiting on futures, submitting if necessary,
     * and coercing as allowed by TypeCoercions;
     * contextMessage (optional) will be displayed in status reports while it waits (e.g. the name of the config key being looked up) */
    public static <T> T resolveValue(Object v, Class<T> type, ExecutionContext exec, String contextMessage) throws ExecutionException, InterruptedException {
        return resolveValue(v, type, exec, contextMessage, false);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> T resolveValue(Object v, Class<T> type, ExecutionContext exec, String contextMessage, boolean forceDeep) throws ExecutionException, InterruptedException {
        if (type==null) 
            throw new NullPointerException("type cannot be null in resolveValue, for '"+v+"'"+(contextMessage!=null ? ", "+contextMessage : ""));
        //if the expected type is a closure or map and that's what we have, we're done (or if it's null);
        //but not allowed to return a future or DeferredSupplier as the resolved value
        if (v==null || (!forceDeep && type.isInstance(v) && !Future.class.isInstance(v) && !DeferredSupplier.class.isInstance(v)))
            return (T) v;
        try {
            //if it's a task or a future, we wait for the task to complete
        	if (v instanceof TaskAdaptable<?>) {
                //if it's a task, we make sure it is submitted
                //(perhaps could run it here? ... tbd)
                if (!((TaskAdaptable<?>) v).asTask().isSubmitted() ) {
                    exec.submit(((TaskAdaptable<?>) v).asTask());
                }
            }
            
            if (v instanceof Future) {
                final Future<?> vfuture = (Future<?>) v;
                
                //including tasks, above
                if (!vfuture.isDone()) {
                    final AtomicReference<Object> vref = new AtomicReference<Object>(v);
                    
                    withBlockingDetails("Waiting for "+(contextMessage!=null ? contextMessage : ""+v), 
                            new Callable<Void>() {
                        public Void call() throws Exception {
                            vref.set( vfuture.get() );
                            return null;
                        }
                    });
                    
                    v = vref.get();
                    
                } else {
                    v = vfuture.get();
                }
                
            } else if (v instanceof DeferredSupplier<?>) {
                v = ((DeferredSupplier<?>) v).get();
                
            } else if (v instanceof Map) {
                //and if a map or list we look inside
                Map result = Maps.newLinkedHashMap();
                for (Map.Entry<?,?> entry : ((Map<?,?>)v).entrySet()) {
                    result.put(entry.getKey(), resolveValue(entry.getValue(), type, exec,
                            (contextMessage!=null ? contextMessage+", " : "") + "map entry "+entry.getKey(), forceDeep));
                }
                return (T) result;
                
            } else if (v instanceof List) {
                List result = Lists.newArrayList();
                int count = 0;
                for (Object it : (List)v) {
                    result.add(resolveValue(it, type, exec, 
                            (contextMessage!=null ? contextMessage+", " : "") + "list entry "+count, forceDeep));
                    count++;
                }
                return (T) result;
                
            } else if (v instanceof Set) {
                Set result = Sets.newLinkedHashSet();
                int count = 0;
                for (Object it : (Set)v) {
                    result.add(resolveValue(it, type, exec, 
                            (contextMessage!=null ? contextMessage+", " : "") + "list entry "+count, forceDeep));
                    count++;
                }
                return (T) result;
                
            } else if (v instanceof Iterable) {
                List result = Lists.newArrayList();
                int count = 0;
                for (Object it : (Iterable)v) {
                    result.add(resolveValue(it, type, exec, 
                            (contextMessage!=null ? contextMessage+", " : "") + "list entry "+count, forceDeep));
                    count++;
                }
                return (T) result;
                
            } else {
                return TypeCoercions.coerce(v, type);
            }
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Error resolving "+(contextMessage!=null ? contextMessage+", " : "")+v+", in "+exec+": "+e, e);
        }
        return resolveValue(v, type, exec, contextMessage, forceDeep);
    }

    /**
     * @see #resolveDeepValue(Object, Class, ExecutionContext, String)
     */
    public static Object resolveDeepValue(Object v, Class<?> type, ExecutionContext exec) throws ExecutionException, InterruptedException {
        return resolveDeepValue(v, type, exec, null);
    }

    /**
     * Resolves the given object, blocking on futures and coercing it to the given type. If the object is a 
     * map or iterable (or a list of map of maps, etc, etc) then walks these maps/iterables to convert all of 
     * their values to the given type. For example, the following will return a list containing a map with "1"="true":
     * 
     *   {@code Object result = resolveDeepValue(ImmutableList.of(ImmutableMap.of(1, true)), String.class, exec)} 
     * 
     * This differs from {@link #resolveValue(Object, Class, ExecutionContext, String)} mainly in its 
     * use of generics and its return type. Even though the {@link #resolveValue(Object, Class, ExecutionContext, String)}
     * method does "deep" conversion of futures contained within iterables/maps, the return type implies
     * that it is the top-level object that should be coerced. For example, the following will try to return a String, 
     * when in fact it is a map, giving a {@link ClassCastException}.
     * 
     *   {@code String result = resolveValue(ImmutableList.of(ImmutableMap.of(1, true)), String.class, exec)}
     *   
     * The one other difference of note is that this forces the resolution to go deep when the type is vague,
     * e.g. if the requested type is an Object, {@link #resolveValue(Object, Class, ExecutionContext, String)}
     * will decide that it matches a Map and not recurse on it, whereas this will recurse on it.
     */
    public static Object resolveDeepValue(Object v, Class<?> type, ExecutionContext exec, String contextMessage) throws ExecutionException, InterruptedException {
        return resolveValue(v, type, exec, contextMessage, true);
    }

    /** sets extra status details on the current task, if possible (otherwise does nothing).
     * the extra status is presented in Task.getStatusDetails(true)
     */
    public static void setExtraStatusDetails(String notes) {
        Task<?> current = current();
        if (current instanceof TaskInternal)
            ((TaskInternal<?>)current).setExtraStatusText(notes); 
    }

    public static <T> TaskBuilder<T> builder() {
        return TaskBuilder.<T>builder();
    }
    
    private static Task<?>[] asTasks(TaskAdaptable<?> ...tasks) {
        Task<?>[] result = new Task<?>[tasks.length];
        for (int i=0; i<tasks.length; i++)
            result[i] = tasks[i].asTask();
        return result;
    }
    
    public static Task<List<?>> parallel(TaskAdaptable<?> ...tasks) {
        return parallelInternal("parallelised tasks", asTasks(tasks));
    }
    
    public static Task<List<?>> parallel(String name, TaskAdaptable<?> ...tasks) {
        return parallelInternal(name, asTasks(tasks));
    }

    public static Task<List<?>> parallel(String name, Iterable<? extends TaskAdaptable<?>> tasks) {
        return parallelInternal(name, asTasks(Iterables.toArray(tasks, TaskAdaptable.class)));
    }
    
    public static Task<List<?>> parallelInternal(String name, Task<?>[] tasks) {
        return Tasks.<List<?>>builder().name(name).parallel(true).add(tasks).build();
    }

    public static Task<List<?>> sequential(TaskAdaptable<?> ...tasks) {
        return sequentialInternal("sequential tasks", asTasks(tasks));
    }
    
    public static Task<List<?>> sequential(String name, TaskAdaptable<?> ...tasks) {
        return sequentialInternal(name, asTasks(tasks));
    }
    
    private static Task<List<?>> sequentialInternal(String name, Task<?>[] tasks) {
        return Tasks.<List<?>>builder().name(name).parallel(false).add(tasks).build();
    }

    public static TaskFactory<?> sequential(TaskFactory<?> ...taskFactories) {
        return sequentialInternal("sequential tasks", taskFactories);
    }
    
    public static TaskFactory<?> sequential(String name, TaskFactory<?> ...taskFactories) {
        return sequentialInternal(name, taskFactories);
    }
    
    private static TaskFactory<?> sequentialInternal(final String name, final TaskFactory<?> ...taskFactories) {
        return new TaskFactory<TaskAdaptable<?>>() {
            @Override
            public TaskAdaptable<?> newTask() {
                TaskBuilder<List<?>> tb = Tasks.<List<?>>builder().name(name).parallel(false);
                for (TaskFactory<?> tf: taskFactories)
                    tb.add(tf.newTask().asTask());
                return tb.build();
            }
        };
    }

    /** returns the first tag found on the given task which matches the given type, looking up the submission hierarachy if necessary */
    @SuppressWarnings("unchecked")
    public static <T> T tag(@Nullable Task<?> task, Class<T> type, boolean recurseHierarchy) {
        // support null task to make it easier for callers to walk hierarchies
        if (task==null) return null;
        for (Object tag: task.getTags())
            if (type.isInstance(tag)) return (T)tag;
        if (!recurseHierarchy) return null;
        return tag(task.getSubmittedByTask(), type, true);
    }
    
    public static boolean isAncestorCancelled(Task<?> t) {
        if (t==null) return false;
        if (t.isCancelled()) return true;
        return isAncestorCancelled(t.getSubmittedByTask());
    }

    public static boolean isQueuedOrSubmitted(TaskAdaptable<?> task) {
        return ((TaskInternal<?>)task.asTask()).isQueuedOrSubmitted();
    }
    
    /**
     * Adds the given task to the given context. Does not throw an exception if the addition fails.
     * @return true if the task was added, false otherwise.
     */
    public static boolean tryQueueing(TaskQueueingContext adder, TaskAdaptable<?> task) {
        if (task==null || isQueuedOrSubmitted(task))
            return false;
        try {
            adder.queue(task.asTask());
            return true;
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Could not add task "+task+" at "+adder+": "+e);
            return false;
        }        
    }
    
    public static <T> Supplier<T> supplier(final TaskAdaptable<T> task) {
        return new Supplier<T>() {
            @Override
            public T get() {
                return task.asTask().getUnchecked();
            }
        };
    }
    
    /** return all children tasks of the given tasks, if it has children, else empty list */
    public static Iterable<Task<?>> children(Task<?> task) {
        if (task instanceof HasTaskChildren)
            return ((HasTaskChildren)task).getChildren();
        return Collections.emptyList();
    }
    
    /** returns failed tasks */
    public static Iterable<Task<?>> failed(Iterable<Task<?>> subtasks) {
        return Iterables.filter(subtasks, new Predicate<Task<?>>() {
            @Override
            public boolean apply(Task<?> input) {
                return input.isError();
            }
        });
    }
    
    /** returns the task, its children, and all its children, and so on;
     * @param root task whose descendants should be iterated
     * @param parentFirst whether to put parents before children or after
     */
    public static Iterable<Task<?>> descendants(Task<?> root, final boolean parentFirst) {
        Iterable<Task<?>> descs = Iterables.concat(Iterables.transform(Tasks.children(root), new Function<Task<?>,Iterable<Task<?>>>() {
            @Override
            public Iterable<Task<?>> apply(Task<?> input) {
                return descendants(input, parentFirst);
            }
        }));
        if (parentFirst) return Iterables.concat(Collections.singleton(root), descs);
        else return Iterables.concat(descs, Collections.singleton(root));
    }

    /** returns the error thrown by the task if {@link Task#isError()}, or null if no error or not done */
    public static Throwable getError(Task<?> t) {
        if (t==null) return null;
        if (!t.isDone()) return null;
        if (t.isCancelled()) return new CancellationException();
        try {
            t.get();
            return null;
        } catch (Throwable error) {
            // do not propagate as we are pretty much guaranteed above that it wasn't this
            // thread which originally threw the error
            return error;
        }
    }
    public static Task<Void> fail(final String name, final Throwable optionalError) {
        return Tasks.<Void>builder().dynamic(false).name(name).body(new Runnable() { public void run() { 
            if (optionalError!=null) throw Exceptions.propagate(optionalError); else throw new RuntimeException("Failed: "+name);
        } }).build();
    }

    /** marks the current task inessential; this mainly matters if the task is running in a parent
     * {@link TaskQueueingContext} and we don't want the parent to fail if this task fails
     * <p>
     * no-op (silently ignored) if not in a task */
    public static void markInessential() {
        Task<?> task = Tasks.current();
        if (task==null) {
            TaskQueueingContext qc = DynamicTasks.getTaskQueuingContext();
            if (qc!=null) task = qc.asTask();
        }
        if (task!=null) {
            TaskTags.markInessential(task);
        }
    }
    
    /** causes failures in subtasks of the current task not to fail the parent;
     * no-op if not in a {@link TaskQueueingContext}.
     * <p>
     * essentially like a {@link #markInessential()} on all tasks in the current 
     * {@link TaskQueueingContext}, including tasks queued subsequently */
    @Beta
    public static void swallowChildrenFailures() {
        Preconditions.checkNotNull(DynamicTasks.getTaskQueuingContext(), "Task queueing context required here");
        TaskQueueingContext qc = DynamicTasks.getTaskQueuingContext();
        if (qc!=null) {
            qc.swallowChildrenFailures();
        }
    }

    /** as {@link TaskTags#addTagDynamically(TaskAdaptable, Object)} but for current task, skipping if no current task */
    public static void addTagDynamically(Object tag) {
        Task<?> t = Tasks.current();
        if (t!=null) TaskTags.addTagDynamically(t, tag);
    }
    
}
