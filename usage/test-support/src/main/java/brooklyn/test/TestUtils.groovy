package brooklyn.test

import static org.testng.AssertJUnit.*
import groovy.time.TimeDuration

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit

import org.codehaus.groovy.runtime.InvokerInvocationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity

import com.google.common.base.Predicate
import com.google.common.base.Supplier

/**
 * Helper functions for tests of Tomcat, JBoss and others.
 */
public class TestUtils {
    private static final Logger log = LoggerFactory.getLogger(TestUtils.class)

    private TestUtils() { }

    /** True if two attempts to connect to the port succeed. */
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

    /** Connects to the given HTTP URL and asserts that the response had status code 200. */
    public static boolean urlRespondsWithStatusCode200(String url) {
        def connection = connectToURL(url)
        int status = ((HttpURLConnection) connection).getResponseCode()
        log.info "connection to {} gives {}", url, status
        if (status == 404)
            throw new Exception("Connection to $url gave 404");
        return status == 200
    }
    
    /** Connects to the given url and returns the connection. */
    public static URLConnection connectToURL(String u) {
        URL url = [u]
        URLConnection connection = url.openConnection()
        connection.connect()
        connection.getContentLength() // Make sure the connection is made.
        return connection
    }
    
    public static void assertEventually(Map flags=[:], Callable c) {
        executeUntilSucceeds(flags, c);
    }
    public static void assertEventually(Map flags=[:], Runnable c) {
        executeUntilSucceeds(flags, c);
    }

    //FIXME rename these to assertEventually, refactor to have boolean blockUntil in some other util class
    //FIXME remove dupilcation with LanguageUtils.repeatUntilSuccess
    public static void executeUntilSucceeds(Map flags=[:], Closure c) {
        executeUntilSucceedsWithFinallyBlock(flags, c) { }
    }

    public static void executeUntilSucceeds(Map flags=[:], Callable c) {
        executeUntilSucceedsWithFinallyBlock(flags, c) { }
    }
    
    public static void executeUntilSucceeds(Map flags=[:], Runnable r) {
        if (r in Callable) 
            executeUntilSucceedsWithFinallyBlock(flags, {return ((Callable)r).call();}, { })
        else if (r in Closure)  // Closure check probably not necessary, just was trying to fix a server build which had a screwy problem
            executeUntilSucceedsWithFinallyBlock(flags, {return ((Closure)r).call();}, { })
        else
            executeUntilSucceedsWithFinallyBlock(flags, {r.run(); return true}, { })
    }

    public static void executeUntilSucceedsElseShutdown(Map flags=[:], Entity entity, Closure c) {
        try { 
            executeUntilSucceedsWithFinallyBlock(flags, c) { }
        } catch (Throwable t) {
            entity.stop()
            throw t
        }
    }

    /** convenience for entities to ensure they shutdown afterwards */
    public static void executeUntilSucceedsWithShutdown(Map flags=[:], Entity entity, Closure c) {
        executeUntilSucceedsWithFinallyBlock(flags, c) { entity.stop() }
    }

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
     * <li>timeout - (a TimeDuration or an integer in millis, defaults to 30*SECONDS)
     * <li>period - (a TimeDuration or an integer in millis, for fixed retry time; if not set, defaults to exponentially increasing from 1 to 500ms)
     * <li>minPeriod - (a TimeDuration or an integer in millis; only used if period not explicitly set; the minimum period when exponentially increasing; defaults to 1ms)
     * <li>maxPeriod - (a TimeDuration or an integer in millis; only used if period not explicitly set; the maximum period when exponentially increasing; defaults to 500ms)
     * <li>maxAttempts - (integer, Integer.MAX_VALUE)
     * </ul>
     *
     * @param flags, accepts the flags listed above
     * @param r
     * @param finallyBlock
     */
    public static void executeUntilSucceedsWithFinallyBlock(Map flags=[:], Callable<?> c, Closure finallyBlock={}) {
        executeUntilSucceedsWithFinallyBlockInternal(flags, c, finallyBlock);
    }
    /** the "real" implementation, renamed to allow multiple entry points (depending whether closure cast to callable) */
    private static void executeUntilSucceedsWithFinallyBlockInternal(Map flags=[:], Callable<?> c, Closure finallyBlock={}) {
//        log.trace "abortOnError = {}", flags.abortOnError
        boolean abortOnException = flags.abortOnException ?: false
        boolean abortOnError = flags.abortOnError ?: false
        boolean useGroovyTruth = flags.useGroovyTruth ?: false
        boolean logException = flags.logException ?: true

        // To speed up tests, default is for the period to start small and increase...
        TimeDuration duration = toTimeDuration(flags.timeout) ?: new TimeDuration(0,0,30,0)
        TimeDuration fixedPeriod = toTimeDuration(flags.period) ?: null
        TimeDuration minPeriod = fixedPeriod ?: toTimeDuration(flags.minPeriod) ?: new TimeDuration(0,0,0,1)
        TimeDuration maxPeriod = fixedPeriod ?: toTimeDuration(flags.maxPeriod) ?: new TimeDuration(0,0,0,500)
        int maxAttempts = flags.maxAttempts ?: Integer.MAX_VALUE
        try {
            Throwable lastException = null;
            Object result;
            long lastAttemptTime = 0;
            long startTime = System.currentTimeMillis()
            long expireTime = startTime+duration.toMilliseconds()
            int attempt = 0;
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
            
            log.trace "Exceeded max attempts or timeout - {} attempts lasting {} ms", attempt, System.currentTimeMillis()-startTime
            if (lastException != null)
                throw lastException
            fail "invalid result: $result"
        } catch (Throwable t) {
			if (logException) log.info("failed execute-until-succeeds (rethrowing): "+t)
			throw t
        } finally {
            finallyBlock.call()
        }
    }

    public static <T> void assertSucceedsContinually(Map flags=[:], Runnable job) {
        assertSucceedsContinually(flags, Executors.callable(job));
    }
    
    public static <T> void assertSucceedsContinually(Map flags=[:], Callable<T> job) {
        TimeDuration duration = toTimeDuration(flags.timeout) ?: new TimeDuration(0,0,1,0)
        TimeDuration period = toTimeDuration(flags.period) ?: new TimeDuration(0,0,0,10)
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
    
    public static <T> void assertContinually(Map flags=[:], Supplier<? extends T> supplier, Predicate<T> predicate) {
        assertContinually(flags, supplier, predicate, (String)null);
    }
    
    public static <T> void assertContinually(Map flags=[:], Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg, long durationMs) {
        TimeDuration duration = toTimeDuration(flags.timeout) ?: new TimeDuration(0,0,1,0)
        TimeDuration period = toTimeDuration(flags.period) ?: new TimeDuration(0,0,0,10)
        long periodMs = period.toMilliseconds()
        long startTime = System.currentTimeMillis()
        long expireTime = startTime+duration.toMilliseconds()
        
        boolean first = true;
        while (first || System.currentTimeMillis() <= expireTime) {
            assertTrue(predicate.apply(supplier.get()), "supplied="+supplier.get()+"; predicate="+predicate+(errMsg!=null?"; "+errMsg:""));
            if (periodMs > 0) sleep(periodMs);
            first = false;
        }
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
    
    public static File getResource(String path, ClassLoader loader) {
        URL resource = loader.getResource(path)
        if (resource==null)
            throw new IllegalArgumentException("cannot find required entity '"+path+"'");
            
        return new File(resource.path)
    }

    public static TimeDuration toTimeDuration(Object duration) {
        if (duration == null) {
            return null
        } else if (duration instanceof TimeDuration) {
            return (TimeDuration) duration
        } else if (duration instanceof Number) {
            return new TimeDuration(0,0,0,(int)duration)
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
    
    public static void assertUrlHasText(Map flags=[:], String url, String ...phrases) {
        String contents;
        TimeDuration timeout = flags.timeout in Number ? flags.timeout*TimeUnit.MILLISECONDS : flags.timeout ?: 30*TimeUnit.SECONDS
        executeUntilSucceeds(timeout:timeout, maxAttempts:50) {
            contents = new URL(url).openStream().getText();
            assertTrue(contents!=null && contents.length()>0)
        }
        for (String text: phrases) {
            if (!contents.contains(text)) {
                log.warn("CONTENTS OF URL MISSING TEXT: $text\n"+contents)
                fail("URL $url does not contain text: $text")
            }
        }
    }

}
