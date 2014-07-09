package brooklyn.test

import static org.testng.Assert.*
import groovy.time.TimeDuration

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import org.codehaus.groovy.runtime.InvokerInvocationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.util.time.Duration;

import com.google.common.base.Predicate
import com.google.common.base.Supplier
import com.google.common.collect.Iterables

/**
 * Helper functions for tests of Tomcat, JBoss and others.
 * 
 * Note that methods will migrate from here to {@link Asserts} in future releases.
 */
public class TestUtils {
    private static final Logger log = LoggerFactory.getLogger(TestUtils.class)

    private TestUtils() { }

    /**
     * True if two attempts to connect to the port succeed.
     * 
     * @deprecated since 0.5; use {@link brooklyn.util.NetworkUtils#isPortAvailable(int)}
     */
    @Deprecated
    public static boolean isPortInUse(int port, long retryAfterMillis=0) {
        try {
            def s = new Socket("localhost", port)
            s.close()
            if (retryAfterMillis>0) {
                log.debug "port {} still open, waiting 1s for it to really close", port
                //give it 1s to close
                Thread.sleep retryAfterMillis
                s = new Socket("localhost", port)
                s.close()
            }
            log.debug "port {} still open (conclusive)", port
            return true
        } catch (ConnectException e) {
            return false
        }
    }

    /**
     * Connects to the given HTTP URL and asserts that the response had status code 200.
     * @deprecated Use HttpTestUtils.getHttpStatusCode(url) == 200
     */
    @Deprecated
    public static boolean urlRespondsWithStatusCode200(String url) {
        int status = HttpTestUtils.getHttpStatusCode(url);
        log.debug "connection to {} gives {}", url, status
        if (status == 404)
            throw new Exception("Connection to $url gave 404");
        return status == 200
    }
    
    /** 
     * Connects to the given HTTP URL and asserts that the response had status code 200.
     * @deprecated Use HttpTestUtils.getHttpStatusCode(url)
     */
    @Deprecated
    public static int urlRespondsStatusCode(String url) {
        return HttpTestUtils.getHttpStatusCode(url);
    }
    
    /** 
     * Connects to the given url and returns the connection.
     * @deprecated Use HttpTestUtils.connectToUrl(url)
     */
    @Deprecated
    public static URLConnection connectToURL(String url) {
        return HttpTestUtils.connectToUrl(url);
    }
    
    // calling groovy from java doesn't cope with generics here; stripping them from here :-(
    //      <T> void assertEventually(Map flags=[:], Supplier<? extends T> supplier, Predicate<T> predicate)
    /**
     * @deprecated since 0.5; use {@link Asserts#eventually(Map, Supplier, Predicate)}
     */
    @Deprecated
    public static void assertEventually(Map flags=[:], Supplier supplier, Predicate predicate) {
        Asserts.eventually(flags, supplier, predicate);
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#eventually(Map, Supplier, Predicate, String)}
     */
    @Deprecated
    public static <T> void assertEventually(Map flags=[:], Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        Asserts.eventually(flags, supplier, predicate, errMsg);
    }

    /**    
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(java.util.Map, Callable)}
     */
    @Deprecated
    public static void assertEventually(Map flags=[:], Callable c) {
        executeUntilSucceeds(flags, c);
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Runnable)}
     */
    @Deprecated
    public static void assertEventually(Map flags=[:], Runnable c) {
        executeUntilSucceeds(flags, c);
    }

    /**
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Callable)}
     */
    @Deprecated
    public static void executeUntilSucceeds(Map flags=[:], Closure c) {
        Asserts.succeedsEventually(flags, c);
    }

    /**
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Callable)}
     */
    @Deprecated
    public static void executeUntilSucceeds(Map flags=[:], Callable c) {
        Asserts.succeedsEventually(flags, c);
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Runnable)}
     */
    @Deprecated
    public static void executeUntilSucceeds(Map flags=[:], Runnable r) {
        if (r in Callable) 
            executeUntilSucceedsWithFinallyBlock(flags, {return ((Callable)r).call();}, { })
        else if (r in Closure)  // Closure check probably not necessary, just was trying to fix a server build which had a screwy problem
            executeUntilSucceedsWithFinallyBlock(flags, {return ((Closure)r).call();}, { })
        else
            executeUntilSucceedsWithFinallyBlock(flags, {r.run(); return true}, { })
    }

    /**
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Callable)}, and tear-down with {@link AfterMethod}.
     */
    @Deprecated
    public static void executeUntilSucceedsElseShutdown(Map flags=[:], Entity entity, Closure c) {
        try { 
            executeUntilSucceedsWithFinallyBlock(flags, c) { }
        } catch (Throwable t) {
            entity.stop()
            throw t
        }
    }

    /**
     * convenience for entities to ensure they shutdown afterwards.
     * 
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Callable)}, and tear-down with {@link AfterMethod}.
     */
    @Deprecated
    public static void executeUntilSucceedsWithShutdown(Map flags=[:], Entity entity, Closure c) {
        executeUntilSucceedsWithFinallyBlock(flags, c) { entity.stop() }
    }

    /**
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Callable)}, and tear-down with {@link AfterMethod}.
     */
    @Deprecated
    public static void executeUntilSucceedsWithFinallyBlock(Map flags=[:], Closure c, Closure finallyBlock={}) {
        executeUntilSucceedsWithFinallyBlockInternal(flags, c, finallyBlock)
    }
    
    /**
     * Convenience method for cases where we need to test until something is true.
     *
     * The runnable will be invoked periodically until it succesfully concludes.
     * Additionally, a finally block can be supplied.
     * <p>
     * The following flags are supported:
     * <ul>
     * <li>abortOnError (boolean, default true)
     * <li>abortOnException - (boolean, default false)
     * <li>useGroovyTruth - (defaults to false; any result code apart from 'false' will be treated as success including null; ignored for Runnables which aren't Callables)
     * <li>timeout - (a Duration or an integer in millis, defaults to 30*SECONDS)
     * <li>period - (a Duration or an integer in millis, for fixed retry time; if not set, defaults to exponentially increasing from 1 to 500ms)
     * <li>minPeriod - (a Duration or an integer in millis; only used if period not explicitly set; the minimum period when exponentially increasing; defaults to 1ms)
     * <li>maxPeriod - (a Duration or an integer in millis; only used if period not explicitly set; the maximum period when exponentially increasing; defaults to 500ms)
     * <li>maxAttempts - (integer, Integer.MAX_VALUE)
     * </ul>
     *
     * @param flags, accepts the flags listed above
     * @param r
     * @param finallyBlock
     * 
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Callable)}, and tear-down with {@link AfterMethod}.
     */
    @Deprecated
    public static void executeUntilSucceedsWithFinallyBlock(Map flags=[:], Callable<?> c, Closure finallyBlock={}) {
        executeUntilSucceedsWithFinallyBlockInternal(flags, c, finallyBlock);
    }
    
    /**
     * the "real" implementation, renamed to allow multiple entry points (depending whether closure cast to callable)
     * 
     * @deprecated since 0.5; use {@link Asserts#succeedsEventually(Map, Callable)}, and tear-down with {@link AfterMethod}.
     */
    @Deprecated
    private static void executeUntilSucceedsWithFinallyBlockInternal(Map flags=[:], Callable<?> c, Closure finallyBlock={}) {
//        log.trace "abortOnError = {}", flags.abortOnError
        boolean abortOnException = flags.abortOnException ?: false
        boolean abortOnError = flags.abortOnError ?: false
        boolean useGroovyTruth = flags.useGroovyTruth ?: false
        boolean logException = flags.logException ?: true

        // To speed up tests, default is for the period to start small and increase...
        Duration duration = Duration.of(flags.timeout) ?: Duration.THIRTY_SECONDS;
        Duration fixedPeriod = Duration.of(flags.period) ?: null
        Duration minPeriod = fixedPeriod ?: Duration.of(flags.minPeriod) ?: Duration.millis(1)
        Duration maxPeriod = fixedPeriod ?: Duration.of(flags.maxPeriod) ?: Duration.millis(500)
        int maxAttempts = flags.maxAttempts ?: Integer.MAX_VALUE;
        int attempt = 0;
        long startTime = System.currentTimeMillis();
        try {
            Throwable lastException = null;
            Object result;
            long lastAttemptTime = 0;
            long expireTime = startTime+duration.toMilliseconds();
            long sleepTimeBetweenAttempts = minPeriod.toMilliseconds();
            
            while (attempt<maxAttempts && lastAttemptTime<expireTime) {
                try {
                    attempt++
                    lastAttemptTime = System.currentTimeMillis()
                    result = c.call()
                    log.trace "Attempt {} after {} ms: {}", attempt, System.currentTimeMillis() - startTime, result
                    if (useGroovyTruth) {
                        if (result) return;
                    } else if (result != false) {
                        if (result instanceof BooleanWithMessage) 
                            log.warn "Test returned an instance of BooleanWithMessage but useGroovyTruth is not set! " +
                                     "The result of this probably isn't what you intended."
                        return;
                    }
                    lastException = null
                } catch(Throwable e) {
                    lastException = e
                    log.trace "Attempt {} after {} ms: {}", attempt, System.currentTimeMillis() - startTime, e.message
                    if (abortOnException) throw e
                    if (abortOnError && e in Error) throw e
                }
                long sleepTime = Math.min(sleepTimeBetweenAttempts, expireTime-System.currentTimeMillis())
                if (sleepTime > 0) Thread.sleep(sleepTime)
                sleepTimeBetweenAttempts = Math.min(sleepTimeBetweenAttempts*2, maxPeriod.toMilliseconds())
            }
            
            log.debug "TestUtils.executeUntilSucceedsWithFinallyBlockInternal exceeded max attempts or timeout - {} attempts lasting {} ms", attempt, System.currentTimeMillis()-startTime
            if (lastException != null)
                throw lastException
            fail "invalid result: $result"
        } catch (Throwable t) {
			if (logException) log.info("failed execute-until-succeeds, "+attempt+" attempts, "+
                (System.currentTimeMillis()-startTime)+"ms elapsed "+
                "(rethrowing): "+t);
			throw t
        } finally {
            finallyBlock.call()
        }
    }

    /**
     * @deprecated since 0.5; use {@link Asserts#succeedsContinually(Map, Runnable)}
     */
    @Deprecated
    public static <T> void assertSucceedsContinually(Map flags=[:], Runnable job) {
        assertSucceedsContinually(flags, Executors.callable(job));
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#succeedsContinually(Map, Callable)}
     */
    @Deprecated
    public static void assertSucceedsContinually(Map flags=[:], Callable<?> job) {
        Duration duration = Duration.of(flags.timeout) ?: Duration.ONE_SECOND
        Duration period = Duration.of(flags.period) ?: Duration.millis(10)
        long periodMs = period.toMilliseconds()
        long startTime = System.currentTimeMillis()
        long expireTime = startTime+duration.toMilliseconds()
        
        boolean first = true;
        while (first || System.currentTimeMillis() <= expireTime) {
            job.call();
            if (periodMs > 0) sleep(periodMs);
            first = false;
        }
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#continually(Map, Supplier, Predicate)}
     */
    @Deprecated
    // FIXME When calling from java, the generics declared in groovy messing things up!
    public static void assertContinuallyFromJava(Map flags=[:], Supplier<?> supplier, Predicate<?> predicate) {
        Asserts.continually(flags, supplier, predicate);
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#continually(Map, Supplier, Predicate)}
     */
    @Deprecated
    public static <T> void assertContinually(Map flags=[:], Supplier<? extends T> supplier, Predicate<T> predicate) {
        Asserts.continually(flags, supplier, predicate, (String)null);
    }

    /**
     * @deprecated since 0.5; use {@link Asserts#continually(Map, Supplier, Predicate, String)}
     */
    @Deprecated
    public static <T> void assertContinually(Map flags=[:], Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg, long durationMs) {
        flags.put("duration", Duration.millis(durationMs));
        Asserts.continually(flags, supplier, predicate, errMsg);
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#continually(Map, Supplier, Predicate, String)}
     */
    @Deprecated
    public static <T> void assertContinually(Map flags=[:], Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        Asserts.continually(flags, supplier, predicate, errMsg);
    }
    
    public static class BooleanWithMessage {
        boolean value; String message;
        public BooleanWithMessage(boolean value, String message) {
            this.value = value; this.message = message;
        }
        public boolean asBoolean() {
            return value
        }
        public String toString() {
            return message
        }
    }
    
    /**
     * @deprecated since 0.5; use {@link brooklyn.util.ResourceUtils}
     */
    @Deprecated
    public static File getResource(String path, ClassLoader loader) {
        URL resource = loader.getResource(path)
        if (resource==null)
            throw new IllegalArgumentException("cannot find required entity '"+path+"'");
            
        return new File(resource.path)
    }

    /**
     * @deprecated since 0.5; use long and {@link TimeUnit}
     */
    @Deprecated
    public static TimeDuration toTimeDuration(Object duration) {
        return toTimeDuration(duration, null);
    }
            
    /**
     * @deprecated since 0.5; use long and {@link TimeUnit}
     */
    @Deprecated
    public static TimeDuration toTimeDuration(Object duration, TimeDuration defaultVal) {
        if (duration == null) {
            return defaultVal;
        } else if (duration instanceof TimeDuration) {
            return (TimeDuration) duration
        } else if (duration instanceof Number) {
            return new TimeDuration(0,0,0,(int)duration)
            // TODO would be nice to have this, but we need to sort out utils / test-utils dependency
//        } else if (duration instanceof String) {
//            return Time.parseTimeString((String)duration);
        } else {
            throw new IllegalArgumentException("Cannot convert $duration of type ${duration.class.name} to a TimeDuration")
        }
    }
    
    public static Throwable unwrapThrowable(Throwable t) {
        if (t.getCause() == null) {
            return t;
        } else if (t instanceof ExecutionException) {
            return unwrapThrowable(t.getCause())
        } else if (t instanceof InvokerInvocationException) {
            return unwrapThrowable(t.getCause())
        } else {
            return t
        }
    }

    /**
     * @deprecated since 0.5; use {@link EntityTestUtils#assertAttributeEqualsEventually(Entity, AttributeSensor, Object)}
     */
    @Deprecated
    public static <T> void assertAttributeEventually(Entity entity, AttributeSensor<T> attribute, T expected) {
        executeUntilSucceeds() {
            assertEquals(entity.getAttribute(attribute), expected);
        }
    }
    
    /**
     * @deprecated since 0.5; use {@link EntityTestUtils#assertAttributeEqualsContinually(Entity, AttributeSensor, Object)}
     */
    @Deprecated
    public static <T> void assertAttributeContinually(Entity entity, AttributeSensor<T> attribute, T expected) {
        assertSucceedsContinually() {
            assertEquals(entity.getAttribute(attribute), expected);
        }
    }
    
    /**
     * @deprecated since 0.5; use {@link HttpTestUtils#assertHttpStatusCodeEquals(String, int)}
     */
    @Deprecated
    public static void assertUrlStatusCodeEventually(final String url, final int expected) {
        executeUntilSucceeds() {
            assertEquals(urlRespondsStatusCode(url), expected);
        }
    }

    /**
     * @deprecated since 0.5; use {@link Asserts#assertFails(Runnable)}
     */
    @Deprecated
    public static void assertFails(Runnable c) {
        assertFailsWith(c, (Predicate)null);
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#assertFailsWith(Closure)}
     */
    @Deprecated
    public static void assertFailsWith(Runnable c, Closure exceptionChecker) {
        assertFailsWith(c, exceptionChecker as Predicate);
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#assertFailsWith(Runnable, Class, Class...)}
     */
    @Deprecated
    public static void assertFailsWith(Runnable c, final Class<? extends Throwable> validException, final Class<? extends Throwable> ...otherValidExceptions) {
        assertFailsWith(c, { e -> 
            if (validException.isInstance(e)) return true;
            if (otherValidExceptions.find {it.isInstance(e)}) return true;
            List expectedTypes = [validException];
            expectedTypes.addAll(Arrays.asList(otherValidExceptions));
            fail("Test threw exception of unexpected type "+e.getClass()+"; expecting "+expectedTypes);             
        });
    }
    
    /**
     * @deprecated since 0.5; use {@link Asserts#assertFailsWith(Runnable, Predicate)}
     */
    @Deprecated
    public static void assertFailsWith(Runnable c, Predicate<Throwable> exceptionChecker) {
        boolean failed = false;
        try {
            c.run();
        } catch (Throwable e) {
            failed = true;
            if (exceptionChecker!=null) {
                if (!exceptionChecker.apply(e)) {
                    fail("Test threw invalid exception: "+e);
                }
            }
            log.debug("Test for exception successful ("+e+")");
        }
        if (!failed) fail("Test code should have thrown exception but did not");
    }

    public static void assertSetsEqual(Collection c1, Collection c2) {
        Set s = new LinkedHashSet();
        s.addAll(c1); s.removeAll(c2);
        if (!s.isEmpty()) fail("First argument contains additional contents: "+s);
        s.clear(); s.addAll(c2); s.removeAll(c1);
        if (!s.isEmpty()) fail("Second argument contains additional contents: "+s);
    }
    
    /**
     * @deprecated since 0.5; use {@code assertFalse(Iterables.isEmpty(c))}
     */
    @Deprecated
    public static <T> void assertNonEmpty(Iterable<T> c) {
        if (c.iterator().hasNext()) return;
        fail("Expected non-empty set");
    }

    /**
     * @deprecated since 0.5; use {@code assertEquals(Iterables.size(c), expectedSize)}
     */
    @Deprecated
    public static <T> void assertSize(Iterable<T> c, int expectedSize) {
        int actualSize = Iterables.size(c);
        if (actualSize==expectedSize) return;
        fail("Expected collection of size "+expectedSize+" but got size "+actualSize+": "+c);
    }

    public static void assertStringContainsLiteral(String string, String substring) {
        if (string==null) fail("String is null");
        if (substring==null) fail("Substring is null");
        if (string.indexOf(substring)>=0) return;
        fail("String '"+string+"' does not contain expected pattern '"+substring+"'");
    }
    
}
