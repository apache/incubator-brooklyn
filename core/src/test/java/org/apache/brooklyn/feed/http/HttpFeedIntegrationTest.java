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

import java.net.URI;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.HttpService;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;

public class HttpFeedIntegrationTest extends BrooklynAppUnitTestSupport {

    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor("aLong", "");

    private HttpService httpService;

    private Location loc;
    private EntityLocal entity;
    private HttpFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = new LocalhostMachineProvisioningLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        if (httpService != null) httpService.shutdown();
        super.tearDown();
    }

    @Test(groups = {"Integration"})
    public void testPollsAndParsesHttpGetResponseWithSsl() throws Exception {
        httpService = new HttpService(PortRanges.fromString("9000+"), true).start();
        URI baseUrl = new URI(httpService.getUrl());

        assertEquals(baseUrl.getScheme(), "https", "baseUrl="+baseUrl);
        
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 200);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Hello, World"), "val="+val);
            }});
    }

    @Test(groups = {"Integration"})
    public void testPollsAndParsesHttpGetResponseWithBasicAuthentication() throws Exception {
        final String username = "brooklyn";
        final String password = "hunter2";
        httpService = new HttpService(PortRanges.fromString("9000+"))
                .basicAuthentication(username, password)
                .start();
        URI baseUrl = new URI(httpService.getUrl());
        assertEquals(baseUrl.getScheme(), "http", "baseUrl="+baseUrl);

        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri(baseUrl)
                .credentials(username, password)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 200);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Hello, World"), "val="+val);
            }});
    }

    @Test(groups = {"Integration"})
    public void testPollWithInvalidCredentialsFails() throws Exception {
        httpService = new HttpService(PortRanges.fromString("9000+"))
                .basicAuthentication("brooklyn", "hunter2")
                .start();

        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri(httpService.getUrl())
                .credentials("brooklyn", "9876543210")
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode())
                        .onFailure(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction())
                        .onException(Functions.constant("Failed!")))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 401);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.equals("Failed!"), "val=" + val);
            }
        });
    }
}
