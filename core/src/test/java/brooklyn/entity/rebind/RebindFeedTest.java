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
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
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
import brooklyn.management.Task;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl.TestEntityWithoutEnrichers;
import brooklyn.util.http.BetterMockWebServer;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Callables;
import com.google.mockwebserver.MockResponse;

public class RebindFeedTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(RebindFeedTest.class);
    
    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor( "aLong", "");

    private BetterMockWebServer server;
    private URL baseUrl;
    
    final static Duration POLL_PERIOD = Duration.millis(20);
    final static Duration POLL_PERIOD_SSH = Duration.millis(500);
    
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
        super.tearDown();
        if (server != null) server.shutdown();
    }
    
    @Test
    public void testHttpFeedRegisteredInInitIsPersistedAndFeedsStop() throws Exception {
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntityWithHttpFeedImpl.class)
                .configure(MyEntityWithHttpFeedImpl.BASE_URL, baseUrl));
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_INT, (Integer)200);
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_STRING, "{\"foo\":\"myfoo\"}");
        assertEquals(origEntity.feeds().getFeeds().size(), 1);

        final long taskCountBefore = ((BasicExecutionManager)origManagementContext.getExecutionManager()).getNumIncompleteTasks();
        
        log.info("Tasks before rebind: "+
            ((BasicExecutionManager)origManagementContext.getExecutionManager()).getAllTasks());
        
        newApp = rebind(false);
        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.feeds().getFeeds();
        assertEquals(newFeeds.size(), 1);
        
        // Expect the feed to still be polling
        newEntity.setAttribute(SENSOR_INT, null);
        newEntity.setAttribute(SENSOR_STRING, null);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_INT, (Integer)200);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_STRING, "{\"foo\":\"myfoo\"}");
        
        // Now test that everything in the origApp stops, including feeds
        Entities.unmanage(origApp);
        origApp = null;
        origManagementContext.getRebindManager().stop();
        Repeater.create().every(Duration.millis(20)).limitTimeTo(Duration.TEN_SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                origManagementContext.getGarbageCollector().gcIteration();
                long taskCountAfterAtOld = ((BasicExecutionManager)origManagementContext.getExecutionManager()).getNumIncompleteTasks();
                List<Task<?>> tasks = ((BasicExecutionManager)origManagementContext.getExecutionManager()).getAllTasks();
                int unendedTasks = 0;
                for (Task<?> t: tasks) {
                    if (!t.isDone()) unendedTasks++;
                }
                log.info("Incomplete task count from "+taskCountBefore+" to "+taskCountAfterAtOld+", "+unendedTasks+" unended; tasks remembered are: "+
                    tasks);
                return taskCountAfterAtOld==0;
            }
        }).runRequiringTrue();
    }

    @Test(groups="Integration", invocationCount=50)
    public void testHttpFeedRegisteredInInitIsPersistedAndFeedsStopManyTimes() throws Exception {
        testHttpFeedRegisteredInInitIsPersistedAndFeedsStop();
    }
    
    @Test
    public void testFunctionFeedRegisteredInInitIsPersisted() throws Exception {
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntityWithFunctionFeedImpl.class));
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_INT, (Integer)1);
        assertEquals(origEntity.feeds().getFeeds().size(), 2);

        newApp = rebind(false);
        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.feeds().getFeeds();
        assertEquals(newFeeds.size(), 2);
        
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
        assertEquals(origEntity.feeds().getFeeds().size(), 1);

        newApp = rebind(false);
        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.feeds().getFeeds();
        assertEquals(newFeeds.size(), 1);
        
        // Expect the feed to still be polling
        newEntity.setAttribute(SENSOR_INT, null);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_INT, (Integer)0);
    }

    public static class MyEntityWithHttpFeedImpl extends TestEntityWithoutEnrichers {
        public static final ConfigKey<URL> BASE_URL = ConfigKeys.newConfigKey(URL.class, "rebindFeedTest.baseUrl");
        
        @Override
        public void init() {
            super.init();
            addFeed(HttpFeed.builder()
                    .entity(this)
                    .baseUrl(getConfig(BASE_URL))
                    .poll(HttpPollConfig.forSensor(SENSOR_INT)
                            .period(POLL_PERIOD)
                            .onSuccess(HttpValueFunctions.responseCode()))
                    .poll(HttpPollConfig.forSensor(SENSOR_STRING)
                            .period(POLL_PERIOD)
                            .onSuccess(HttpValueFunctions.stringContentsFunction()))
                    .build());
        }
    }
    
    public static class MyEntityWithFunctionFeedImpl extends TestEntityWithoutEnrichers {
        @Override
        public void init() {
            super.init();
            addFeed(FunctionFeed.builder()
                    .entity(this)
                    .poll(FunctionPollConfig.forSensor(SENSOR_INT)
                            .period(POLL_PERIOD)
                            .callable(Callables.returning(1)))
                    .build());
            addFeed(FunctionFeed.builder()
                    .entity(this)
                    .poll(FunctionPollConfig.forSensor(SENSOR_STRING)
                            .period(POLL_PERIOD)
                            .callable(Callables.returning("OK")))
                    .build());
        }
    }
    
    public static class MyEntityWithSshFeedImpl extends TestEntityWithoutEnrichers {
        @Override
        public void start(Collection<? extends Location> locs) {
            super.start(locs);
            addFeed(SshFeed.builder()
                    .entity(this)
                    .poll(new SshPollConfig<Integer>(SENSOR_INT)
                        .command("true")
                        .period(POLL_PERIOD_SSH)
                        .onSuccess(SshValueFunctions.exitStatus()))
                    .build());
        }
    }
}
