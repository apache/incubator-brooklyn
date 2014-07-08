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
package brooklyn.event.feed.http;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.FeedConfig;
import brooklyn.event.feed.PollConfig;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.http.BetterMockWebServer;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.time.Duration;

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


    @Test(groups="Integration")
    /** marked as integration so it doesn't fail the plain build in environments
     * with dodgy DNS (ie where "unresolvable_hostname_or_one_with_no_webserver_on_port_80" resolves as a host run by the provider)
     * <p>
     * (a surprising number of ISP's do this,
     * happily serving adverts for your ISP, yielding "success" here,
     * or timing out, giving null here)
     * <p>
     * if you want to make this test work, you can e.g. set it to loopback IP assuming you don't have any servers on port 80,
     * with the following in /etc/hosts
     * <p>  
     * 127.0.0.1  unresolvable_hostname_or_one_with_no_webserver_on_port_80
    // or some other IP which won't resolve
     */
    public void testPollsAndParsesHttpErrorResponseWild() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri("http://unresolvable_hostname_or_one_with_no_webserver_on_port_80")
                .poll(HttpPollConfig.forSensor(SENSOR_STRING)
                        .onSuccess(Functions.constant("success"))
                        .onFailure(Functions.constant("failure"))
                        .onException(Functions.constant("error")))
                .build();
        
        assertSensorEventually(SENSOR_STRING, "error", TIMEOUT_MS);
    }
    
    @Test
    public void testPollsAndParsesHttpErrorResponseLocal() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                // combo of port 46069 and unknown path will hopefully give an error
                // (without the port, in jenkins it returns some bogus success page)
                .baseUri("http://localhost:46069/path/should/not/exist")
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .onSuccess(Functions.constant("success"))
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
                            entity.setAttribute(SENSOR_INT, response.getResponseCode());
                            if (response.getResponseCode()==200)
                                entity.setAttribute(SENSOR_STRING, response.getContentAsString());
                            return null;
                        }
                    })
                    .onFailureOrException(EntityFunctions.settingSensorsConstantFunction(entity, MutableMap.<AttributeSensor<?>,Object>of(
                        SENSOR_INT, -1, 
                        SENSOR_STRING, PollConfig.REMOVE)))
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
