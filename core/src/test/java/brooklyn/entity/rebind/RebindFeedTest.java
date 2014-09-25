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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.net.URL;
import java.util.Collection;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshValueFunctions;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.http.BetterMockWebServer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Callables;
import com.google.mockwebserver.MockResponse;

public class RebindFeedTest extends RebindTestFixtureWithApp {

    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor( "aLong", "");

    private BetterMockWebServer server;
    private URL baseUrl;
    
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
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (server != null) server.shutdown();
        super.tearDown();
    }
    
    @Test
    public void testHttpFeedRegisteredInInitIsPersisted() throws Exception {
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntityWithHttpFeedImpl.class)
                .configure(MyEntityWithHttpFeedImpl.BASE_URL, baseUrl));
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_INT, (Integer)200);
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_STRING, "{\"foo\":\"myfoo\"}");
        assertEquals(origEntity.getFeedSupport().getFeeds().size(), 1);

        newApp = rebind(false);
        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.getFeedSupport().getFeeds();
        assertEquals(newFeeds.size(), 1);
        
        // Expect the feed to still be polling
        newEntity.setAttribute(SENSOR_INT, null);
        newEntity.setAttribute(SENSOR_STRING, null);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_INT, (Integer)200);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_STRING, "{\"foo\":\"myfoo\"}");
    }

    @Test
    public void testFunctionFeedRegisteredInInitIsPersisted() throws Exception {
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntityWithFunctionFeedImpl.class));
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_INT, (Integer)1);
        assertEquals(origEntity.getFeedSupport().getFeeds().size(), 1);

        newApp = rebind(false);
        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.getFeedSupport().getFeeds();
        assertEquals(newFeeds.size(), 1);
        
        // Expect the feed to still be polling
        newEntity.setAttribute(SENSOR_INT, null);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_INT, (Integer)1);
    }
    
    @Test(groups="Integration")
    public void testSshFeedRegisteredInStartIsPersisted() throws Exception {
        LocalhostMachineProvisioningLocation origLoc = origApp.newLocalhostProvisioningLocation();
        SshMachineLocation origMachine = origLoc.obtain();

        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntityWithSshFeedImpl.class)
                .location(origMachine));
        
        origApp.start(ImmutableList.<Location>of());

        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_INT, (Integer)0);
        assertEquals(origEntity.getFeedSupport().getFeeds().size(), 1);

        newApp = rebind(false);
        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.getFeedSupport().getFeeds();
        assertEquals(newFeeds.size(), 1);
        
        // Expect the feed to still be polling
        newEntity.setAttribute(SENSOR_INT, null);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_INT, (Integer)0);
    }

    public static class MyEntityWithHttpFeedImpl extends TestEntityImpl {
        public static final ConfigKey<URL> BASE_URL = ConfigKeys.newConfigKey(URL.class, "rebindFeedTest.baseUrl");
        
        @Override
        public void init() {
            super.init();
            addFeed(HttpFeed.builder()
                    .entity(this)
                    .baseUrl(getConfig(BASE_URL))
                    .poll(HttpPollConfig.forSensor(SENSOR_INT)
                            .period(100)
                            .onSuccess(HttpValueFunctions.responseCode()))
                    .poll(HttpPollConfig.forSensor(SENSOR_STRING)
                            .period(100)
                            .onSuccess(HttpValueFunctions.stringContentsFunction()))
                    .build());
        }
    }
    
    public static class MyEntityWithFunctionFeedImpl extends TestEntityImpl {
        @Override
        public void init() {
            super.init();
            addFeed(FunctionFeed.builder()
                    .entity(this)
                    .poll(FunctionPollConfig.forSensor(SENSOR_INT)
                            .period(100)
                            .callable(Callables.returning(1)))
                    .build());
        }
    }
    
    public static class MyEntityWithSshFeedImpl extends TestEntityImpl {
        @Override
        public void start(Collection<? extends Location> locs) {
            super.start(locs);
            addFeed(SshFeed.builder()
                    .entity(this)
                    .poll(new SshPollConfig<Integer>(SENSOR_INT)
                        .command("true")
                        .onSuccess(SshValueFunctions.exitStatus()))
                    .build());
        }
    }
}
