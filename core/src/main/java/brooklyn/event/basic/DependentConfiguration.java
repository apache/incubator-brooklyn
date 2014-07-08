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
package brooklyn.event.basic;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import groovy.lang.Closure;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ExecutionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.DeferredSupplier;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.ParallelTask;
import brooklyn.util.task.TaskInternal;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/** Conveniences for making tasks which run in entity {@link ExecutionContext}s, subscribing to attributes from other entities, possibly transforming those;
 * these {@link Task} instances are typically passed in {@link EntityLocal#setConfig(ConfigKey, Object)}.
 * <p>
 * If using a lot it may be useful to:
 * <pre>
 * {@code
 *   import static brooklyn.event.basic.DependentConfiguration.*;
 * }
 * </pre>
 */
public class DependentConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DependentConfiguration.class);
    
    //not instantiable, only a static helper
    private DependentConfiguration() {}

    /**
     * Default readiness is Groovy truth.
     * 
     * @see #attributeWhenReady(Entity, AttributeSensor, Predicate)
     */
    public static <T> Task<T> attributeWhenReady(Entity source, AttributeSensor<T> sensor) {
        return attributeWhenReady(source, sensor, GroovyJavaMethods.truthPredicate());
    }
    
    public static <T> Task<T> attributeWhenReady(Entity source, AttributeSensor<T> sensor, Closure ready) {
        Predicate<T> readyPredicate = (ready != null) ? GroovyJavaMethods.predicateFromClosure(ready) : GroovyJavaMethods.truthPredicate();
        return attributeWhenReady(source, sensor, readyPredicate);
    }
    
    /** returns a {@link Task} which blocks until the given sensor on the given source entity gives a value that satisfies ready, then returns that value;
     * particular useful in Entity configuration where config will block until Tasks have a value
     */
    public static <T> Task<T> attributeWhenReady(final Entity source, final AttributeSensor<T> sensor, final Predicate<? super T> ready) {
        Builder<T, T> builder = builder().attributeWhenReady(source, sensor);
        if (ready != null) builder.readiness(ready);
        return builder.build();

    }

    public static <T,V> Task<V> attributePostProcessedWhenReady(Entity source, AttributeSensor<T> sensor, Closure<Boolean> ready, Closure<V> postProcess) {
        Predicate<? super T> readyPredicate = (ready != null) ? GroovyJavaMethods.predicateFromClosure(ready) : GroovyJavaMethods.truthPredicate();
        Function<? super T, V> postProcessFunction = GroovyJavaMethods.<T,V>functionFromClosure(postProcess);
        return attributePostProcessedWhenReady(source, sensor, readyPredicate, postProcessFunction);
    }

    public static <T,V> Task<V> attributePostProcessedWhenReady(Entity source, AttributeSensor<T> sensor, Closure<V> postProcess) {
        return attributePostProcessedWhenReady(source, sensor, GroovyJavaMethods.truthPredicate(), GroovyJavaMethods.<T,V>functionFromClosure(postProcess));
    }

    public static <T> Task<T> valueWhenAttributeReady(Entity source, AttributeSensor<T> sensor, T value) {
        return DependentConfiguration.<T,T>attributePostProcessedWhenReady(source, sensor, GroovyJavaMethods.truthPredicate(), Functions.constant(value));
    }

    public static <T,V> Task<V> valueWhenAttributeReady(Entity source, AttributeSensor<T> sensor, Function<? super T,V> valueProvider) {
        return attributePostProcessedWhenReady(source, sensor, GroovyJavaMethods.truthPredicate(), valueProvider);
    }
    
    public static <T,V> Task<V> valueWhenAttributeReady(Entity source, AttributeSensor<T> sensor, Closure<V> valueProvider) {
        return attributePostProcessedWhenReady(source, sensor, GroovyJavaMethods.truthPredicate(), valueProvider);
    }
    
    public static <T,V> Task<V> attributePostProcessedWhenReady(final Entity source, final AttributeSensor<T> sensor, final Predicate<? super T> ready, final Closure<V> postProcess) {
        return attributePostProcessedWhenReady(source, sensor, ready, GroovyJavaMethods.<T,V>functionFromClosure(postProcess));
    }
    
    public static <T,V> Task<V> attributePostProcessedWhenReady(final Entity source, final AttributeSensor<T> sensor, final Predicate<? super T> ready, final Function<? super T,V> postProcess) {
        Builder<T, T> builder = builder().attributeWhenReady(source, sensor);
        if (ready != null) builder.readiness(ready);
        if (postProcess != null) builder.postProcess(postProcess);
        return ((Builder)builder).build();
    }

    public static <T> T waitInTaskForAttributeReady(Entity source, AttributeSensor<T> sensor, Predicate<? super T> ready) {
        return waitInTaskForAttributeReady(source, sensor, ready, ImmutableList.<AttributeAndSensorCondition<?>>of());
    }
    
    public static <T> T waitInTaskForAttributeReady(final Entity source, final AttributeSensor<T> sensor, Predicate<? super T> ready, List<AttributeAndSensorCondition<?>> abortConditions) {
        T value = source.getAttribute(sensor);
        final List<Exception> abortion = Lists.newCopyOnWriteArrayList();

        // return immediately if either the ready predicate or the abort conditions hold
        if (ready==null) ready = GroovyJavaMethods.truthPredicate();
        if (ready.apply(value)) return value;
        for (AttributeAndSensorCondition abortCondition : abortConditions) {
            Object abortValue = abortCondition.source.getAttribute(abortCondition.sensor);
            if (abortCondition.predicate.apply(abortValue)) {
                abortion.add(new Exception("Abort due to "+abortCondition.source+" -> "+abortCondition.sensor));
            }
        }
        if (abortion.size() > 0) {
            throw new CompoundRuntimeException("Aborted waiting for ready from "+source+" "+sensor, abortion);
        }

        TaskInternal<?> current = (TaskInternal<?>) Tasks.current();
        if (current == null) throw new IllegalStateException("Should only be invoked in a running task");
        Entity entity = BrooklynTaskTags.getTargetOrContextEntity(current);
        if (entity == null) throw new IllegalStateException("Should only be invoked in a running task with an entity tag; "+
                current+" has no entity tag ("+current.getStatusDetail(false)+")");
        final AtomicReference<T> data = new AtomicReference<T>();
        final Semaphore semaphore = new Semaphore(0); // could use Exchanger
        SubscriptionHandle subscription = null;
        List<SubscriptionHandle> abortSubscriptions = Lists.newArrayList();
        try {
            subscription = ((EntityInternal)entity).getSubscriptionContext().subscribe(source, sensor, new SensorEventListener<T>() {
                @Override public void onEvent(SensorEvent<T> event) {
                    data.set(event.getValue());
                    semaphore.release();
                }});
            for (final AttributeAndSensorCondition abortCondition : abortConditions) {
                abortSubscriptions.add(((EntityInternal)entity).getSubscriptionContext().subscribe(abortCondition.source, abortCondition.sensor, new SensorEventListener<Object>() {
                    @Override public void onEvent(SensorEvent<Object> event) {
                        if (abortCondition.predicate.apply(event.getValue())) {
                            abortion.add(new Exception("Abort due to "+abortCondition.source+" -> "+abortCondition.sensor));
                            semaphore.release();
                        }
                    }}));
            }
            value = source.getAttribute(sensor);
            while (!ready.apply(value)) {
                String prevBlockingDetails = current.setBlockingDetails("Waiting for ready from "+source+" "+sensor+" (subscription)");
                try {
                    semaphore.acquire();
                } finally {
                    current.setBlockingDetails(prevBlockingDetails);
                }
                
                if (abortion.size() > 0) {
                    throw new CompoundRuntimeException("Aborted waiting for ready from "+source+" "+sensor, abortion);
                }
                value = data.get();
            }
            if (LOG.isDebugEnabled()) LOG.debug("Attribute-ready for {} in entity {}", sensor, source);
            return value;
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            if (subscription != null) {
                ((EntityInternal)entity).getSubscriptionContext().unsubscribe(subscription);
            }
            for (SubscriptionHandle handle : abortSubscriptions) {
                ((EntityInternal)entity).getSubscriptionContext().unsubscribe(handle);
            }
        }
    }
    
    /**
     * Returns a {@link Task} which blocks until the given job returns, then returns the value of that job.
     */
    public static <T> Task<T> whenDone(Callable<T> job) {
        return new BasicTask<T>(MutableMap.of("tag", "whenDone", "displayName", "waiting for job"), job);
    }

    /**
     * Returns a {@link Task} which waits for the result of first parameter, then applies the function in the second
     * parameter to it, returning that result.
     *
     * Particular useful in Entity configuration where config will block until Tasks have completed,
     * allowing for example an {@link #attributeWhenReady(Entity, AttributeSensor, Predicate)} expression to be
     * passed in the first argument then transformed by the function in the second argument to generate
     * the value that is used for the configuration
     */
    public static <U,T> Task<T> transform(final Task<U> task, final Function<U,T> transformer) {
        return transform(MutableMap.of("displayName", "transforming "+task), task, transformer);
    }
 
    /** @see #transform(Task, Function) */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <U,T> Task<T> transform(Task<U> task, Closure transformer) {
        return transform(task, GroovyJavaMethods.functionFromClosure(transformer));
    }
    
    /** @see #transform(Task, Function) */
    public static <U,T> Task<T> transform(final Map flags, final TaskAdaptable<U> task, final Function<U,T> transformer) {
        return new BasicTask<T>(flags, new Callable<T>() {
            public T call() throws Exception {
                if (!task.asTask().isSubmitted()) {
                    BasicExecutionContext.getCurrentExecutionContext().submit(task);
                } 
                return transformer.apply(task.asTask().get());
            }});        
    }
     
    /** Returns a task which waits for multiple other tasks (submitting if necessary)
     * and performs arbitrary computation over the List of results.
     * @see #transform(Task, Function) but note argument order is reversed (counterintuitive) to allow for varargs */
    public static <U,T> Task<T> transformMultiple(Function<List<U>,T> transformer, TaskAdaptable<U> ...tasks) {
        return transformMultiple(MutableMap.of("displayName", "transforming multiple"), transformer, tasks);
    }

    /** @see #transformMultiple(Function, TaskAdaptable...) */
    public static <U,T> Task<T> transformMultiple(Closure transformer, TaskAdaptable<U> ...tasks) {
        return transformMultiple(GroovyJavaMethods.functionFromClosure(transformer), tasks);
    }

    /** @see #transformMultiple(Function, TaskAdaptable...) */
    public static <U,T> Task<T> transformMultiple(Map flags, Closure transformer, TaskAdaptable<U> ...tasks) {
        return transformMultiple(flags, GroovyJavaMethods.functionFromClosure(transformer), tasks);
    }
    
    /** @see #transformMultiple(Function, TaskAdaptable...) */
    public static <U,T> Task<T> transformMultiple(Map flags, final Function<List<U>,T> transformer, TaskAdaptable<U> ...tasks) {
        return transformMultiple(flags, transformer, Arrays.asList(tasks));
    }
    public static <U,T> Task<T> transformMultiple(Map flags, final Function<List<U>,T> transformer, List<? extends TaskAdaptable<U>> tasks) {
        if (tasks.size()==1) {
            return transform(flags, tasks.get(0), new Function<U,T>() {
                @Override @Nullable
                public T apply(@Nullable U input) {
                    return transformer.apply(ImmutableList.of(input));
                }
            });
        }
        return transform(flags, new ParallelTask<U>(tasks), transformer);
    }


    /** Method which returns a Future containing a string formatted using String.format,
     * where the arguments can be normal objects or tasks;
     * tasks will be waited on (submitted if necessary) and their results substituted in the call
     * to String.format.
     * <p>
     * Example:
     * <pre>
     * {@code
     *   setConfig(URL, DependentConfiguration.formatString("%s:%s", 
     *           DependentConfiguration.attributeWhenReady(target, Target.HOSTNAME),
     *           DependentConfiguration.attributeWhenReady(target, Target.PORT) ) );
     * }
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public static Task<String> formatString(final String spec, final Object ...args) {
        List<TaskAdaptable<Object>> taskArgs = Lists.newArrayList();
        for (Object arg: args) {
            if (arg instanceof TaskAdaptable) taskArgs.add((TaskAdaptable<Object>)arg);
            else if (arg instanceof TaskFactory) taskArgs.add( ((TaskFactory<TaskAdaptable<Object>>)arg).newTask() );
        }
            
        return transformMultiple(
            MutableMap.<String,String>of("displayName", "formatting '"+spec+"' with "+taskArgs.size()+" task"+(taskArgs.size()!=1?"s":"")), 
                new Function<List<Object>, String>() {
            @Override public String apply(List<Object> input) {
                Iterator<?> tri = input.iterator();
                Object[] vv = new Object[args.length];
                int i=0;
                for (Object arg : args) {
                    if (arg instanceof TaskAdaptable || arg instanceof TaskFactory) vv[i] = tri.next();
                    else if (arg instanceof DeferredSupplier) vv[i] = ((DeferredSupplier<?>) arg).get();
                    else vv[i] = arg;
                    i++;
                }
                return String.format(spec, vv);
            }},
            taskArgs);
    }

    /** returns a task for parallel execution returning a list of values for the given sensor for the given entity list, 
     * optionally when the values satisfy a given readiness predicate (defaulting to groovy truth if not supplied) */
    public static <T> Task<List<T>> listAttributesWhenReady(AttributeSensor<T> sensor, Iterable<Entity> entities) {
        return listAttributesWhenReady(sensor, entities, GroovyJavaMethods.truthPredicate());
    }
    
    public static <T> Task<List<T>> listAttributesWhenReady(AttributeSensor<T> sensor, Iterable<Entity> entities, Closure readiness) {
        Predicate<T> readinessPredicate = (readiness != null) ? GroovyJavaMethods.predicateFromClosure(readiness) : GroovyJavaMethods.truthPredicate();
        return listAttributesWhenReady(sensor, entities, readiness);
    }
    
    /** returns a task for parallel execution returning a list of values of the given sensor list on the given entity, 
     * optionally when the values satisfy a given readiness predicate (defaulting to groovy truth if not supplied) */    
    public static <T> Task<List<T>> listAttributesWhenReady(final AttributeSensor<T> sensor, Iterable<Entity> entities, Predicate<? super T> readiness) {
        if (readiness == null) readiness = GroovyJavaMethods.truthPredicate();
        return builder().attributeWhenReadyFromMultiple(entities, sensor, readiness).build();
    }

    /** @see #waitForTask(Task, Entity, String) */
    public static <T> T waitForTask(Task<T> t, Entity context) throws InterruptedException {
        return waitForTask(t, context, null);
    }
    
    /** blocks until the given task completes, submitting if necessary, returning the result of that task;
     * optional contextMessage is available in status if this is running in a task
     */
    @SuppressWarnings("unchecked")
    public static <T> T waitForTask(Task<T> t, Entity context, String contextMessage) throws InterruptedException {
        try {
            return (T) Tasks.resolveValue(t, Object.class, ((EntityInternal)context).getExecutionContext(), contextMessage);
        } catch (ExecutionException e) {
            throw Throwables.propagate(e);
        }
    }
    
    public static class AttributeAndSensorCondition<T> {
        protected final Entity source;
        protected final AttributeSensor<T> sensor;
        protected final Predicate<? super T> predicate;
        
        public AttributeAndSensorCondition(Entity source, AttributeSensor<T> sensor, Predicate<? super T> predicate) {
            this.source = checkNotNull(source, "source");
            this.sensor = checkNotNull(sensor, "sensor");
            this.predicate = checkNotNull(predicate, "predicate");
        }
    }
    
    public static Builder<?,?> builder() {
        return new Builder<Object,Object>();
    }
    
    /**
     * Builder for producing variants of attributeWhenReady.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Beta
    public static class Builder<T,V> {
        protected Entity source;
        protected AttributeSensor<T> sensor;
        protected Predicate<? super T> readiness;
        protected List<AttributeAndSensorCondition<?>> multiSource = Lists.newArrayList();
        protected Function<? super T, ? extends V> postProcess;
        protected Function<?, ? extends V> postProcessFromMultiple;
        protected List<AttributeAndSensorCondition<?>> abortConditions = Lists.newArrayList();
        
        /**
         * Will wait for the attribute on the given entity.
         * If that entity report {@link Lifecycle#ON_FIRE} for its {@link Attributes#SERVICE_STATE} then it will abort. 
         */
        public <T2> Builder<T2,T2> attributeWhenReady(Entity source, AttributeSensor<T2> sensor) {
            this.source = checkNotNull(source, "source");
            this.sensor = (AttributeSensor) checkNotNull(sensor, "sensor");
            abortIf(source, Attributes.SERVICE_STATE, Predicates.equalTo(Lifecycle.ON_FIRE));
            return (Builder<T2, T2>) this;
        }
        /** returns a task for parallel execution returning a list of values of the given sensor list on the given entity, 
         * optionally when the values satisfy a given readiness predicate (defaulting to groovy truth if not supplied) */ 
        @Beta
        public <T2> Builder<T2, List<T2>> attributeWhenReadyFromMultiple(Iterable<? extends Entity> sources, AttributeSensor<T2> sensor) {
            return attributeWhenReadyFromMultiple(sources, sensor, GroovyJavaMethods.truthPredicate());
        }
        @Beta
        public <T2> Builder<T2, List<T2>> attributeWhenReadyFromMultiple(Iterable<? extends Entity> sources, AttributeSensor<T2> sensor, Predicate<? super T2> readiness) {
            for (Entity s : checkNotNull(sources, "sources")) {
                AttributeAndSensorCondition<T2> condition = new AttributeAndSensorCondition<T2>(s, sensor, readiness);
                multiSource.add(condition);
            }
            return (Builder<T2, List<T2>>) this;
        }
        public Builder<T,V> readiness(Closure<Boolean> val) {
            this.readiness = GroovyJavaMethods.predicateFromClosure(checkNotNull(val, "val"));
            return this;
        }
        public Builder<T,V> readiness(Predicate<? super T> val) {
            this.readiness = checkNotNull(val, "ready");
            return this;
        }
        public <V2> Builder<T,V2> postProcess(Closure<V2> val) {
            this.postProcess = (Function) GroovyJavaMethods.<T,V2>functionFromClosure(checkNotNull(val, "postProcess"));
            return (Builder<T,V2>) this;
        }
        public <V2> Builder<T,V2> postProcess(final Function<? super T, V2>  val) {
            this.postProcess = (Function) checkNotNull(val, "postProcess");
            return (Builder<T,V2>) this;
        }
        public <V2> Builder<T,V2> postProcessFromMultiple(final Function<? super V, V2> val) {
            this.postProcessFromMultiple = (Function) checkNotNull(val, "postProcess");
            return (Builder<T,V2>) this;
        }
        public <T2> Builder<T,V> abortIf(Entity source, AttributeSensor<T2> sensor) {
            return abortIf(source, sensor, GroovyJavaMethods.truthPredicate());
        }
        public <T2> Builder<T,V> abortIf(Entity source, AttributeSensor<T2> sensor, Predicate<? super T2> predicate) {
            abortConditions.add(new AttributeAndSensorCondition<T2>(source, sensor, predicate));
            return this;
        }
        public Task<V> build() {
            checkState(source != null ^ multiSource.size() > 0, "Entity source or sources must be set: source=%s; multiSource=%s", source, multiSource);
            checkState(source == null ? sensor == null : sensor != null, "Sensor must be set if single source is set: source=%s; sensors=%s", source, sensor);
            if (multiSource.size() > 0) {
                checkState(readiness == null, "Cannot set global readiness with multi-source");
                checkState(postProcess == null, "Cannot set global post-process with multi-source");
                checkState(abortConditions.isEmpty(), "Cannot set global abort-conditions with multi-source");
            } else {
                if (readiness == null) readiness = GroovyJavaMethods.truthPredicate();
                if (postProcess == null) postProcess = (Function) Functions.identity();
            }
            
            if (source != null) {
                return new BasicTask<V>(
                        MutableMap.of("tag", "attributeWhenReady", "displayName", "retrieving sensor "+sensor.getName()+" from "+source.getDisplayName()), 
                        new Callable<V>() {
                            @Override public V call() {
                                T result = waitInTaskForAttributeReady(source, sensor, readiness, abortConditions);
                                return postProcess.apply(result);
                            }
                        });
            } else {
                // TODO Do we really want to try to support the list-of-entities?
                final Task<V> task = (Task<V>) new ParallelTask<Object>(Iterables.transform(multiSource, new Function<AttributeAndSensorCondition<?>, Task<T>>() {
                    @Override public Task<T> apply(AttributeAndSensorCondition<?> it) {
                        return (Task) builder().attributeWhenReady(it.source, it.sensor).readiness((Predicate)it.predicate).build();
                    }
                }));
                if (postProcessFromMultiple == null) {
                    return task;
                } else {
                    return new BasicTask(new Callable<V>() {
                        @Override public V call() throws Exception {
                            Object prePostProgress = DynamicTasks.queueIfPossible(task).orSubmitAndBlock().getTask().get();
                            return ((Function<Object,V>)postProcessFromMultiple).apply(prePostProgress);
                        }
                    });
                }
            }
        }
    }
}
