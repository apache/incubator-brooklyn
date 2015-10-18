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
package org.apache.brooklyn.feed.http;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityInternal.FeedSupport;
import org.apache.brooklyn.core.feed.FeedConfig;
import org.apache.brooklyn.core.feed.PollConfig;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.http.BetterMockWebServer;
import org.apache.brooklyn.util.core.http.HttpToolResponse;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.SocketPolicy;

public class HttpFeedTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(HttpFeedTest.class);
    
    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor( "aLong", "");

    private static final long TIMEOUT_MS = 10*1000;
    
    private BetterMockWebServer server;
    private URL baseUrl;
    
    private Location loc;
    private EntityLocal entity;
    private HttpFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        server = BetterMockWebServer.newInstanceLocalhost();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse().setResponseCode(200).addHeader("content-type: application/json").setBody("{\"foo\":\"myfoo\"}"));
        }
        server.play();
        baseUrl = server.getUrl("/");

        loc = app.newLocalhostProvisioningLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        if (server != null) server.shutdown();
        feed = null;
        super.tearDown();
    }
    
    @Test
    public void testPollsAndParsesHttpGetResponse() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(HttpPollConfig.forSensor(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(HttpPollConfig.forSensor(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();
        
        assertSensorEventually(SENSOR_INT, (Integer)200, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, "{\"foo\":\"myfoo\"}", TIMEOUT_MS);
    }
    
    @Test
    public void testFeedDeDupe() throws Exception {
        testPollsAndParsesHttpGetResponse();
        entity.addFeed(feed);
        log.info("Feed 0 is: "+feed);
        
        testPollsAndParsesHttpGetResponse();
        log.info("Feed 1 is: "+feed);
        entity.addFeed(feed);
                
        FeedSupport feeds = ((EntityInternal)entity).feeds();
        Assert.assertEquals(feeds.getFeeds().size(), 1, "Wrong feed count: "+feeds.getFeeds());
    }
    
    @Test
    public void testSetsConnectionTimeout() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .connectionTimeout(Duration.TEN_SECONDS)
                        .socketTimeout(Duration.TEN_SECONDS)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .build();
        
        assertSensorEventually(SENSOR_INT, (Integer)200, TIMEOUT_MS);
    }
    
    // TODO How to cause the other end to just freeze (similar to aws-ec2 when securityGroup port is not open)?
    @Test
    public void testSetsConnectionTimeoutWhenServerDisconnects() throws Exception {
        if (server != null) server.shutdown();
        server = BetterMockWebServer.newInstanceLocalhost();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        }
        server.play();
        baseUrl = server.getUrl("/");

        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .connectionTimeout(Duration.TEN_SECONDS)
                        .socketTimeout(Duration.TEN_SECONDS)
                        .onSuccess(HttpValueFunctions.responseCode())
                        .onException(Functions.constant(-1)))
                .build();
        
        assertSensorEventually(SENSOR_INT, -1, TIMEOUT_MS);
    }
    
    
    @Test
    public void testPollsAndParsesHttpPostResponse() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .method("post")
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .method("post")
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();
        
        assertSensorEventually(SENSOR_INT, (Integer)200, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, "{\"foo\":\"myfoo\"}", TIMEOUT_MS);
    }

    @Test
    public void testUsesFailureHandlerOn4xx() throws Exception {
        server = BetterMockWebServer.newInstanceLocalhost();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("Unauthorised"));
        }
        server.play();
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(server.getUrl("/"))
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode())
                        .onFailure(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction())
                        .onFailure(Functions.constant("Failed")))
                .build();

        assertSensorEventually(SENSOR_INT, 401, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, "Failed", TIMEOUT_MS);

        server.shutdown();
    }

    @Test
    public void testUsesExceptionHandlerOn4xxAndNoFailureHandler() throws Exception {
        server = BetterMockWebServer.newInstanceLocalhost();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("Unauthorised"));
        }
        server.play();
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(server.getUrl("/"))
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode())
                        .onException(Functions.constant(-1)))
                .build();

        assertSensorEventually(SENSOR_INT, -1, TIMEOUT_MS);

        server.shutdown();
    }

    @Test(groups="Integration")
    // marked integration as it takes a wee while
    public void testSuspendResume() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();
        assertSensorEventually(SENSOR_INT, (Integer)200, TIMEOUT_MS);
        feed.suspend();
        final int countWhenSuspended = server.getRequestCount();
        
        Thread.sleep(500);
        if (server.getRequestCount() > countWhenSuspended+1)
            Assert.fail("Request count continued to increment while feed was suspended, from "+countWhenSuspended+" to "+server.getRequestCount());
        
        feed.resume();
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(server.getRequestCount() > countWhenSuspended + 1,
                        "Request count failed to increment when feed was resumed, from " + countWhenSuspended + ", still at " + server.getRequestCount());
            }
        });
    }

    @Test(groups="Integration")
    // marked integration as it takes a wee while
    public void testStartSuspended() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(HttpPollConfig.forSensor(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(HttpPollConfig.forSensor(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .suspended()
                .build();
        Asserts.continually(MutableMap.of("timeout", 500),
                Entities.attributeSupplier(entity, SENSOR_INT), Predicates.<Integer>equalTo(null));
        int countWhenSuspended = server.getRequestCount();
        feed.resume();
        Asserts.eventually(Entities.attributeSupplier(entity, SENSOR_INT), Predicates.<Integer>equalTo(200));
        if (server.getRequestCount() <= countWhenSuspended)
            Assert.fail("Request count failed to increment when feed was resumed, from "+countWhenSuspended+", still at "+server.getRequestCount());
        log.info("RUN: "+countWhenSuspended+" - "+server.getRequestCount());
    }


    @Test
    public void testPollsAndParsesHttpErrorResponseLocal() throws Exception {
        int unboundPort = Networking.nextAvailablePort(10000);
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri("http://localhost:" + unboundPort + "/path/should/not/exist")
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .onSuccess(Functions.constant("success"))
                        .onFailure(Functions.constant("failure"))
                        .onException(Functions.constant("error")))
                .build();
        
        assertSensorEventually(SENSOR_STRING, "error", TIMEOUT_MS);
    }

    @Test
    public void testPollsMulti() throws Exception {
        newMultiFeed(baseUrl);
        assertSensorEventually(SENSOR_INT, (Integer)200, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, "{\"foo\":\"myfoo\"}", TIMEOUT_MS);
    }

    // because takes a wee while
    @SuppressWarnings("rawtypes")
    @Test(groups="Integration")
    public void testPollsMultiClearsOnSubsequentFailure() throws Exception {
        server = BetterMockWebServer.newInstanceLocalhost();
        for (int i = 0; i < 10; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("Hello World"));
        }
        for (int i = 0; i < 10; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("Unauthorised"));
        }
        server.play();

        newMultiFeed(server.getUrl("/"));
        
        assertSensorEventually(SENSOR_INT, 200, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, "Hello World", TIMEOUT_MS);
        
        assertSensorEventually(SENSOR_INT, -1, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, null, TIMEOUT_MS);
        
        List<String> attrs = Lists.transform(MutableList.copyOf( ((EntityInternal)entity).getAllAttributes().keySet() ),
            new Function<AttributeSensor,String>() {
                @Override public String apply(AttributeSensor input) { return input.getName(); } });
        Assert.assertTrue(!attrs.contains(SENSOR_STRING.getName()), "attrs contained "+SENSOR_STRING);
        Assert.assertTrue(!attrs.contains(FeedConfig.NO_SENSOR.getName()), "attrs contained "+FeedConfig.NO_SENSOR);
        
        server.shutdown();
    }

    private void newMultiFeed(URL baseUrl) {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                
                .poll(HttpPollConfig.forMultiple()
                    .onSuccess(new Function<HttpToolResponse,Void>() {
                        public Void apply(HttpToolResponse response) {
                            entity.sensors().set(SENSOR_INT, response.getResponseCode());
                            if (response.getResponseCode()==200)
                                entity.sensors().set(SENSOR_STRING, response.getContentAsString());
                            return null;
                        }
                    })
                    .onFailureOrException(Functionals.function(EntityFunctions.settingSensorsConstant(entity, MutableMap.<AttributeSensor<?>,Object>of(
                        SENSOR_INT, -1, 
                        SENSOR_STRING, PollConfig.REMOVE))))
                .period(100))
                .build();
    }
    

    private <T> void assertSensorEventually(final AttributeSensor<T> sensor, final T expectedVal, long timeout) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", timeout), new Callable<Void>() {
            public Void call() {
                assertEquals(entity.getAttribute(sensor), expectedVal);
                return null;
            }
        });
    }
}
