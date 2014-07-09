package brooklyn.test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;

import com.google.common.util.concurrent.Callables;

public class AssertsTest {

    private static final Runnable NOOP_RUNNABLE = new Runnable() {
        @Override public void run() {
        }
    };
    
    // TODO this is confusing -- i'd expect it to fail since it always returns false;
    // see notes at start of Asserts and in succeedsEventually method
    @Test
    public void testSucceedsEventually() {
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.millis(50)), Callables.returning(false));
    }
    
    @Test
    public void testAssertReturnsEventually() throws Exception {
        Asserts.assertReturnsEventually(NOOP_RUNNABLE, Duration.THIRTY_SECONDS);
    }
    
    @Test
    public void testAssertReturnsEventuallyTimesOut() throws Exception {
        final AtomicBoolean interrupted = new AtomicBoolean();
        
        try {
            Asserts.assertReturnsEventually(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(60*1000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }},
                Duration.of(10, TimeUnit.MILLISECONDS));
            Assert.fail("Should have thrown AssertionError on timeout");
        } catch (TimeoutException e) {
            // success
        }
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Assert.assertTrue(interrupted.get());
            }});
    }
    
    @Test
    public void testAssertReturnsEventuallyPropagatesException() throws Exception {
        try {
            Asserts.assertReturnsEventually(new Runnable() {
                public void run() {
                    throw new IllegalStateException("Simulating failure");
                }},
                Duration.THIRTY_SECONDS);
            Assert.fail("Should have thrown AssertionError on timeout");
        } catch (ExecutionException e) {
            IllegalStateException ise = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (ise == null || !ise.toString().contains("Simulating failure")) throw e;
        }
    }
}
