package brooklyn.event.basic;

import groovy.lang.Closure

import java.util.List;
import java.util.concurrent.Callable
import java.util.concurrent.Semaphore

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent
import brooklyn.management.SubscriptionHandle
import brooklyn.management.Task
import brooklyn.util.task.BasicExecutionContext
import brooklyn.util.task.BasicTask
import brooklyn.util.task.ParallelTask

import com.google.common.base.Function
import com.google.common.base.Predicate

/** Conveniences for making tasks which run in entity {@link ExecutionContext}s, subscribing to attributes from other entities, possibly transforming those;
 * these {@link Task} instances are typically passed in {@link AbstractEntity#setConfig(ConfigKey, Object)}.
 * <p>
 * If using a lot it may be useful to:
 * <code>
 * import static brooklyn.event.basic.DependentConfiguration.*;
 * </code>
 */
public class DependentConfiguration {

    protected static final Logger LOG = LoggerFactory.getLogger(DependentConfiguration.class)
    
    //not instantiable, only a static helper
    private DependentConfiguration() {}

    /** @see #attributeWhenReady(Entity, AttributeSensor, Predicate); default readiness (if no third argument) is Groovy truth */    
    public static <T> Task<T> attributeWhenReady(Entity source, AttributeSensor<T> sensor, Closure ready = { it }) {
        attributeWhenReady(source, sensor, new Predicate() { public boolean apply(Object o) { (ready?:{it}).call(o) } })
    }
    
    /** returns a {@link Task} which blocks until the given sensor on the given source entity gives a value that satisfies ready, then returns that value;
     * particular useful in Entity configuration where config will block until Tasks have a value
     */
    public static <T> Task<T> attributeWhenReady(Entity source, AttributeSensor<T> sensor, Predicate ready) {
        new BasicTask<T>(tag:"attributeWhenReady", displayName:"retrieving $source $sensor", { waitInTaskForAttributeReady(source, sensor, ready); } )    
    }

    public static <T> Task<T> attributePostProcessedWhenReady(Entity source, AttributeSensor<T> sensor, Closure ready, Closure postProcess) {
        attributePostProcessedWhenReady(source, sensor, new Predicate() { public boolean apply(Object o) { (ready?:{it}).call(o) } }, postProcess)
    }

    public static <T> Task<T> attributePostProcessedWhenReady(Entity source, AttributeSensor<T> sensor, Closure postProcess) {
        attributePostProcessedWhenReady(source, sensor, { it }, postProcess)
    }

    public static <T> Task<T> valueWhenAttributeReady(Entity source, AttributeSensor<T> sensor, Object value) {
        attributePostProcessedWhenReady(source, sensor, { it }, { value })
    }

    public static <T> Task<T> valueWhenAttributeReady(Entity source, AttributeSensor<T> sensor, Function valueProvider) {
        attributePostProcessedWhenReady(source, sensor, { it }, valueProvider)
    }
    
    public static <T> Task<T> valueWhenAttributeReady(Entity source, AttributeSensor<T> sensor, Closure valueProvider) {
        attributePostProcessedWhenReady(source, sensor, { it }, valueProvider)
    }
    
    public static <T> Task<T> attributePostProcessedWhenReady(Entity source, AttributeSensor<T> sensor, Predicate ready, Closure postProcess) {
        new BasicTask<T>(tag:"attributePostProcessedWhenReady", displayName:"retrieving $source $sensor", 
                { def result = waitInTaskForAttributeReady(source, sensor, ready); return postProcess.call(result) } )
    }

    private static <T> T waitInTaskForAttributeReady(Entity source, AttributeSensor<T> sensor, Predicate ready) {
        T value = source.getAttribute(sensor);
        if (ready==null) ready = new Predicate() { public boolean apply(Object o) { o } };
        
        if (ready.apply(value)) return value
        BasicTask current = BasicExecutionContext.currentExecutionContext.currentTask
        if (!current) throw new IllegalStateException("Should only be invoked in a running task")
        AbstractEntity entity = current.tags.find { it in AbstractEntity }
        if (!entity) throw new IllegalStateException("Should only be invoked in a running task with an entity tag; ${current} has no entity tag ("+current.getStatusDetail(false)+")");
        T data
        Semaphore semaphore = new Semaphore(0) // could use Exchanger
        SubscriptionHandle subscription
        try {
            subscription = entity.subscriptionContext.subscribe source, sensor, { SensorEvent event ->
	                data = event.value
	                semaphore.release()
	            }
            value = source.getAttribute(sensor)
            while (!ready.apply(value)) {
                current.setBlockingDetails("Waiting for notification from subscription on $source $sensor")
                semaphore.acquire()
                current.setBlockingDetails(null)
                value = data
            }
            if (LOG.isDebugEnabled()) LOG.debug("Attribute-ready for $sensor in entity $source")
            return value
        } finally {
            entity.subscriptionContext.unsubscribe(subscription)
        }
    }
    
    /**
     * Returns a {@link Task} which blocks until the given job returns, then returns the value of that job.
     */
    public static <T> Task<T> whenDone(Callable<T> job) {
        return new BasicTask<T>([tag:"whenDone", displayName:"waiting for job"], job)
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
    public static <U,T> Task<T> transform(Task<U> task, Function<U,T> transformer) {
        new BasicTask<T>( {
            if (!task.isSubmitted()) {
                BasicExecutionContext.getCurrentExecutionContext().submit(task);
            } 
            transformer.apply(task.get())
        } );
    }
 
    /** @see #transform(Task, Function) */
    public static <U,T> Task<T> transform(Task<U> task, Closure transformer) {
        transform(task, transformer as Function)
    }
    
    /** Returns a task which waits for multiple other tasks (submitting if necessary)
     * and performs arbitrary computation over the List of results.
     * @see #transform(Task, Function) but note argument order is reversed (counterintuitive) to allow for varargs */
    public static <U,T> Task<T> transformMultiple(Function<List<U>,T> transformer, Task<U> ...tasks) {
        transform(new ParallelTask(tasks), transformer);
    }

    /** Returns a task which waits for multiple other tasks (submitting if necessary)
     * and performs arbitrary computation over the List of results.
     * @see #transform(Task, Function) but note argument order is reversed (counterintuitive) to allow for varargs */
    public static <U,T> Task<T> transformMultiple(Closure transformer, Task<U> ...tasks) {
        transform(new ParallelTask(tasks), transformer);
    }

    /** Method which returns a Future containing a string formatted using String.format,
     * where the arguments can be normal objects or tasks;
     * tasks will be waited on (submitted if necessary) and their results substituted in the call
     * to String.format.
     * <p>
     * Example:
     * <code>
        setConfig(URL, DependentConfiguration.formatString("%s:%s", 
                        DependentConfiguration.attributeWhenReady(target, Target.HOSTNAME),
                        DependentConfiguration.attributeWhenReady(target, Target.PORT) ) );
     * </code>
     */
    public static Task<String> formatString(String spec, Object ...args) {
        List taskArgs = []
        for (Object arg: args)
            if (arg in Task) taskArgs << arg;
            
        transformMultiple({ List<Object> taskResults ->
                Iterator<Object> tri = taskResults.iterator();
                Object[] vv = new Object[args.length];
                int i=0;
                for (Object arg: args) {
                    if (arg in Task) vv[i] = tri.next();
                    else vv[i] = arg;
                    i++;
                }
                String.format(spec, vv);
            }, taskArgs.toArray(new Task[taskArgs.size()]));
    }
    
    public static <T> Task<List<T>> listAttributesWhenReady(Sensor<T> sensor, Iterable<Entity> entities, Closure readiness = null) {
        listAttributesWhenReady(sensor, entities, new Predicate() { public boolean apply(Object o) { (readiness?:{it}).call(o) } });
    }
    public static <T> Task<List<T>> listAttributesWhenReady(Sensor<T> sensor, Iterable<Entity> entities, Predicate<T> readiness) {
        new ParallelTask(entities.collect({ attributeWhenReady(it, sensor, readiness) }) );
    }

}
