package brooklyn.event.basic;

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
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ExecutionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.Task;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ParallelTask;
import brooklyn.util.task.TaskInternal;
import brooklyn.util.task.Tasks;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
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

    protected static final Logger LOG = LoggerFactory.getLogger(DependentConfiguration.class);
    
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
        return new BasicTask<T>(
                MutableMap.of("tag", "attributeWhenReady", "displayName", "retrieving sensor "+sensor.getName()+" from "+source.getDisplayName()), 
                new Callable<T>() {
                    public T call() {
                        return waitInTaskForAttributeReady(source, sensor, ready);
                    }
                });
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

    public static <T> Task<T> valueWhenAttributeReady(Entity source, AttributeSensor<T> sensor, Function<? super T,T> valueProvider) {
        return attributePostProcessedWhenReady(source, sensor, GroovyJavaMethods.truthPredicate(), valueProvider);
    }
    
    public static <T> Task<T> valueWhenAttributeReady(Entity source, AttributeSensor<T> sensor, Closure<T> valueProvider) {
        return attributePostProcessedWhenReady(source, sensor, GroovyJavaMethods.truthPredicate(), valueProvider);
    }
    
    public static <T,V> Task<V> attributePostProcessedWhenReady(final Entity source, final AttributeSensor<T> sensor, final Predicate<? super T> ready, final Closure<V> postProcess) {
        return attributePostProcessedWhenReady(source, sensor, ready, GroovyJavaMethods.<T,V>functionFromClosure(postProcess));
    }

    public static <T,V> Task<V> attributePostProcessedWhenReady(final Entity source, final AttributeSensor<T> sensor, final Predicate<? super T> ready, final Function<? super T,V> postProcess) {
        return new BasicTask<V>(
                MutableMap.of("tag", "attributePostProcessedWhenReady", "displayName", "retrieving "+source+" "+sensor), 
                new Callable<V>() {
                    @Override public V call() {
                        T result = waitInTaskForAttributeReady(source, sensor, ready);
                        return postProcess.apply(result);
                    }
                });
    }

    public static <T> T waitInTaskForAttributeReady(Entity source, AttributeSensor<T> sensor, Predicate<? super T> ready) {
        T value = source.getAttribute(sensor);
        if (ready==null) ready = GroovyJavaMethods.truthPredicate();
        if (ready.apply(value)) return value;
        TaskInternal<?> current = (TaskInternal<?>) Tasks.current();
        if (current == null) throw new IllegalStateException("Should only be invoked in a running task");
        Entity entity = BrooklynTasks.getTargetOrContextEntity(current);
        if (entity == null) throw new IllegalStateException("Should only be invoked in a running task with an entity tag; "+
                current+" has no entity tag ("+current.getStatusDetail(false)+")");
        final AtomicReference<T> data = new AtomicReference<T>();
        final Semaphore semaphore = new Semaphore(0); // could use Exchanger
        SubscriptionHandle subscription = null;
        try {
            subscription = ((EntityInternal)entity).getSubscriptionContext().subscribe(source, sensor, new SensorEventListener<T>() {
                @Override public void onEvent(SensorEvent<T> event) {
                    data.set(event.getValue());
                    semaphore.release();
                }});
            value = source.getAttribute(sensor);
            while (!ready.apply(value)) {
                current.setBlockingDetails("Waiting for ready from "+source+" "+sensor+" (subscription)");
                try {
                    semaphore.acquire();
                } finally {
                    current.resetBlockingDetails();
                }
                value = data.get();
            }
            if (LOG.isDebugEnabled()) LOG.debug("Attribute-ready for {} in entity {}", sensor, source);
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        } finally {
            if (subscription != null) {
                ((EntityInternal)entity).getSubscriptionContext().unsubscribe(subscription);
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
    public static <U,T> Task<T> transform(final Map flags, final Task<U> task, final Function<U,T> transformer) {
        return new BasicTask<T>(flags, new Callable<T>() {
            public T call() throws Exception {
                if (!task.isSubmitted()) {
                    BasicExecutionContext.getCurrentExecutionContext().submit(task);
                } 
                return transformer.apply(task.get());
            }});        
    }
     
    /** Returns a task which waits for multiple other tasks (submitting if necessary)
     * and performs arbitrary computation over the List of results.
     * @see #transform(Task, Function) but note argument order is reversed (counterintuitive) to allow for varargs */
    public static <U,T> Task<T> transformMultiple(Function<List<U>,T> transformer, Task<U> ...tasks) {
        return transformMultiple(MutableMap.of("displayName", "transforming multiple"), transformer, tasks);
    }

    /** @see #transformMultiple(Function, Task...) */
    public static <U,T> Task<T> transformMultiple(Closure transformer, Task<U> ...tasks) {
        return transformMultiple(GroovyJavaMethods.functionFromClosure(transformer), tasks);
    }

    /** @see #transformMultiple(Function, Task...) */
    public static <U,T> Task<T> transformMultiple(Map flags, Closure transformer, Task<U> ...tasks) {
        return transformMultiple(flags, GroovyJavaMethods.functionFromClosure(transformer), tasks);
    }
    
    /** @see #transformMultiple(Function, Task...) */
    public static <U,T> Task<T> transformMultiple(Map flags, final Function<List<U>,T> transformer, Task<U> ...tasks) {
        return transformMultiple(flags, transformer, Arrays.asList(tasks));
    }
    public static <U,T> Task<T> transformMultiple(Map flags, final Function<List<U>,T> transformer, List<Task<U>> tasks) {
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
        List<Task<Object>> taskArgs = Lists.newArrayList();
        for (Object arg: args) {
            if (arg instanceof Task) taskArgs.add((Task<Object>)arg);
        }
            
        return transformMultiple(
            MutableMap.<String,String>of("displayName", "formatting '"+spec+"' with "+taskArgs.size()+" task"+(taskArgs.size()!=1?"s":"")), 
                new Function<List<Object>, String>() {
            @Override public String apply(List<Object> input) {
                Iterator<?> tri = input.iterator();
                Object[] vv = new Object[args.length];
                int i=0;
                for (Object arg : args) {
                    if (arg instanceof Task) vv[i] = tri.next();
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
    public static <T> Task<List<T>> listAttributesWhenReady(final AttributeSensor<T> sensor, Iterable<Entity> entities, final Predicate<? super T> readiness) {
        return new ParallelTask<T>(Iterables.transform(entities, new Function<Entity, Task<T>>() {
            @Override public Task<T> apply(Entity it) {
                return attributeWhenReady(it, sensor, readiness);
            }
        }));
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
    
}
