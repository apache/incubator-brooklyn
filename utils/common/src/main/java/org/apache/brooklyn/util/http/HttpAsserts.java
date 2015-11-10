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
package org.apache.brooklyn.util.http;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * Utility assertions on HTTP.
 * 
 * @author aled
 */
public class HttpAsserts {

    private static final Logger LOG = LoggerFactory.getLogger(HttpAsserts.class);

    /** @return whether the given HTTP status code is a "success" class code (2xx) */
    public static boolean isHealthyStatusCode(int code) {
        return code>=200 && code<=299;
    }
    
    /** Asserts that the given HTTP status code indicates "success", i.e. {@link #isHealthyStatusCode(int)} is true */
    public static void assertHealthyStatusCode(int code) {
        if (isHealthyStatusCode(code)) return;
        Asserts.fail("Expected success status code, got: " + code);
    }

    /** Asserts that the given HTTP status code does not indicate "success", i.e. {@link #isHealthyStatusCode(int)} returns false */
    public static void assertNotHealthyStatusCode(int code) {
        if (!isHealthyStatusCode(code)) return;
        Asserts.fail("Expected non-success status code, got: " + code);
    }

    /** @return whether the given HTTP status code is a "client error" class code (4xx) */
    public static boolean isClientErrorStatusCode(int code) {
        return code>=400 && code<=499;
    }
    
    /** Asserts that the given HTTP status code indicates "client error", i.e. {@link #isClientErrorStatusCode(int)} is true */
    public static void assertClientErrorStatusCode(int code) {
        if (isClientErrorStatusCode(code)) return;
        Asserts.fail("Expected client error status code, got: " + code);        
    }

    /** @return whether the given HTTP status code is a "server error" class code (5xx) */
    public static boolean isServerErrorStatusCode(int code) {
        return code>=500 && code<=599;
    }
    
    /** Asserts that the given HTTP status code indicates "server error", i.e. {@link #isServerErrorStatusCode(int)} is true */
    public static void assertServerErrorStatusCode(int code) {
        if (isServerErrorStatusCode(code)) return;
        Asserts.fail("Expected server error status code, got: " + code);        
    }

    /**
     * Asserts that gets back any "valid" response - i.e. not an exception. This could be an unauthorized,
     * a redirect, a 404, or anything else that implies there is web-server listening on that port.
     *
     * @param url The URL to connect to.
     */
    public static void assertUrlReachable(String url) {
        try {
            HttpTool.getHttpStatusCode(url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted for "+url+" (in assertion that is reachable)", e);
        } catch (Exception e) {
            throw new IllegalStateException("Server at "+url+" Asserts.failed to respond (in assertion that is reachable): "+e, e);
        }
    }

    /**
     * Asserts that the URL could not be reached, detected as an IOException.
     *
     * @param url The URL to connect to.
     */

    public static void assertUrlUnreachable(String url) {
        try {
            int statusCode = HttpTool.getHttpStatusCode(url);
            Asserts.fail("Expected url " + url + " unreachable, but got status code " + statusCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted for "+url+" (in assertion that unreachable)", e);
        } catch (Exception e) {
            IOException cause = Exceptions.getFirstThrowableOfType(e, IOException.class);
            if (cause != null) {
                // success; clean shutdown transitioning from 400 to error
            } else {
                propagateAsAssertionError(e);
            }                        
        }
    }

    /**
     * Asserts that the URL becomes unreachable within a default time period.
     *
     * @param url The URL
     */
    public static void assertUrlUnreachableEventually(final String url) {
        assertUrlUnreachableEventually(Maps.<String,Object>newLinkedHashMap(), url);
    }


    /**
     * Asserts that the URL becomes unreachable within a configurable time period.
     *
     * @param flags The flags controlling the timeout.
     *              For details see {@link org.apache.brooklyn.test.Asserts#succeedsEventually(java.util.Map, java.util.concurrent.Callable)}
     * @param url The URL
     */
    public static void assertUrlUnreachableEventually(Map<String,?> flags, final String url) {
        assertEventually(flags, new Runnable() {
            public void run() {
                assertUrlUnreachable(url);
            }
        });
    }

    /**
     * Assert that the status code returned from the URL is in the given codes.
     *
     * @param url The URL to get.
     * @param acceptableReturnCodes The return codes that are expected.
     */
    public static void assertHttpStatusCodeEquals(String url, int... acceptableReturnCodes) {
        List<Integer> acceptableCodes = Lists.newArrayList();
        for (int code : acceptableReturnCodes) {
            acceptableCodes.add((Integer)code);
        }
        try {
            int actualCode = HttpTool.getHttpStatusCode(url);
            Asserts.assertTrue(acceptableCodes.contains(actualCode), "code=" + actualCode + "; expected=" + acceptableCodes + "; url=" + url);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted for "+url+" (in assertion that result code is "+acceptableCodes+")", e);
        } catch (Exception e) {
            throw new IllegalStateException("Server at "+url+" Asserts.failed to respond (in assertion that result code is "+acceptableCodes+"): "+e, e);
        }
    }

    public static void assertHttpStatusCodeEventuallyEquals(final String url, final int expectedCode) {
        assertHttpStatusCodeEventuallyEquals(Maps.<String,Object>newLinkedHashMap(), url, expectedCode);
    }

    public static void assertHttpStatusCodeEventuallyEquals(Map<String,?> flags, final String url, final int expectedCode) {
        assertEventually(flags, new Runnable() {
            public void run() {
                assertHttpStatusCodeEquals(url, expectedCode);
            }
        });
    }

    public static void assertContentContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        try {
            String contents = HttpTool.getContent(url);
            Asserts.assertTrue(contents != null && contents.length() > 0);
            for (String text: Lists.asList(phrase, additionalPhrases)) {
                if (!contents.contains(text)) {
                    LOG.warn("CONTENTS OF URL "+url+" MISSING TEXT: "+text+"\n"+contents);
                    Asserts.fail("URL "+url+" does not contain text: "+text);
                }
            }
        } catch (Exception e) {
            throw propagateAsAssertionError(e);
        }
    }

    public static void assertContentNotContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        try {
            String contents = HttpTool.getContent(url);
            Asserts.assertTrue(contents != null);
            for (String text: Lists.asList(phrase, additionalPhrases)) {
                if (contents.contains(text)) {
                    LOG.warn("CONTENTS OF URL "+url+" HAS TEXT: "+text+"\n"+contents);
                    Asserts.fail("URL "+url+" contain text: "+text);
                }
            }
        } catch (Exception e) {
            throw propagateAsAssertionError(e);
        }
    }

    public static void assertErrorContentContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        try {
            String contents = HttpTool.getErrorContent(url);
            Asserts.assertTrue(contents != null && contents.length() > 0);
            for (String text: Lists.asList(phrase, additionalPhrases)) {
                if (!contents.contains(text)) {
                    LOG.warn("CONTENTS OF URL "+url+" MISSING TEXT: "+text+"\n"+contents);
                    Asserts.fail("URL "+url+" does not contain text: "+text);
                }
            }
        } catch (Exception e) {
            throw propagateAsAssertionError(e);
        }
    }


    public static void assertErrorContentNotContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        try {
            String err = HttpTool.getErrorContent(url);
            Asserts.assertTrue(err != null);
            for (String text: Lists.asList(phrase, additionalPhrases)) {
                if (err.contains(text)) {
                    LOG.warn("CONTENTS OF URL "+url+" HAS TEXT: "+text+"\n"+err);
                    Asserts.fail("URL "+url+" contain text: "+text);
                }
            }
        } catch (Exception e) {
            throw propagateAsAssertionError(e);
        }
    }

    private static AssertionError propagateAsAssertionError(Exception e) {
        final AssertionError assertionError = new AssertionError("Assertion failed", e);
        return assertionError;
    }

    public static void assertContentEventuallyContainsText(final String url, final String phrase, final String ...additionalPhrases) {
        assertContentEventuallyContainsText(MutableMap.<String,Object>of(), url, phrase, additionalPhrases);
    }
    
    public static void assertContentEventuallyContainsText(Map<String,?> flags, final String url, final String phrase, final String ...additionalPhrases) {
        assertEventually(flags, new Runnable() {
            public void run() {
                assertContentContainsText(url, phrase, additionalPhrases);
            }
        });
    }

    private static void assertEventually(Map<String,?> flags, Runnable r) {
        try {
            Asserts.succeedsEventually(flags, r);
        } catch (Exception e) {
            throw propagateAsAssertionError(e);
        }
    }

    private static void assertEventually(Runnable r) {
        try {
            Asserts.succeedsEventually(r);
        } catch (Exception e) {
            throw propagateAsAssertionError(e);
        }
    }

    public static void assertContentMatches(String url, String regex) {
        String contents = HttpTool.getContent(url);
        Asserts.assertNotNull(contents);
        Asserts.assertTrue(contents.matches(regex), "Contents does not match expected regex ("+regex+"): "+contents);
    }

    public static void assertContentEventuallyMatches(final String url, final String regex) {
        assertEventually(new Runnable() {
            @Override
            public void run() {
                assertContentMatches(url, regex);
            }
        });
    }

    public static void assertContentEventuallyMatches(Map<String,?> flags, final String url, final String regex) {
        assertEventually(flags, new Runnable() {
            @Override
            public void run() {
                assertContentMatches(url, regex);
            }
        });
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
     * if (future.isDone()) future.get(); // get exception if its Asserts.failed
     * }
     * </pre>
     *
     * NOTE that the exception thrown by future.get() is a java.util.concurrent.ExecutionException,
     * not an AssertionError.
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

}
