package brooklyn.management.internal.task;

import groovy.lang.Closure;
import groovy.time.TimeDuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.util.JavaGroovyEquivalents;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;


public class Futures {
    private Futures() {}
    
    /** runs the given closures simultaneously; can optionally be give a timeout (in TimeDuration, after which jobs are cancelled) and/or an ExecutorService executor */
    //TODO rather than cancel after timeout (the default), we could simply apply the timeout to waitFor
    static List<Future<?>> run(Closure<?> ...c) throws ExecutionException, InterruptedException {
        return run(Collections.emptyMap(), c);
    }
    
    static List<Future<?>> run(Map m, Closure<?> ...c) throws ExecutionException, InterruptedException {
        List<Future<?>> result = start(m, c);
        waitFor(result);
        return result;
    }
    
    static List<Future<?>> start(Closure<?> ...c) throws InterruptedException {
        return start(Collections.emptyMap(), c);
    }
    
    static List<Future<?>> start(Map m, Closure<?> ...c) throws InterruptedException {
        Map m2 = new LinkedHashMap(m);
        DelegatingExecutor ex = new DelegatingExecutor(m2);
        List<Future<?>> result = ex.executeAll(c);
        ex.shutdown();
        return result;
    }

    static List<Future<?>> waitFor(Collection<Future<?>> futures) throws ExecutionException {
        if (futures instanceof List) {
            return waitFor((List<Future<?>>) futures);
        } else {
            return waitFor(ImmutableList.copyOf(futures));
        }
    }
    
    static List<Future<?>> waitFor(List<Future<?>> futures) throws ExecutionException {
        List<Exception> problems = new ArrayList<Exception>();
        for (Future<?> it : futures) {
            try { it.get(); } catch (Exception e) { if (!(e instanceof TimeoutException) && !(e instanceof CancellationException)) problems.add(e); } }
        if (problems.size() > 0) {
            throw problems.size()==1 ? new ExecutionException((String)problems.get(0).getMessage(), problems.get(0)) :
                new ExecutionException(""+problems.size()+" errors, including: "+problems.get(0), problems.get(0));
        }
        return futures;
    }
    
    /**
     * Returns a {@link Future} containing the value when it is truthy (ie non-null, non-zero, non-empty).
     */
    static <T> QualifiableFuture<T> futureValue(Closure<T> value) {
        return (QualifiableFuture<T>) futureValueWhen(value);
    }
    
    /**
     * Returns a {@link Future} containing the value when isValueReady evaluates to true.
     */
    static <T> Future<T> futureValueWhen(Closure<T> c) {
        return futureValueWhen(c, JavaGroovyEquivalents.<T>groovyTruthPredicate());
    }
    static <T> Future<T> futureValueWhen(Closure<T> value, Closure<Boolean> isValueReady) {
        return new FutureValue<T>(value, isValueReady);
    }
    static <T> Future<T> futureValueWhen(Closure<T> value, Predicate<T> isValueReady) {
        return new FutureValue<T>(value, isValueReady);
    }
    
    /**
     * Returns the value when isValueReady evaluates to true.
     */
    static <T> T when(Closure<T> c) throws Throwable {
        return when(c, JavaGroovyEquivalents.<T>groovyTruthPredicate());
    }
    static <T> T when(Closure<T> value, Closure<Boolean> isValueReady) throws Throwable {
        Future<T> future = futureValueWhen(value, isValueReady);
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
    static <T> T when(Closure<T> value, Predicate<T> isValueReady) throws Throwable {
        Future<T> future = futureValueWhen(value, isValueReady);
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
    
    /** returns the given value; when the item is a future, it waits for the future to be done */
    static Object getBlocking(Object v) throws Exception {
        return getBlocking(Collections.emptyMap(), v);
    }
    static Object getBlocking(Map params, Object v) throws Exception {
        if (v instanceof Future) {
            try {
                Object t = params.get("timeout");
                if (t instanceof TimeDuration)
                    return ((Future)v).get(((TimeDuration)t).toMilliseconds(), TimeUnit.MILLISECONDS);
                else
                    return ((Future)v).get();
            } catch (Exception e) {
                if ((e instanceof TimeoutException) && params.containsKey("defaultValue")) return params.get("defaultValue");
                throw e;
            }
        }
        return v;
    }
}

