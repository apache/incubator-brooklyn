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

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Time;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
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

    private static final Logger LOG = LoggerFactory.getLogger(HttpTestUtils.class);

    static final ExecutorService executor = Executors.newCachedThreadPool();
    
    /**
     * Connects to the given url and returns the connection.
     * Caller should {@code connection.getInputStream().close()} the result of this
     * (especially if they are making heavy use of this method).
     */
    public static URLConnection connectToUrl(String u) throws Exception {
        final URL url = new URL(u);
        final AtomicReference<Exception> exception = new AtomicReference<Exception>();
        
        // sometimes openConnection hangs, so run in background
        Future<URLConnection> f = executor.submit(new Callable<URLConnection>() {
            public URLConnection call() {
                try {
                    HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                        @Override public boolean verify(String s, SSLSession sslSession) {
                            return true;
                        }
                    });
                    URLConnection connection = url.openConnection();
                    TrustingSslSocketFactory.configure(connection);
                    connection.connect();
    
                    connection.getContentLength(); // Make sure the connection is made.
                    return connection;
                } catch (Exception e) {
                    exception.set(e);
                    LOG.debug("Error connecting to url "+url+" (propagating): "+e, e);
                }
                return null;
            }
        });
        try {
            URLConnection result = null;
            try {
                result = f.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                LOG.debug("Error connecting to url "+url+", probably timed out (rethrowing): "+e);
                throw new IllegalStateException("Connect to URL not complete within 60 seconds, for url "+url+": "+e);
            }
            if (exception.get() != null) {
                LOG.debug("Error connecting to url "+url+", thread caller of "+exception, new Throwable("source of rethrown error "+exception));
                throw exception.get();
            } else {
                return result;
            }
        } finally {
            f.cancel(true);
        }
    }

    public static void assertHealthyStatusCode(int code) {
        if (code>=200 && code<=299) return;
        Assert.fail("Wrong status code: "+code);
    }
    
    public static int getHttpStatusCode(String url) throws Exception {
        URLConnection connection = connectToUrl(url);
        long startTime = System.currentTimeMillis();
        int status = ((HttpURLConnection) connection).getResponseCode();
        
        // read fully if possible, then close everything, trying to prevent cached threads at server
        consumeAndCloseQuietly((HttpURLConnection) connection);
        
        if (LOG.isDebugEnabled())
            LOG.debug("connection to {} ({}ms) gives {}", new Object[] { url, (System.currentTimeMillis()-startTime), status });
        return status;
    }

    /**
     * Asserts that gets back any "valid" response - i.e. not an exception. This could be an unauthorized,
     * a redirect, a 404, or anything else that implies there is web-server listening on that port.
     */
    public static void assertUrlReachable(String url) {
        try {
            getHttpStatusCode(url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted for "+url+" (in assertion that is reachable)", e);
        } catch (Exception e) {
            throw new IllegalStateException("Server at "+url+" failed to respond (in assertion that is reachable): "+e, e);
        }
    }

    public static void assertUrlUnreachable(String url) {
        try {
            int statusCode = getHttpStatusCode(url);
            fail("Expected url "+url+" unreachable, but got status code "+statusCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted for "+url+" (in assertion that unreachable)", e);
        } catch (Exception e) {
            IOException cause = Exceptions.getFirstThrowableOfType(e, IOException.class);
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
        Asserts.succeedsEventually(flags, new Runnable() {
            public void run() {
                assertUrlUnreachable(url);
            }
         });
    }

    public static void assertHttpStatusCodeEquals(String url, int... acceptableReturnCodes) {
        List<Integer> acceptableCodes = Lists.newArrayList();
        for (int code : acceptableReturnCodes) {
            acceptableCodes.add((Integer)code);
        }
        try {
            int actualCode = getHttpStatusCode(url);
            assertTrue(acceptableCodes.contains(actualCode), "code="+actualCode+"; expected="+acceptableCodes+"; url="+url);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted for "+url+" (in assertion that result code is "+acceptableCodes+")", e);
        } catch (Exception e) {
            throw new IllegalStateException("Server at "+url+" failed to respond (in assertion that result code is "+acceptableCodes+"): "+e, e);
        }
    }

    public static void assertHttpStatusCodeEventuallyEquals(final String url, final int expectedCode) {
        assertHttpStatusCodeEventuallyEquals(Maps.newLinkedHashMap(),  url, expectedCode);
    }

    public static void assertHttpStatusCodeEventuallyEquals(Map flags, final String url, final int expectedCode) {
        Asserts.succeedsEventually(flags, new Runnable() {
            public void run() {
                assertHttpStatusCodeEquals(url, expectedCode);
            }
         });
    }

    public static void assertContentContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        try {
            String contents = getContent(url);
            Assert.assertTrue(contents != null && contents.length() > 0);
            for (String text: Lists.asList(phrase, additionalPhrases)) {
                if (!contents.contains(text)) {
                    LOG.warn("CONTENTS OF URL "+url+" MISSING TEXT: "+text+"\n"+contents);
                    Assert.fail("URL "+url+" does not contain text: "+text);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static void assertContentNotContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        try {
            String contents = getContent(url);
            Assert.assertTrue(contents != null);
            for (String text: Lists.asList(phrase, additionalPhrases)) {
                if (contents.contains(text)) {
                    LOG.warn("CONTENTS OF URL "+url+" HAS TEXT: "+text+"\n"+contents);
                    Assert.fail("URL "+url+" contain text: "+text);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static void assertErrorContentContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        try {
            String contents = getErrorContent(url);
            Assert.assertTrue(contents != null && contents.length() > 0);
            for (String text: Lists.asList(phrase, additionalPhrases)) {
                if (!contents.contains(text)) {
                    LOG.warn("CONTENTS OF URL "+url+" MISSING TEXT: "+text+"\n"+contents);
                    Assert.fail("URL "+url+" does not contain text: "+text);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }


    public static void assertErrorContentNotContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        try {
            String err = getErrorContent(url);
            Assert.assertTrue(err != null);
            for (String text: Lists.asList(phrase, additionalPhrases)) {
                if (err.contains(text)) {
                    LOG.warn("CONTENTS OF URL "+url+" HAS TEXT: "+text+"\n"+err);
                    Assert.fail("URL "+url+" contain text: "+text);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    public static void assertContentEventuallyContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        assertContentEventuallyContainsText(MutableMap.of(), url, phrase, additionalPhrases);
    }
    
    public static void assertContentEventuallyContainsText(Map flags, final String url, final String phrase, final String ...additionalPhrases) {
        Asserts.succeedsEventually(flags, new Runnable() {
            public void run() {
                assertContentContainsText(url, phrase, additionalPhrases);
            }
         });
    }
    
    public static void assertContentMatches(String url, String regex) {
        String contents = getContent(url);
        Assert.assertNotNull(contents);
        Assert.assertTrue(contents.matches(regex), "Contents does not match expected regex ("+regex+"): "+contents);
    }

    public static void assertContentEventuallyMatches(final String url, final String regex) {
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertContentMatches(url, regex);
            }
        });
    }
    
    public static String getErrorContent(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) connectToUrl(url);
            long startTime = System.currentTimeMillis();
            
            String err;
            int status;
            try {
                InputStream errStream = connection.getErrorStream();
                err = Streams.readFullyString(errStream);
                status = connection.getResponseCode();
            } finally {
                closeQuietly(connection);
            }
            
            if (LOG.isDebugEnabled())
                LOG.debug("read of err {} ({}ms) complete; http code {}", new Object[] { url, Time.makeTimeStringRounded(System.currentTimeMillis()-startTime), status});
            return err;

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public static String getContent(String url) {
        try {
            return DefaultGroovyMethods.getText(new URL(url).openStream());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    /**
     * Schedules (with the given executor) a poller that repeatedly accesses the given url, to confirm it always gives
     * back the expected status code.
     * 
     * Expected usage is to query the future, such as:
     * 
     * <pre>
     * {@code
     * Future<?> future = assertAsyncHttpStatusCodeContinuallyEquals(executor, url, 200);
     * // do other stuff...
     * if (future.isDone()) future.get(); // get exception if it's failed
     * }
     * </pre>
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
    
    /**
     * Consumes the input stream entirely and then cleanly closes the connection.
     * Ignores all exceptions completely, not even logging them!
     * 
     * Consuming the stream fully is useful for preventing idle TCP connections. 
     * See {@linkplain http://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html}.
     */
    public static void consumeAndCloseQuietly(HttpURLConnection connection) {
        try { Streams.readFully(connection.getInputStream()); } catch (Exception e) {}
        closeQuietly(connection);
    }
    
    /**
     * Closes all streams of the connection, and disconnects it. Ignores all exceptions completely,
     * not even logging them!
     */
    public static void closeQuietly(HttpURLConnection connection) {
        try { connection.disconnect(); } catch (Exception e) {}
        try { connection.getInputStream().close(); } catch (Exception e) {}
        try { connection.getOutputStream().close(); } catch (Exception e) {}
        try { connection.getErrorStream().close(); } catch (Exception e) {}
    }
}
