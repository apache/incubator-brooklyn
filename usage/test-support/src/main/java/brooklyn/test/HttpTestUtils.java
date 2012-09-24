package brooklyn.test;

import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Throwables.getCausalChain;
import static com.google.common.collect.Iterables.find;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * Utility methods to aid testing HTTP.
 * 
 * @author aled
 */
public class HttpTestUtils {

	// TODO Delete methods from TestUtils, to just have them here (or switch so TestUtils delegates here,
	// and deprecate methods in TestUtils until deleted).
	
    public static int getHttpStatusCode(String url) {
        return TestUtils.urlRespondsStatusCode(url);
    }

    public static void assertUrlUnreachable(String url) {
        try {
            int statusCode = getHttpStatusCode(url);
            fail("Expected url "+url+" unreachable, but got status code "+statusCode);
        } catch (Exception e) {
            IOException cause = getFirstThrowableOfType(e, IOException.class);
            if (cause != null) {
                // success; clean shutdown transitioning from 400 to error
            } else {
                Throwables.propagate(e);
            }                        
        }
    }

    public static void assertUrlUnreachableEventually(final String url) {
        assertUrlUnreachableEventually(Maps.newLinkedHashMap(), url);
    }
    
    public static void assertUrlUnreachableEventually(Map flags, final String url) {
        TestUtils.executeUntilSucceeds(flags, new Runnable() {
            public void run() {
                assertUrlUnreachable(url);
            }
         });
    }

    public static void assertHttpStatusCodeEquals(String url, int expectedCode) {
        try {
            assertEquals(getHttpStatusCode(url), expectedCode, "url="+url);
        } catch (Exception e) {
            throw new IllegalStateException("Server at "+url+" failed to respond (in assertion that result code is "+expectedCode+"): "+e, e);
        }
    }

    public static void assertHttpStatusCodeEventuallyEquals(final String url, final int expectedCode) {
        assertHttpStatusCodeEventuallyEquals(Maps.newLinkedHashMap(),  url, expectedCode);
    }

    public static void assertHttpStatusCodeEventuallyEquals(Map flags, final String url, final int expectedCode) {
        TestUtils.executeUntilSucceeds(flags, new Runnable() {
            public void run() {
                assertHttpStatusCodeEquals(url, expectedCode);
            }
         });
    }

    public static void assertHttpContentEventuallyContainsText(final String url, final String containedText) {
        TestUtils.executeUntilSucceeds(new Runnable() {
            public void run() {
                TestUtils.assertUrlHasText(url, containedText);
            }
         });
    }
    
    /**
     * Schedules (with the given executor) a poller that repeatedly accesses the given url, to confirm it always gives
     * back the expected status code.
     * 
     * Expected usage is to query the future, such as:
     * 
     * <code>
     *     Future<?> future = assertAsyncHttpStatusCodeContinuallyEquals(executor, url, 200);
     *     // do other stuff...
     *     if (future.isDone()) future.get(); // get exception if it's failed
     * </code>
     * 
     * For stopping it, you can either do future.cancel(true), or you can just do executor.shutdownNow().
     * 
     * TODO Look at difference between this and WebAppMonitor, to decide if this should be kept.
     */
    public static ListenableFuture<?> assertAsyncHttpStatusCodeContinuallyEquals(ListeningExecutorService executor, final String url, final int expectedStatusCode) {
        return executor.submit(new Runnable() {
            @Override public void run() {
                // TODO Need to drop logging; remove sleep when that's done.
                while (!Thread.currentThread().isInterrupted()) {
                    assertHttpStatusCodeEquals(url, expectedStatusCode);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return; // graceful return
                    }
                }
            }
        });
    }
    
    // TODO Part-duplicated from jclouds Throwables2
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T getFirstThrowableOfType(Throwable from, Class<T> clazz) {
       try {
          return (T) find(getCausalChain(from), instanceOf(clazz));
       } catch (NoSuchElementException e) {
          return null;
       }
    }
}
