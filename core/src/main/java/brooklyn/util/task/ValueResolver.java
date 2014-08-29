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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import brooklyn.management.ExecutionContext;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Durations;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

/** 
 * Resolves a given object, as follows:
 * <li> If it is a {@link Tasks} or a {@link DeferredSupplier} then get its contents
 * <li> If it's a map and {@link #deep(boolean)} is requested, it applies resolution to contents
 * <li> It applies coercion
 * <p>
 * Fluent-style API exposes a number of other options.
 */
public class ValueResolver<T> {
    
    final Object value;
    final Class<T> type;
    ExecutionContext exec;
    String description;
    boolean forceDeep;
    /** null means do it if you can; true means always, false means never */
    Boolean embedResolutionInTask;
    /** timeout on execution, if possible, or if embedResolutionInTask is true */
    Duration timeout;
    
    // internal fields
    final Object parentOriginalValue;
    final CountdownTimer parentTimer;
    AtomicBoolean started = new AtomicBoolean(false);
    boolean expired;
    
    ValueResolver(Object v, Class<T> type) {
        this.value = v;
        this.type = type;
        checkTypeNotNull();
        parentOriginalValue = null;
        parentTimer = null;
    }
    
    ValueResolver(Object v, Class<T> type, ValueResolver<?> parent) {
        this.value = v;
        this.type = type;
        checkTypeNotNull();
        
        exec = parent.exec;
        description = parent.description;
        forceDeep = parent.forceDeep;
        embedResolutionInTask = parent.embedResolutionInTask;

        parentOriginalValue = parent.getOriginalValue();

        timeout = parent.timeout;
        parentTimer = parent.parentTimer;
        if (parentTimer!=null && parentTimer.isExpired())
            expired = true;
    }

    public static class ResolverBuilderPretype {
        final Object v;
        public ResolverBuilderPretype(Object v) {
            this.v = v;
        }
        public <T> ValueResolver<T> as(Class<T> type) {
            return new ValueResolver<T>(v, type);
        }
    }
    
    /** execution context to use when resolving; required if resolving unsubmitted tasks or running with a time limit */
    public ValueResolver<T> context(ExecutionContext exec) {
        this.exec = exec;
        return this;
    }
    
    /** sets a message which will be displayed in status reports while it waits (e.g. the name of the config key being looked up) */
    public ValueResolver<T> description(String description) {
        this.description = description;
        return this;
    }
    
    /** causes nested structures (maps, lists) to be descended and nested unresolved values resolved */
    public ValueResolver<T> deep(boolean forceDeep) {
        this.forceDeep = forceDeep;
        return this;
    }

    /** if true, forces execution of a deferred supplier to be run in a task;
     * if false, it prevents it (meaning time limits may not be applied);
     * if null, the default, it runs in a task if a time limit is applied.
     * <p>
     * running inside a task is required for some {@link DeferredSupplier}
     * instances which look up a task {@link ExecutionContext}. */
    public ValueResolver<T> embedResolutionInTask(Boolean embedResolutionInTask) {
        this.embedResolutionInTask = embedResolutionInTask;
        return this;
    }
    
    /** sets a time limit on executions
     * <p>
     * used for {@link Task} and {@link DeferredSupplier} instances.
     * may require an execution context at runtime. */
    public ValueResolver<T> timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
    
    protected void checkTypeNotNull() {
        if (type==null) 
            throw new NullPointerException("type must be set to resolve, for '"+value+"'"+(description!=null ? ", "+description : ""));
    }

    public T get() {
        return getMaybe().get();
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Maybe<T> getMaybe() {
        if (started.getAndSet(true))
            throw new IllegalStateException("ValueResolver can only be used once");
        
        if (expired) return Maybe.absent("Nested resolution of "+getOriginalValue()+" did not complete within "+timeout);
        
        CountdownTimer timerU = parentTimer;
        if (timerU==null && timeout!=null)
            timerU = timeout.countdownTimer();
        final CountdownTimer timer = timerU;
        if (timer!=null && !timer.isRunning())
            timer.start();
        
        checkTypeNotNull();
        Object v = this.value;
        
        //if the expected type is a closure or map and that's what we have, we're done (or if it's null);
        //but not allowed to return a future or DeferredSupplier as the resolved value
        if (v==null || (!forceDeep && type.isInstance(v) && !Future.class.isInstance(v) && !DeferredSupplier.class.isInstance(v)))
            return Maybe.of((T) v);
        
        try {
            //if it's a task or a future, we wait for the task to complete
            if (v instanceof TaskAdaptable<?>) {
                //if it's a task, we make sure it is submitted
                if (!((TaskAdaptable<?>) v).asTask().isSubmitted() ) {
                    // TODO could try to get exec context from Tasks.current() ... should we?
                    if (exec==null)
                        return Maybe.absent("Value for unsubmitted task '"+getDescription()+"' requested but no execution context available");
                    exec.submit(((TaskAdaptable<?>) v).asTask());
                }
            }

            if (v instanceof Future) {
                final Future<?> vfuture = (Future<?>) v;

                //including tasks, above
                if (!vfuture.isDone()) {
                    Callable<Maybe> callable = new Callable<Maybe>() {
                        public Maybe call() throws Exception {
                            return Durations.get(vfuture, timer);
                        } };

                    String description = getDescription();
                    Maybe vm = Tasks.withBlockingDetails("Waiting for "+description, callable);
                    if (vm.isAbsent()) return vm;
                    v = vm.get();

                } else {
                    v = vfuture.get();
                    
                }

            } else if (v instanceof DeferredSupplier<?>) {
                final Object vf = v;
                Callable<Object> callable = new Callable<Object>() {
                    public Object call() throws Exception {
                        return ((DeferredSupplier<?>) vf).get();
                    } };
                    
                if (Boolean.TRUE.equals(embedResolutionInTask) || timeout!=null) {
                    if (exec==null)
                        return Maybe.absent("Embedding in task needed for '"+getDescription()+"' but no execution context available");
                        
                    String description = getDescription();
                    Task<Object> vt = exec.submit(Tasks.<Object>builder().body(callable).name("Resolving dependent value").description(description).build());
                    Maybe<Object> vm = Durations.get(vt, timer);
                    vt.cancel(true);
                    if (vm.isAbsent()) return (Maybe<T>)vm;
                    v = vm.get();
                    
                } else {
                    v = callable.call();
                    
                }

            } else if (v instanceof Map) {
                //and if a map or list we look inside
                Map result = Maps.newLinkedHashMap();
                for (Map.Entry<?,?> entry : ((Map<?,?>)v).entrySet()) {
                    Maybe<?> vv = new ValueResolver(entry.getValue(), type, this)
                        .description( (description!=null ? description+", " : "") + "map entry "+entry.getKey() )
                        .getMaybe();
                    if (vv.isAbsent()) return (Maybe<T>)vv;
                    result.put(entry.getKey(), vv.get());
                }
                return Maybe.of((T) result);

            } else if (v instanceof Set) {
                Set result = Sets.newLinkedHashSet();
                int count = 0;
                for (Object it : (Set)v) {
                    Maybe<?> vv = new ValueResolver(it, type, this)
                        .description( (description!=null ? description+", " : "") + "entry "+count )
                        .getMaybe();
                    if (vv.isAbsent()) return (Maybe<T>)vv;
                    result.add(vv.get());
                    count++;
                }
                return Maybe.of((T) result);

            } else if (v instanceof Iterable) {
                List result = Lists.newArrayList();
                int count = 0;
                for (Object it : (Iterable)v) {
                    Maybe<?> vv = new ValueResolver(it, type, this)
                        .description( (description!=null ? description+", " : "") + "entry "+count )
                        .getMaybe();
                    if (vv.isAbsent()) return (Maybe<T>)vv;
                    result.add(vv.get());
                    count++;
                }
                return Maybe.of((T) result);

            } else {
                return TypeCoercions.tryCoerce(v, TypeToken.of(type));
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Error resolving "+(description!=null ? description+", " : "")+v+", in "+exec+": "+e, e);
        }
        
        return new ValueResolver(v, type, this).getMaybe();
    }

    protected String getDescription() {
        return description!=null ? description : ""+value;
    }
    protected Object getOriginalValue() {
        if (parentOriginalValue!=null) return parentOriginalValue;
        return value;
    }
}