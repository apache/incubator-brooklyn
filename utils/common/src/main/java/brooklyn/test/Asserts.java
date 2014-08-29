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
package brooklyn.test;

import groovy.lang.Closure;

import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * TODO should move this to new package brooklyn.util.assertions
 * and TODO should add a repeating() method which returns an AssertingRepeater extending Repeater
 * and:
 * <li> adds support for requireAllIterationsTrue
 * <li> convenience run methods equivalent to succeedsEventually and succeedsContinually
 */
@Beta
public class Asserts {

    /**
     * The default timeout for assertions. Alter in individual tests by giving a
     * "timeout" entry in method flags.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.THIRTY_SECONDS;

    private static final Logger log = LoggerFactory.getLogger(Asserts.class);

    private Asserts() {}
    
    // --- selected routines from testng.Assert for visibility without needing that package
    
    /**
     * Asserts that a condition is true. If it isn't,
     * an AssertionError, with the given message, is thrown.
     * @param condition the condition to evaluate
     * @param message the assertion error message
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) fail(message);
    }

    /**
     * Asserts that a condition is false. If it isn't,
     * an AssertionError, with the given message, is thrown.
     * @param condition the condition to evaluate
     * @param message the assertion error message
     */
    public static void assertFalse(boolean condition, String message) {
        if (condition) fail(message);
    }

    /**
     * Fails a test with the given message.
     * @param message the assertion error message
     */
    public static AssertionError fail(String message) {
        throw new AssertionError(message);
    }

    public static void assertEqualsIgnoringOrder(Iterable<?> actual, Iterable<?> expected) {
        assertEqualsIgnoringOrder(actual, expected, false, null);
    }

    public static void assertEqualsIgnoringOrder(Iterable<?> actual, Iterable<?> expected, boolean logDuplicates, String errmsg) {
        Set<?> actualSet = Sets.newLinkedHashSet(actual);
        Set<?> expectedSet = Sets.newLinkedHashSet(expected);
        Set<?> extras = Sets.difference(actualSet, expectedSet);
        Set<?> missing = Sets.difference(expectedSet, actualSet);
        List<Object> duplicates = Lists.newArrayList(actual);
        for (Object a : actualSet) {
            duplicates.remove(a);
        }
        String fullErrmsg = "extras="+extras+"; missing="+missing
                + (logDuplicates ? "; duplicates="+MutableSet.copyOf(duplicates) : "")
                +"; actualSize="+Iterables.size(actual)+"; expectedSize="+Iterables.size(expected)
                +"; actual="+actual+"; expected="+expected+"; "+errmsg;
        assertTrue(extras.isEmpty(), fullErrmsg);
        assertTrue(missing.isEmpty(), fullErrmsg);
        assertTrue(Iterables.size(actual) == Iterables.size(expected), fullErrmsg);
        assertTrue(actualSet.equals(expectedSet), fullErrmsg); // should be covered by extras/missing/size test
    }

    // --- new routines
    
    public static <T> void eventually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        eventually(ImmutableMap.<String,Object>of(), supplier, predicate);
    }
    
    public static <T> void eventually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate) {
        eventually(flags, supplier, predicate, (String)null);
    }
    
    public static <T> void eventually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        Duration timeout = toDuration(flags.get("timeout"), Duration.ONE_SECOND);
        Duration period = toDuration(flags.get("period"), Duration.millis(10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+timeout.toMilliseconds();
        
        boolean first = true;
        T supplied = supplier.get();
        while (first || System.currentTimeMillis() <= expireTime) {
            supplied = supplier.get();
            if (predicate.apply(supplied)) {
                return;
            }
            first = false;
            if (periodMs > 0) sleep(periodMs);
        }
        fail("supplied="+supplied+"; predicate="+predicate+(errMsg!=null?"; "+errMsg:""));
    }
    
    // TODO improve here -- these methods aren't very useful without timeouts
    public static <T> void continually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        continually(ImmutableMap.<String,Object>of(), supplier, predicate);
    }

    public static <T> void continually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<? super T> predicate) {
        continually(flags, supplier, predicate, (String)null);
    }

    public static <T> void continually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        Duration duration = toDuration(flags.get("timeout"), Duration.ONE_SECOND);
        Duration period = toDuration(flags.get("period"), Duration.millis(10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+duration.toMilliseconds();
        
        boolean first = true;
        while (first || System.currentTimeMillis() <= expireTime) {
            assertTrue(predicate.apply(supplier.get()), "supplied="+supplier.get()+"; predicate="+predicate+(errMsg!=null?"; "+errMsg:""));
            if (periodMs > 0) sleep(periodMs);
            first = false;
        }
    }

    
    /**
     * Asserts given runnable succeeds in default duration.
     * @see #DEFAULT_TIMEOUT
     */
    public static void succeedsEventually(Runnable r) {
        succeedsEventually(ImmutableMap.<String,Object>of(), r);
    }

    public static void succeedsEventually(Map<String,?> flags, Runnable r) {
        succeedsEventually(flags, toCallable(r));
    }
    
    /**
     * Asserts given callable succeeds in default duration.
     * @see #DEFAULT_TIMEOUT
     */
    public static <T> T succeedsEventually(Callable<T> c) {
        return succeedsEventually(ImmutableMap.<String,Object>of(), c);
    }
    
    // FIXME duplication with TestUtils.BooleanWithMessage
    public static class BooleanWithMessage {
        boolean value; String message;
        public BooleanWithMessage(boolean value, String message) {
            this.value = value; this.message = message;
        }
        public boolean asBoolean() {
            return value;
        }
        public String toString() {
            return message;
        }
    }

    /**
     * Convenience method for cases where we need to test until something is true.
     *
     * The runnable will be invoked periodically until it succesfully concludes.
     * <p>
     * The following flags are supported:
     * <ul>
     * <li>abortOnError (boolean, default true)
     * <li>abortOnException - (boolean, default false)
     * <li>timeout - (a Duration or an integer in millis, defaults to 30*SECONDS)
     * <li>period - (a Duration or an integer in millis, for fixed retry time; if not set, defaults to exponentially increasing from 1 to 500ms)
     * <li>minPeriod - (a Duration or an integer in millis; only used if period not explicitly set; the minimum period when exponentially increasing; defaults to 1ms)
     * <li>maxPeriod - (a Duration or an integer in millis; only used if period not explicitly set; the maximum period when exponentially increasing; defaults to 500ms)
     * <li>maxAttempts - (integer, Integer.MAX_VALUE)
     * </ul>
     * 
     * The following flags are deprecated:
     * <ul>
     * <li>useGroovyTruth - (defaults to false; any result code apart from 'false' will be treated as success including null; ignored for Runnables which aren't Callables)
     * </ul>
     * 
     * @param flags, accepts the flags listed above
     * @param r
     * @param finallyBlock
     */
    public static <T> T succeedsEventually(Map<String,?> flags, Callable<T> c) {
        boolean abortOnException = get(flags, "abortOnException", false);
        boolean abortOnError = get(flags, "abortOnError", false);
        boolean useGroovyTruth = get(flags, "useGroovyTruth", false);
        boolean logException = get(flags, "logException", true);

        // To speed up tests, default is for the period to start small and increase...
        Duration duration = toDuration(flags.get("timeout"), DEFAULT_TIMEOUT);
        Duration fixedPeriod = toDuration(flags.get("period"), null);
        Duration minPeriod = (fixedPeriod != null) ? fixedPeriod : toDuration(flags.get("minPeriod"), Duration.millis(1));
        Duration maxPeriod = (fixedPeriod != null) ? fixedPeriod : toDuration(flags.get("maxPeriod"), Duration.millis(500));
        int maxAttempts = get(flags, "maxAttempts", Integer.MAX_VALUE);
        int attempt = 0;
        long startTime = System.currentTimeMillis();
        try {
            Throwable lastException = null;
            T result = null;
            long lastAttemptTime = 0;
            long expireTime = startTime+duration.toMilliseconds();
            long sleepTimeBetweenAttempts = minPeriod.toMilliseconds();
            
            while (attempt < maxAttempts && lastAttemptTime < expireTime) {
                try {
                    attempt++;
                    lastAttemptTime = System.currentTimeMillis();
                    result = c.call();
                    if (log.isTraceEnabled()) log.trace("Attempt {} after {} ms: {}", new Object[] {attempt, System.currentTimeMillis() - startTime, result});
                    if (useGroovyTruth) {
                        if (groovyTruth(result)) return result;
                    } else if (Boolean.FALSE.equals(result)) {
                        if (result instanceof BooleanWithMessage) 
                            log.warn("Test returned an instance of BooleanWithMessage but useGroovyTruth is not set! " +
                                     "The result of this probably isn't what you intended.");
                        // FIXME surprising behaviour, "false" result here is acceptable
                        return result;
                    } else {
                        return result;
                    }
                    lastException = null;
                } catch(Throwable e) {
                    lastException = e;
                    if (log.isTraceEnabled()) log.trace("Attempt {} after {} ms: {}", new Object[] {attempt, System.currentTimeMillis() - startTime, e.getMessage()});
                    if (abortOnException) throw e;
                    if (abortOnError && e instanceof Error) throw e;
                }
                long sleepTime = Math.min(sleepTimeBetweenAttempts, expireTime-System.currentTimeMillis());
                if (sleepTime > 0) Thread.sleep(sleepTime);
                sleepTimeBetweenAttempts = Math.min(sleepTimeBetweenAttempts*2, maxPeriod.toMilliseconds());
            }
            
            log.debug("TestUtils.executeUntilSucceedsWithFinallyBlockInternal exceeded max attempts or timeout - {} attempts lasting {} ms", attempt, System.currentTimeMillis()-startTime);
            if (lastException != null)
                throw lastException;
            throw fail("invalid result: "+result);
        } catch (Throwable t) {
            if (logException) log.info("failed succeeds-eventually, "+attempt+" attempts, "+
                    (System.currentTimeMillis()-startTime)+"ms elapsed "+
                    "(rethrowing): "+t);
            throw propagate(t);
        }
    }

    public static <T> void succeedsContinually(Runnable r) {
        succeedsContinually(ImmutableMap.<String,Object>of(), r);
    }
    
    public static <T> void succeedsContinually(Map<?,?> flags, Runnable r) {
        succeedsContinually(flags, toCallable(r));
    }

    public static <T> T succeedsContinually(Callable<T> c) {
        return succeedsContinually(ImmutableMap.<String,Object>of(), c);
    }
    
    public static <T> T succeedsContinually(Map<?,?> flags, Callable<T> job) {
        Duration duration = toDuration(flags.get("timeout"), Duration.ONE_SECOND);
        Duration period = toDuration(flags.get("period"), Duration.millis(10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+duration.toMilliseconds();
        
        boolean first = true;
        T result = null;
        while (first || System.currentTimeMillis() <= expireTime) {
            try {
                result = job.call();
            } catch (Exception e) {
                throw propagate(e);
            }
            if (periodMs > 0) sleep(periodMs);
            first = false;
        }
        return result;
    }
    
    private static Duration toDuration(Object duration, Duration defaultVal) {
        if (duration == null)
            return defaultVal;
        else 
            return Duration.of(duration);
    }
    
    public static void assertFails(Runnable r) {
        assertFailsWith(toCallable(r), Predicates.alwaysTrue());
    }
    
    public static void assertFails(Callable<?> c) {
        assertFailsWith(c, Predicates.alwaysTrue());
    }
    
    public static void assertFailsWith(Callable<?> c, final Closure<Boolean> exceptionChecker) {
        assertFailsWith(c, new Predicate<Throwable>() {
            public boolean apply(Throwable input) {
                return exceptionChecker.call(input);
            }
        });
    }
    
    public static void assertFailsWith(Runnable c, final Class<? extends Throwable> validException, final Class<? extends Throwable> ...otherValidExceptions) {
        final List<Class<?>> validExceptions = ImmutableList.<Class<?>>builder()
                .add(validException)
                .addAll(ImmutableList.copyOf(otherValidExceptions))
                .build();
        
        assertFailsWith(c, new Predicate<Throwable>() {
            public boolean apply(Throwable e) {
                for (Class<?> validException: validExceptions) {
                    if (validException.isInstance(e)) return true;
                }
                fail("Test threw exception of unexpected type "+e.getClass()+"; expecting "+validExceptions);
                return false;
            }
        });
    }

    public static void assertFailsWith(Runnable r, Predicate<? super Throwable> exceptionChecker) {
        assertFailsWith(toCallable(r), exceptionChecker);
    }
    
    public static void assertFailsWith(Callable<?> c, Predicate<? super Throwable> exceptionChecker) {
        boolean failed = false;
        try {
            c.call();
        } catch (Throwable e) {
            failed = true;
            if (!exceptionChecker.apply(e)) {
                log.debug("Test threw invalid exception (failing)", e);
                fail("Test threw invalid exception: "+e);
            }
            log.debug("Test for exception successful ("+e+")");
        }
        if (!failed) fail("Test code should have thrown exception but did not");
    }

    public static void assertReturnsEventually(final Runnable r, Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        Runnable wrappedR = new Runnable() {
            @Override public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    throwable.set(t);
                    throw Exceptions.propagate(t);
                }
            }
        };
        Thread thread = new Thread(wrappedR, "assertReturnsEventually("+r+")");
        try {
            thread.start();
            thread.join(timeout.toMilliseconds());
            if (thread.isAlive()) {
                throw new TimeoutException("Still running: r="+r+"; thread="+Arrays.toString(thread.getStackTrace()));
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            thread.interrupt();
        }
        
        if (throwable.get() !=  null) {
            throw new ExecutionException(throwable.get());
        }
    }

    public static <T> void assertThat(T object, Predicate<T> condition) {
        if (condition.apply(object)) return;
        fail("Failed "+condition+": "+object);
    }

    @SuppressWarnings("rawtypes")
    private static boolean groovyTruth(Object o) {
        // TODO Doesn't handle matchers (see http://docs.codehaus.org/display/GROOVY/Groovy+Truth)
        if (o == null) {
            return false;
        } else if (o instanceof Boolean) {
            return (Boolean)o;
        } else if (o instanceof String) {
            return !((String)o).isEmpty();
        } else if (o instanceof Collection) {
            return !((Collection)o).isEmpty();
        } else if (o instanceof Map) {
            return !((Map)o).isEmpty();
        } else if (o instanceof Iterator) {
            return ((Iterator)o).hasNext();
        } else if (o instanceof Enumeration) {
            return ((Enumeration)o).hasMoreElements();
        } else {
            return true;
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T get(Map<String,?> map, String key, T defaultVal) {
        Object val = map.get(key);
        return (T) ((val == null) ? defaultVal : val);
    }
    
    private static Callable<?> toCallable(Runnable r) {
        return (r instanceof Callable) ? (Callable<?>)r : Executors.callable(r);
    }
    
    private static void sleep(long periodMs) {
        if (periodMs > 0) {
            try {
                Thread.sleep(periodMs);
            } catch (InterruptedException e) {
                throw propagate(e);
            }
        }
    }
    
    private static RuntimeException propagate(Throwable t) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (t instanceof RuntimeException) throw (RuntimeException)t;
        if (t instanceof Error) throw (Error)t;
        throw new RuntimeException(t);
    }
}
