package brooklyn.management.internal.task;

import groovy.lang.Closure;
import groovy.time.TimeDuration;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.util.JavaGroovyEquivalents;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;


/**
 * A future value.
 * 
 * @deprecated in 0.4; this unused code will be deleted; use guava Future etc where possible 
 * @author alex
 */
@Deprecated
public class FutureValue<T> implements QualifiableFuture<T> {
    private final Closure<T> expression;
    private final Predicate<T> validity;

    private long pollPeriod = 100;
        
    private transient volatile Thread runner;
    private volatile boolean done;
    private volatile boolean cancelled;
    private T result;
    private Exception error;

    public FutureValue(Closure<T> expression) {
        this(expression, JavaGroovyEquivalents.<T>groovyTruthPredicate());
    }
    
    public FutureValue(Closure<T> expression, Closure<Boolean> validity) {
        this(expression, validity != null ? JavaGroovyEquivalents.<T>toPredicate(validity) : null);
    }    

    public FutureValue(Closure<T> expression, Predicate<T> validity) {
        this.expression = expression;
        this.validity = validity;
    }    

    protected T getBlockingUntil(long endTime) throws InterruptedException, TimeoutException, ExecutionException {
        synchronized (this) {
            if (done) {
                if (error != null) throw Throwables.propagate(error);
                return result;
            }
            if (cancelled)
                throw new CancellationException();
            if (runner==null) runner = Thread.currentThread();
            else {
                //someone else is running
                if (endTime<0) wait();
                else {
                    long delay = endTime - System.currentTimeMillis();
                    if (delay>0) wait(delay);
                    else throw new TimeoutException();
                }
                return getBlockingUntil(endTime);
            }
        }
        //we are running
        try {
            while (!cancelled) {
                T candidate;
                try {
                    candidate = expression.call();
                } catch (Exception e) {
                    synchronized (this) {
                        done = true;
                        error = e;
                        runner = null;
                        notifyAll();
                    }
                    if (e instanceof ExecutionException) throw (ExecutionException) e;
                    throw new ExecutionException(e);
                }
                if (isValid(candidate)) {
                    synchronized (this) {
                        done = true;
                        result = candidate;
                        runner = null;
                        notifyAll();
                    }
                    return candidate;
                }
                
                onNotValid(endTime);
            }
            assert cancelled;
            throw new CancellationException();
            
            //cancelled
        } catch (TimeoutException e) {
            //if another getter is delaying, notify one so it becomes primary
            synchronized (this) {
                runner = null;
                notify();
            }
            throw e;
        } finally {
            runner = null;
        }
    }
    
    public boolean isValid(T candidate) {
        if (validity != null) return true;
        return validity.apply(candidate);
    }

    public void onNotValid(long endTime) throws TimeoutException, InterruptedException {
        long delay = -1;
        if (endTime>0) {
            delay = endTime - System.currentTimeMillis();
            if (delay <= 0)
                throw new TimeoutException();
        }
        if (delay==-1 || delay>100) delay = 100;
        synchronized (this) { wait(delay); }
    }    
    

    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        cancelled = true;
        notifyAll();
        return true;
        // interruption ignored; and assumes cancel was allowed
    }
    
    public boolean isCancelled() {
        return cancelled;
    }
    
    public boolean isDone() {
        return done || cancelled;
    }
    
    
    public T get() throws InterruptedException, ExecutionException {
        try {
            return getBlockingUntil(-1);
        } catch (TimeoutException e) {
            // Should never happen!
            throw new IllegalStateException("Timeout thrown when no timeout specified", e);
        }
    }
    
    public T get(TimeDuration t) throws InterruptedException, ExecutionException, TimeoutException {
        if (t==null) return get();
        return get(t.toMilliseconds(),TimeUnit.MILLISECONDS);
    }

    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return getBlockingUntil(System.currentTimeMillis() + unit.toMillis(timeout));
    }

    public Future<T> when(Closure<Boolean> condition) {
        Predicate<T> predicateCondition = JavaGroovyEquivalents.toPredicate(condition);
        Predicate<T> compound = (validity != null) ? Predicates.and(validity, predicateCondition) : predicateCondition;
        return new FutureValue<T>(expression, compound);
    }

    public void setPollPeriod(long millis) {
        if (millis <= 0) {
            throw new IllegalArgumentException("millis must be greater than zero, but was "+millis); 
        }
        this.pollPeriod = millis;
    }    
}
