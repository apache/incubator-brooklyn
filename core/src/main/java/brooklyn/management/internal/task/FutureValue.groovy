package brooklyn.management.internal.task

import groovy.time.TimeDuration

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import brooklyn.util.internal.TimeExtras

/**
 * A future value.
 * 
 * @author alex
 */
public class FutureValue<T> implements QualifiableFuture<T> {
    private final Closure expression;
    private final Closure validity;

    private long pollPeriod = 100;
        
    private transient volatile Thread runner;
    private volatile boolean done;
    private volatile boolean cancelled;
    private T result;
    private Exception error;

    public FutureValue(Closure expression, Closure validity = { it }) {
        this.expression = expression;
        this.validity = validity;
    }    

    static { TimeExtras.init() }

    protected T getBlockingUntil(long endTime) {
        synchronized (this) {
            if (done) {
                if (error) throw error;
                return result;
            }
            if (cancelled)
                throw new CancellationException();
            if (runner==null) runner = Thread.currentThread();
            else {
                //someone else is running
                if (endTime<0) wait()
                else {
                    long delay = endTime - System.currentTimeMillis()
                    if (delay>0) wait(delay)
                    else throw new TimeoutException()
                }
                return getBlockingUntil(endTime)
            }
        }
        //we are running
        try {
            while (!cancelled) {
                T candidate;
                try {
                    candidate = expression.call()
                } catch (Exception e) {
                    synchronized (this) {
                        done = true
                        error = e
                        runner = null
                        notifyAll()                        
                    }
                    if (e in ExecutionException) throw e
                    throw new ExecutionException(e)
                }
                if (isValid(candidate)) {
                    synchronized (this) {
                        done = true
                        result = candidate
                        runner = null
                        notifyAll()
                    }
                    return candidate
                }
                
                onNotValid(endTime)
            }
            //cancelled
        } catch (TimeoutException e) {
            //if another getter is delaying, notify one so it becomes primary
            synchronized (this) {
                runner = null
                notify()
            }
            throw e
        } finally {
            runner = null
        }
    }
    
    public boolean isValid(T candidate) {
        if (!validity) return true
        return validity.call(candidate)
    }

    public void onNotValid(long endTime) {
        long delay = -1;
        if (endTime>0) {
            delay = endTime - System.currentTimeMillis();
            if (delay <= 0)
                throw new TimeoutException();
        }
        if (delay==-1 || delay>100) delay = 100;
        synchronized (this) { wait(delay) }
    }    
    

    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        cancelled = true;
        notifyAll()
        // interruption ignored
    }
    
    public boolean isCancelled() {
        return cancelled
    }
    
    public boolean isDone() {
        return done || cancelled
    }
    
    
    public T get() throws InterruptedException, ExecutionException {
        getBlockingUntil(-1)
    }
    
    public T get(TimeDuration t) {
        if (t==null) return getBlockingUntil -1
        return getBlockingUntil (System.currentTimeMillis() + t.toMilliseconds())
    }

    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
        return get(timeout*unit)
    }

    public Future<T> when(Closure condition) {
        return new FutureValue(expression, { isValid(it) && condition.call(it) })
    }

    public void setPollPeriod(long millis) {
        assert millis>0
        this.pollPeriod = millis;
    }    
}
