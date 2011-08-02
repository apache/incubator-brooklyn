package brooklyn.event.basic;

import groovy.lang.Closure

import java.util.concurrent.Callable

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.AttributeSensor
import brooklyn.management.SubscriptionHandle
import brooklyn.management.Task
import brooklyn.util.task.BasicExecutionContext
import brooklyn.util.task.BasicTask

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

    //not instantiable, only a static helper
    private DependentConfiguration() {}

    /** @see #attributeWhenReady(Entity, AttributeSensor, Predicate); default readiness (if no third argument) is Groovy truth */    
    public static <T> Task<T> attributeWhenReady(Entity source, AttributeSensor<T> sensor, Closure ready = { it }) {
        attributeWhenReady(source, sensor, new Predicate() { public boolean apply(Object o) { ready.call(o) } })
    }
    
    /** returns a {@link Task} which blocks until the given sensor on the given source entity gives a value that satisfies ready, then returns that value;
     * particular useful in Entity configuration where config will block until Tasks have a value
     */
    public static <T> Task<T> attributeWhenReady(Entity source, AttributeSensor<T> sensor, Predicate ready) {
        new BasicTask<T>(tag:"attributeWhenReady", displayName:"retrieving $source $sensor", { waitInTaskForAttributeReady(source, sensor, ready); } )    
    }

    public static <T> Task<T> attributePostProcessedWhenReady(Entity source, AttributeSensor<T> sensor, Closure ready = { it }, Closure postProcess = { it }) {
        attributePostProcessedWhenReady(source, sensor, new Predicate() { public boolean apply(Object o) { ready.call(o) } }, postProcess)
    }
    
    public static <T> Task<T> attributePostProcessedWhenReady(Entity source, AttributeSensor<T> sensor, Predicate ready, Closure postProcess = { it }) {
        new BasicTask<T>(tag:"attributePostProcessedWhenReady", displayName:"retrieving $source $sensor", 
                { def result = waitInTaskForAttributeReady(source, sensor, ready); return postProcess.call(result) } )
    }

    private static <T> T waitInTaskForAttributeReady(Entity source, AttributeSensor<T> sensor, Predicate ready) {
        T v = ((AbstractEntity)source).getAttribute(sensor);
        if (ready.apply(v)) 
            return v
        BasicTask t = BasicExecutionContext.getCurrentExecutionContext().getCurrentTask();
        if (t==null) throw new IllegalStateException("should only be invoked in a running task");
//        println "waiting in $t with tags "+t.getTags()
        AbstractEntity e = t.getTags().find { it in Entity }
        if (e==null) throw new IllegalStateException("should only be invoked in a running task with an entity tag; $t has no entity tag ("+t.getStatusDetail(false)+")");
        T[] data = new T[1]
        SubscriptionHandle sub
        try {
            synchronized (data) {
                sub = e.getSubscriptionContext().subscribe(source, sensor, {
                    synchronized (data) {
                        data[0] = it.value
                        data.notifyAll()
                    }
                });
                v = source.getAttribute(sensor)
                while (!ready.apply(v)) {
                    t.setBlockingDetails("waiting for notification from subscription on $source $sensor")
                    data.wait()
                    v = data[0]
                }
                return v
            }
        } finally {
            e.getSubscriptionContext().unsubscribe(sub)
        }
    }
    
    /** returns a {@link Task} which blocks until the given job returns, then returns the value of that job.
     */
    public static <T> Task<T> whenDone(Callable<T> job) {
        return new BasicTask<T>([tag:"whenDone", displayName:"waiting for job"], job)
    }

    /** returns a {@link Task} which waits for the result of first parameter, then applies the function in the second parameter to it, returning that result;
     * particular useful in Entity configuration where config will block until Tasks have completed,
     * allowing for example an {@link #attributeWhenReady} expression to be passed in the first argument
     * then transformed by the function in the second argument to generate the value that is used for the configuration
     */
    public static <U,T> Task<T> transform(Task<U> f, Function<U,T> g) {
        new BasicTask<T>( {
            if (!f.isSubmitted()) {
                BasicExecutionContext.getCurrentExecutionContext().submit(f);
            } 
            g.apply(f.get())
        } );
    }
 
    /** @see #transform(Task, Function) */
    public static <U,T> Task<T> transform(Task<U> f, Closure g) {
        transform(f, g as Function)
    }
}
