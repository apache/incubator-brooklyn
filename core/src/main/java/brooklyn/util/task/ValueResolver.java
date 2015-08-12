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

import org.apache.brooklyn.management.ExecutionContext;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.TaskAdaptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.repeat.Repeater;
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
public class ValueResolver<T> implements DeferredSupplier<T> {

    /** 
     * Period to wait if we're expected to return real quick 
     * but we want fast things to have time to finish.
     * <p>
     * Timings are always somewhat arbitrary but this at least
     * allows some intention to be captured in code rather than arbitrary values. */
    public static Duration REAL_QUICK_WAIT = Duration.millis(50);
    /** 
     * Period to wait if we're expected to return quickly 
     * but we want to be a bit more generous for things to finish,
     * without letting a caller get annoyed. 
     * <p>
     * See {@link #REAL_QUICK_WAIT}. */
    public static Duration PRETTY_QUICK_WAIT = Duration.millis(200);
    
    /** Period to wait when we have to poll but want to give the illusion of no wait.
     * See {@link Repeater#DEFAULT_REAL_QUICK_PERIOD} */ 
    public static Duration REAL_QUICK_PERIOD = Repeater.DEFAULT_REAL_QUICK_PERIOD;
    
    private static final Logger log = LoggerFactory.getLogger(ValueResolver.class);
    
    final Object value;
    final Class<T> type;
    ExecutionContext exec;
    String description;
    boolean forceDeep;
    /** null means do it if you can; true means always, false means never */
    Boolean embedResolutionInTask;
    /** timeout on execution, if possible, or if embedResolutionInTask is true */
    Duration timeout;
    boolean isTransientTask = true;
    
    T defaultValue = null;
    boolean returnDefaultOnGet = false;
    boolean swallowExceptions = false;
    
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
        
        // default value and swallow exceptions do not need to be nested
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

    /** returns a copy of this resolver which can be queried, even if the original (single-use instance) has already been copied */
    public ValueResolver<T> clone() {
        ValueResolver<T> result = new ValueResolver<T>(value, type)
            .context(exec).description(description)
            .embedResolutionInTask(embedResolutionInTask)
            .deep(forceDeep)
            .timeout(timeout);
        if (returnDefaultOnGet) result.defaultValue(defaultValue);
        if (swallowExceptions) result.swallowExceptions();
        return result;
    }
    
    /** execution context to use when resolving; required if resolving unsubmitted tasks or running with a time limit */
    public ValueResolver<T> context(ExecutionContext exec) {
        this.exec = exec;
        return this;
    }
    /** as {@link #context(ExecutionContext)} for use from an entity */
    public ValueResolver<T> context(Entity entity) {
        return context(entity!=null ? ((EntityInternal)entity).getExecutionContext() : null);
    }
    
    /** sets a message which will be displayed in status reports while it waits (e.g. the name of the config key being looked up) */
    public ValueResolver<T> description(String description) {
        this.description = description;
        return this;
    }
    
    /** sets a default value which will be returned on a call to {@link #get()} if the task does not complete
     * or completes with an error
     * <p>
     * note that {@link #getMaybe()} returns an absent object even in the presence of
     * a default, so that any error can still be accessed */
    public ValueResolver<T> defaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
        this.returnDefaultOnGet = true;
        return this;
    }

    /** indicates that no default value should be returned on a call to {@link #get()}, and instead it should throw
     * (this is the default; this method is provided to undo a call to {@link #defaultValue(Object)}) */
    public ValueResolver<T> noDefaultValue() {
        this.returnDefaultOnGet = false;
        this.defaultValue = null;
        return this;
    }
    
    /** indicates that exceptions in resolution should not be thrown on a call to {@link #getMaybe()}, 
     * but rather used as part of the {@link Maybe#get()} if it's absent, 
     * and swallowed altogether on a call to {@link #get()} in the presence of a {@link #defaultValue(Object)} */
    public ValueResolver<T> swallowExceptions() {
        this.swallowExceptions = true;
        return this;
    }
    
    /** whether the task should be marked as transient; defaults true */
    public ValueResolver<T> transientTask(boolean isTransientTask) {
        this.isTransientTask = isTransientTask;
        return this;
    }
    
    public Maybe<T> getDefault() {
        if (returnDefaultOnGet) return Maybe.of(defaultValue);
        else return Maybe.absent("No default value set");
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
        Maybe<T> m = getMaybe();
        if (m.isPresent()) return m.get();
        if (returnDefaultOnGet) return defaultValue;
        return m.get();
    }
    
    public Maybe<T> getMaybe() {
        Maybe<T> result = getMaybeInternal();
        if (log.isTraceEnabled()) {
            log.trace(this+" evaluated as "+result);
        }
        return result;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected Maybe<T> getMaybeInternal() {
        if (started.getAndSet(true))
            throw new IllegalStateException("ValueResolver can only be used once");
        
        if (expired) return Maybe.absent("Nested resolution of "+getOriginalValue()+" did not complete within "+timeout);
        
        ExecutionContext exec = this.exec;
        if (exec==null) {
            // if execution context not specified, take it from the current task if present
            exec = BasicExecutionContext.getCurrentExecutionContext();
        }
        
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

                if ((!Boolean.FALSE.equals(embedResolutionInTask) && (exec!=null || timeout!=null)) || Boolean.TRUE.equals(embedResolutionInTask)) {
                    if (exec==null)
                        return Maybe.absent("Embedding in task needed for '"+getDescription()+"' but no execution context available");
                        
                    Callable<Object> callable = new Callable<Object>() {
                        public Object call() throws Exception {
                            try {
                                Tasks.setBlockingDetails("Retrieving "+vf);
                                return ((DeferredSupplier<?>) vf).get();
                            } finally {
                                Tasks.resetBlockingDetails();
                            }
                        } };
                    String description = getDescription();
                    TaskBuilder<Object> vb = Tasks.<Object>builder().body(callable).name("Resolving dependent value").description(description);
                    if (isTransientTask) vb.tag(BrooklynTaskTags.TRANSIENT_TASK_TAG);
                    Task<Object> vt = exec.submit(vb.build());
                    // TODO to handle immediate resolution, it would be nice to be able to submit 
                    // so it executes in the current thread,
                    // or put a marker in the target thread or task while it is running that the task 
                    // should never wait on anything other than another value being resolved 
                    // (though either could recurse infinitely) 
                    Maybe<Object> vm = Durations.get(vt, timer);
                    vt.cancel(true);
                    if (vm.isAbsent()) return (Maybe<T>)vm;
                    v = vm.get();
                    
                } else {
                    try {
                        Tasks.setBlockingDetails("Retrieving (non-task) "+vf);
                        v = ((DeferredSupplier<?>) vf).get();
                    } finally {
                        Tasks.resetBlockingDetails();
                    }
                }

            } else if (v instanceof Map) {
                //and if a map or list we look inside
                Map result = Maps.newLinkedHashMap();
                for (Map.Entry<?,?> entry : ((Map<?,?>)v).entrySet()) {
                    Maybe<?> kk = new ValueResolver(entry.getKey(), type, this)
                        .description( (description!=null ? description+", " : "") + "map key "+entry.getKey() )
                        .getMaybe();
                    if (kk.isAbsent()) return (Maybe<T>)kk;
                    Maybe<?> vv = new ValueResolver(entry.getValue(), type, this)
                        .description( (description!=null ? description+", " : "") + "map value for key "+kk.get() )
                        .getMaybe();
                    if (vv.isAbsent()) return (Maybe<T>)vv;
                    result.put(kk.get(), vv.get());
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
            Exceptions.propagateIfFatal(e);
            
            IllegalArgumentException problem = new IllegalArgumentException("Error resolving "+(description!=null ? description+", " : "")+v+", in "+exec+": "+e, e);
            if (swallowExceptions) {
                if (log.isDebugEnabled())
                    log.debug("Resolution of "+this+" failed, swallowing and returning: "+e);
                return Maybe.absent(problem);
            }
            if (log.isDebugEnabled())
                log.debug("Resolution of "+this+" failed, throwing: "+e);
            throw problem;
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
    
    @Override
    public String toString() {
        return JavaClassNames.cleanSimpleClassName(this)+"["+JavaClassNames.cleanSimpleClassName(type)+" "+value+"]";
    }
}