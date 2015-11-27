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

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.test.http.TestHttpRequestHandler;
import org.apache.brooklyn.test.http.TestHttpServer;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.ResponseServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Tests on {@link HttpAsserts}.
 *
 * @todo Restructure this to avoid sleeps, according to conversation at
 * <a href="https://github.com/apache/incubator-brooklyn/pull/994#issuecomment-154074295">#994</a> on github.
 */
@Test(groups = "Integration")
public class HttpAssertsTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpAssertsTest.class);
    private static Duration DELAY_FOR_SERVER_TO_SETTLE = Duration.seconds(2);
    
    HttpClient httpClient;
    URI baseUri;
    private TestHttpServer server;
    private String baseUrl;
    private String simpleEndpoint;
    private ScheduledExecutorService executor;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        server = initializeServer();
        initVars();
        Time.sleep(DELAY_FOR_SERVER_TO_SETTLE);
    }

    private void initVars() {
        baseUrl = server.getUrl();
        httpClient = HttpTool.httpClientBuilder()
            .uri(baseUrl)
            .build();
        baseUri = URI.create(baseUrl);
        simpleEndpoint = testUri("/simple");
        executor = Executors.newScheduledThreadPool(3);
    }

    private TestHttpServer initializeServerUnstarted() {
        return new TestHttpServer()
            .interceptor(new ResponseServer())
            .interceptor(new ResponseBasicUnauthorized())
            .interceptor(new RequestBasicAuth())
            .handler("/simple", new TestHttpRequestHandler().response("OK"))
            .handler("/empty", new TestHttpRequestHandler().code(HttpStatus.SC_NO_CONTENT))
            .handler("/missing", new TestHttpRequestHandler().code(HttpStatus.SC_NOT_FOUND).response("Missing"))
            .handler("/redirect", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "/simple"))
            .handler("/cycle", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "/cycle"))
            .handler("/secure", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "https://0.0.0.0/"));
    }
    private TestHttpServer initializeServer() {
            return initializeServerUnstarted().start();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        server.stop();
        Time.sleep(DELAY_FOR_SERVER_TO_SETTLE);
    }

    // schedule a stop of server after n seconds
    private void stopAfter(final Duration time) {
        final TestHttpServer serverCached = server;
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                LOG.info("stopping server ("+time+" elapsed)");
                serverCached.stop();
            }
        }, time.toMilliseconds(), TimeUnit.MILLISECONDS);
    }

    // stop server and pause to wait for it to finish
    private void stopServer() {
        server.stop();
        Time.sleep(DELAY_FOR_SERVER_TO_SETTLE);
    }

    // schedule a start of server after n seconds
    private void startAfter(final Duration time) {
        // find the port before delay so that callers can get the right url
        // (sometimes if stopped and started it can't bind to the same port;
        // at least that is one suspicion for failures on hosted jenkins)
        server = initializeServerUnstarted();
        server.basePort(Networking.nextAvailablePort(50606));
        initVars();
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                LOG.info("starting server ("+time+" elapsed)");
                server.start();
            }
        }, time.toMilliseconds(), TimeUnit.MILLISECONDS);
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
        HttpAsserts.assertUrlUnreachable(simpleEndpoint);
    }

    @Test
    public void shouldAssertUrlUnreachableEventually() {
        HttpAsserts.assertUrlReachable(baseUrl);
        stopAfter(Duration.seconds(1));
        HttpAsserts.assertUrlUnreachableEventually(baseUrl);
    }

    @Test
    public void shouldAssertUrlUnreachableEventuallyWithFlags() throws Exception {
        String baseUrlOrig = baseUrl;
        LOG.info("testing "+JavaClassNames.niceClassAndMethod()+", server "+server.getUrl());
        stopAfter(DELAY_FOR_SERVER_TO_SETTLE);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE.add(DELAY_FOR_SERVER_TO_SETTLE).add(DELAY_FOR_SERVER_TO_SETTLE));
        LOG.info(JavaClassNames.niceClassAndMethod()+" queued server changes");
        HttpAsserts.assertUrlReachable(baseUrlOrig);
        HttpAsserts.assertUrlUnreachableEventually(ImmutableMap.of("timeout", "10s"), baseUrlOrig);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void shouldFailAssertUrlUnreachableEventuallyWithTimeout() throws Exception {
        HttpAsserts.assertUrlReachable(baseUrl);
        HttpAsserts.assertUrlUnreachableEventually(ImmutableMap.of("timeout", "3s"), baseUrl);
    }

    @Test
    public void shouldAssertHttpStatusCodeEquals() {
        HttpAsserts.assertHttpStatusCodeEquals(baseUrl, 500, 501);
        HttpAsserts.assertHttpStatusCodeEquals(simpleEndpoint, 201, 200);
        HttpAsserts.assertHttpStatusCodeEquals(testUri("/missing"), 400, 404);
    }

    @Test
    public void shouldAssertHttpStatusCodeEventuallyEquals() throws Exception {
        stopServer();
        HttpAsserts.assertUrlUnreachable(simpleEndpoint);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE);
        try {
            HttpAsserts.assertHttpStatusCodeEventuallyEquals(simpleEndpoint, 200);
        } catch (Throwable t) {
            LOG.warn("Failed waiting for simple with start after: "+t, t);
            LOG.warn("Detail: server at "+server.getUrl()+" ("+server+"), looking at "+simpleEndpoint);
            throw Exceptions.propagate(t);
        }
    }

    private String testUri(String str) {
        return baseUri.resolve(str).toString();
    }

    @Test
    public void shouldAssertContentContainsText() {
        HttpAsserts.assertContentContainsText(simpleEndpoint, "OK");
    }

    @Test
    public void shouldAssertContentNotContainsText() {
        HttpAsserts.assertContentNotContainsText(simpleEndpoint, "Bad");
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
        HttpAsserts.assertUrlUnreachable(simpleEndpoint);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE);
        HttpAsserts.assertContentEventuallyContainsText(simpleEndpoint, "OK");
    }

    @Test
    public void shouldAssertContentEventuallyContainsTextWithFlags() {
        stopServer();
        HttpAsserts.assertUrlUnreachable(simpleEndpoint);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE);
        HttpAsserts.assertContentEventuallyContainsText(ImmutableMap.of("timeout", 
            DELAY_FOR_SERVER_TO_SETTLE.add(Duration.ONE_SECOND).toStringRounded()), 
            simpleEndpoint, "OK");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void shouldAssertContentEventuallyContainsTextWithTimeout() {
        stopServer();
        HttpAsserts.assertUrlUnreachable(simpleEndpoint);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE.add(Duration.seconds(2)));
        HttpAsserts.assertContentEventuallyContainsText(ImmutableMap.of("timeout", 
            DELAY_FOR_SERVER_TO_SETTLE.add(Duration.ONE_SECOND).toStringRounded()), 
            simpleEndpoint, "OK");
    }


    @Test
    public void shouldAssertContentMatches() {
        HttpAsserts.assertContentMatches(simpleEndpoint, "[Oo][Kk]");
    }

    @Test
    public void shouldAssertContentEventuallyMatches() throws Exception {
        stopServer();
        Time.sleep(DELAY_FOR_SERVER_TO_SETTLE);
        HttpAsserts.assertUrlUnreachable(simpleEndpoint);
        Time.sleep(DELAY_FOR_SERVER_TO_SETTLE);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE);
        HttpAsserts.assertContentEventuallyMatches(simpleEndpoint, "[Oo][Kk]");
    }

    @Test
    public void shouldAssertContentEventuallyMatchesWithFlags() {
        stopServer();
        HttpAsserts.assertUrlUnreachable(simpleEndpoint);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE);
        HttpAsserts.assertContentEventuallyMatches(ImmutableMap.of("timeout", "3s"), simpleEndpoint, "[Oo][Kk]");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void shouldAssertContentEventuallyMatchesWithTimeout() {
        stopServer();
        HttpAsserts.assertUrlUnreachable(simpleEndpoint);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE.add(Duration.seconds(2)));
        HttpAsserts.assertContentEventuallyMatches(ImmutableMap.of("timeout", 
            DELAY_FOR_SERVER_TO_SETTLE.add(Duration.ONE_SECOND).toStringRounded()), 
            simpleEndpoint, "[Oo][Kk]");
    }

    @Test
    public void shouldAssertAsyncHttpStatusCodeContinuallyEquals() throws Exception {
        stopServer();
        ListeningExecutorService listeningExecutor = MoreExecutors.listeningDecorator(executor);
        final ListenableFuture<?> future =
            HttpAsserts.assertAsyncHttpStatusCodeContinuallyEquals(listeningExecutor, simpleEndpoint, 200);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE.add(Duration.seconds(1)));
        if (future.isDone()) {
            future.get(); // should not throw exception
        }
        future.cancel(true);
    }

    @Test(expectedExceptions = ExecutionException.class)
    public void shouldAssertAsyncHttpStatusCodeContinuallyEqualsFails() throws Exception {
        stopServer();
        ListeningExecutorService listeningExecutor = MoreExecutors.listeningDecorator(executor);
        final ListenableFuture<?> future =
            HttpAsserts.assertAsyncHttpStatusCodeContinuallyEquals(listeningExecutor, testUri("/missing"), 200);
        startAfter(DELAY_FOR_SERVER_TO_SETTLE.add(Duration.seconds(1)));
        Time.sleep(DELAY_FOR_SERVER_TO_SETTLE);
        if (future.isDone()) {
            Object result = future.get(); // should throw exception
            LOG.warn("Should have failed, instead gave "+result+" (accessing "+server+")");
        } else {
            LOG.warn("Future should have been done");
        }
        future.cancel(true);
    }
}
