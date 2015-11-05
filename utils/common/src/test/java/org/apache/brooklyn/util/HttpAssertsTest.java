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
package org.apache.brooklyn.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.brooklyn.test.http.TestHttpRequestHandler;
import org.apache.brooklyn.test.http.TestHttpServer;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.ResponseServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.*;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests on {@link HttpAsserts}.
 *
 * @todo Restructure this to avoid sleeps, according to conversation at
 * <a href="https://github.com/apache/incubator-brooklyn/pull/994#issuecomment-154074295">#994</a> on github.
 */
@Test(groups = "Integration")
public class HttpAssertsTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpAssertsTest.class);
    HttpClient httpClient;
    URI baseUri;
    private TestHttpServer server;
    private String baseUrl;
    private ScheduledExecutorService executor;


    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        server = initializeServer();
        baseUrl = server.getUrl();
        httpClient = HttpTool.httpClientBuilder()
            .uri(baseUrl)
            .build();
        baseUri = URI.create(baseUrl);
        executor = Executors.newScheduledThreadPool(3);
        TimeUnit.SECONDS.sleep(2);
    }

    private TestHttpServer initializeServer() {
        return new TestHttpServer()
            .interceptor(new ResponseServer())
            .interceptor(new ResponseBasicUnauthorized())
            .interceptor(new RequestBasicAuth())
            .handler("/simple", new TestHttpRequestHandler().response("OK"))
            .handler("/empty", new TestHttpRequestHandler().code(HttpStatus.SC_NO_CONTENT))
            .handler("/missing", new TestHttpRequestHandler().code(HttpStatus.SC_NOT_FOUND).response("Missing"))
            .handler("/redirect", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "/simple"))
            .handler("/cycle", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "/cycle"))
            .handler("/secure", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "https://0.0.0.0/"))
            .start();
    }


    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        server.stop();
        TimeUnit.SECONDS.sleep(2);
    }

    // schedule a stop of server after n seconds
    private void stopAfter(int delay) {
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                server.stop();
            }
        }, delay, TimeUnit.SECONDS);
    }

    // stop server and pause to wait for it to finish
    private void stopServer() {
        server.stop();
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }

    // schedule a start of server after n seconds
    private void startAfter(int delay) {
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                server = initializeServer();
            }
        }, delay, TimeUnit.SECONDS);
    }

    private HttpToolResponse doGet(String str) {
        final URI address = baseUri.resolve(str);
        return HttpTool.httpGet(httpClient, address,
            ImmutableMap.<String, String>of());
    }

    @Test
    public void shouldAssertHealthyStatusCode() {
        final HttpToolResponse response = doGet("/simple");
        HttpAsserts.assertHealthyStatusCode(response.getResponseCode());
    }

    @Test
    public void shouldAssertUrlReachable() {
        HttpAsserts.assertUrlReachable(baseUrl);
    }

    @Test
    public void shouldAssertUrlUnreachable() {
        stopServer();
        HttpAsserts.assertUrlUnreachable(testUri("/simple"));
    }

    @Test
    public void shouldAssertUrlUnreachableEventually() {
        HttpAsserts.assertUrlReachable(baseUrl);
        stopAfter(1);
        HttpAsserts.assertUrlUnreachableEventually(baseUrl);
    }

    @Test
    public void shouldAssertUrlUnreachableEventuallyWithFlags() throws Exception {
        stopAfter(5);
        TimeUnit.SECONDS.sleep(3);
        HttpAsserts.assertUrlReachable(baseUrl);
        HttpAsserts.assertUrlUnreachableEventually(ImmutableMap.of("timeout", "10s"), baseUrl);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void shouldAssertUrlUnreachableEventuallyWithTimeout() throws Exception {
        HttpAsserts.assertUrlReachable(baseUrl);
        HttpAsserts.assertUrlUnreachableEventually(ImmutableMap.of("timeout", "3s"), baseUrl);
    }


    @Test
    public void shouldAssertHttpStatusCodeEquals() {
        HttpAsserts.assertHttpStatusCodeEquals(baseUrl, 500, 501);
        HttpAsserts.assertHttpStatusCodeEquals(testUri("/simple"), 201, 200);
        HttpAsserts.assertHttpStatusCodeEquals(testUri("/missing"), 400, 404);
    }

    @Test
    public void shouldAssertHttpStatusCodeEventuallyEquals() throws Exception {
        stopServer();
        final String simple = testUri("/simple");
        HttpAsserts.assertUrlUnreachable(simple);
        startAfter(2);
        HttpAsserts.assertHttpStatusCodeEventuallyEquals(simple, 200);
    }

    private String testUri(String str) {
        return baseUri.resolve(str).toString();
    }

    @Test
    public void shouldAssertContentContainsText() {
        HttpAsserts.assertContentContainsText(testUri("/simple"), "OK");
    }

    @Test
    public void shouldAssertContentNotContainsText() {
        HttpAsserts.assertContentNotContainsText(testUri("/simple"), "Bad");
    }

    @Test
    public void shouldAssertErrorContentsContainsText() {
        HttpAsserts.assertErrorContentContainsText(testUri("/missing"), "Missing");
    }

    @Test
    public void shouldAssertErrorContentNotContainsText() {
        HttpAsserts.assertErrorContentNotContainsText(testUri("/missing"), "Bogus");
    }

    @Test
    public void shouldAssertContentEventuallyContainsText() {
        stopServer();
        final String simple = testUri("/simple");
        HttpAsserts.assertUrlUnreachable(simple);
        startAfter(2);
        HttpAsserts.assertContentEventuallyContainsText(simple, "OK");
    }

    @Test
    public void shouldAssertContentEventuallyContainsTextWithFlags() {
        stopServer();
        final String simple = testUri("/simple");
        HttpAsserts.assertUrlUnreachable(simple);
        startAfter(2);
        HttpAsserts.assertContentEventuallyContainsText(ImmutableMap.of("timeout", "3s"), simple, "OK");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void shouldAssertContentEventuallyContainsTextWithTimeout() {
        stopServer();
        final String simple = testUri("/simple");
        HttpAsserts.assertUrlUnreachable(simple);
        startAfter(4);
        HttpAsserts.assertContentEventuallyContainsText(ImmutableMap.of("timeout", "3s"), simple, "OK");
    }


    @Test
    public void shouldAssertContentMatches() {
        HttpAsserts.assertContentMatches(testUri("/simple"), "[Oo][Kk]");
    }

    @Test
    public void shouldAssertContentEventuallyMatches() throws Exception {
        stopServer();
        TimeUnit.SECONDS.sleep(2);
        final String simple = testUri("/simple");
        HttpAsserts.assertUrlUnreachable(simple);
        TimeUnit.SECONDS.sleep(2);
        startAfter(2);
        HttpAsserts.assertContentEventuallyMatches(testUri("/simple"), "[Oo][Kk]");
    }

    @Test
    public void shouldAssertContentEventuallyMatchesWithFlags() {
        stopServer();
        final String simple = testUri("/simple");
        HttpAsserts.assertUrlUnreachable(simple);
        startAfter(2);
        HttpAsserts.assertContentEventuallyMatches(ImmutableMap.of("timeout", "3s"), testUri("/simple"), "[Oo][Kk]");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void shouldAssertContentEventuallyMatchesWithTimeout() {
        stopServer();
        final String simple = testUri("/simple");
        HttpAsserts.assertUrlUnreachable(simple);
        startAfter(4);
        HttpAsserts.assertContentEventuallyMatches(ImmutableMap.of("timeout", "3s"), testUri("/simple"), "[Oo][Kk]");
    }

    @Test
    public void shouldAssertAsyncHttpStatusCodeContinuallyEquals() throws Exception {
        ListeningExecutorService listeningExecutor = MoreExecutors.listeningDecorator(executor);
        final ListenableFuture<?> future =
            HttpAsserts.assertAsyncHttpStatusCodeContinuallyEquals(listeningExecutor, testUri("/simple"), 200);
        TimeUnit.SECONDS.sleep(3);
        if (future.isDone()) {
            future.get(); // should not throw exception
        }
        future.cancel(true);
    }

    @Test(expectedExceptions = ExecutionException.class)
    public void shouldAssertAsyncHttpStatusCodeContinuallyEqualsFails() throws Exception {
        ListeningExecutorService listeningExecutor = MoreExecutors.listeningDecorator(executor);
        final ListenableFuture<?> future =
            HttpAsserts.assertAsyncHttpStatusCodeContinuallyEquals(listeningExecutor, testUri("/missing"), 200);
        TimeUnit.SECONDS.sleep(3);
        if (future.isDone()) {
            future.get(); // should throw exception
        }
        future.cancel(true);
    }
}
