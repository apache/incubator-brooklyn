package brooklyn.management.internal.task

import groovy.lang.Closure
import groovy.time.TimeDuration

import java.util.Collection
import java.util.List
import java.util.Map
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


public class Futures {
    private Futures() {}
    
    /** runs the given closures simultaneously; can optionally be give a timeout (in TimeDuration, after which jobs are cancelled) and/or an ExecutorService executor */
    //TODO rather than cancel after timeout (the default), we could simply apply the timeout to waitFor
    static List<Future<?>> run(Map m=[:], Closure<?> ...c) {
        List<Future> result = start(m, c)
        waitFor(result);
        result
    }
    static List<Future<?>> start(Map m=[:], Closure<?> ...c) {
        Map m2 = new LinkedHashMap(m);
        DelegatingExecutor ex = new DelegatingExecutor(m2);
        List<Future> result = ex.executeAll(c)
        ex.shutdown()
        result
    }
    
    static List<Future<?>> waitFor(Collection<Future<?>> futures) {
        def problems = []
        futures.each { try { it.get() } catch (Exception e) { if (!(e in TimeoutException) && !(e in CancellationException)) problems += e } }
        if (problems) {
            throw problems.size()==1 ? new ExecutionException((String)problems[0].message, problems[0]) :
                new ExecutionException(""+problems.size()+" errors, including: "+problems[0], problems[0])
        }
        futures
    }
    
    /**
     * Returns a {@link Future} containing the value when it is truthy (ie non-null, non-zero, non-empty).
     */
    static <T> QualifiableFuture<T> futureValue(Closure<?> value) {
        futureValueWhen value
    }
    
    /**
     * Returns a {@link Future} containing the value when isValueReady evaluates to true.
     */
    static <T> Future<T> futureValueWhen(Closure<T> value, Closure<Boolean> isValueReady = { it }) {
        new FutureValue<T>(value, isValueReady);
    }
    
    /**
     * Returns the value when isValueReady evaluates to true.
     */
    static <T> T when(Closure<T> value, Closure<Boolean> isValueReady = { it }) {
        Future<T> future = futureValueWhen value, isValueReady
        try {
            return future.get()
        } catch (ExecutionException e) {
            throw e.cause
        }
    }
    
    /** returns the given value; when the item is a future, it waits for the future to be done */
    static Object getBlocking(Map params=[:], Object v) {
        if (v in Future) {
            try {
                TimeDuration t = params["timeout"]
                if (t in TimeDuration)
                    return v.get(params["timeout"].toMilliseconds(), TimeUnit.MILLISECONDS)
                else
                    return v.get()
            } catch (Exception e) {
                if ((e in TimeoutException) && params["defaultValue"]) return params["defaultValue"] 
                throw e;
            }
        }
        return v
    }
}

