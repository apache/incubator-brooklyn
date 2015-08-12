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

import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.entity.TestEntity;
import org.apache.brooklyn.test.entity.TestEntityImpl.TestEntityWithoutEnrichers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
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
import brooklyn.management.internal.BrooklynGarbageCollector;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableList;
import brooklyn.util.http.BetterMockWebServer;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Callables;
import com.google.mockwebserver.MockResponse;

public class RebindFeedTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(RebindFeedTest.class);
    
    public final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    public final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor( "aLong", "");

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
        log.info("Count of incomplete tasks before "+taskCountBefore);
        
        log.info("Tasks before rebind: "+
            ((BasicExecutionManager)origManagementContext.getExecutionManager()).getAllTasks());
        
        newApp = rebind();
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
        
        waitForTaskCountToBecome(origManagementContext, 0);
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

        newApp = rebind();
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

        newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.feeds().getFeeds();
        assertEquals(newFeeds.size(), 1);
        
        // Expect the feed to still be polling
        newEntity.setAttribute(SENSOR_INT, null);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_INT, (Integer)0);
    }

    @Test
    public void testReRebindDedupesCorrectlyBasedOnTagOrContentsPersisted() throws Exception {
        doReReReRebindDedupesCorrectlyBasedOnTagOrContentsPersisted(-1, 2, false);
    }
    
    @Test(groups="Integration")
    public void testReReReReRebindDedupesCorrectlyBasedOnTagOrContentsPersisted() throws Exception {
        doReReReRebindDedupesCorrectlyBasedOnTagOrContentsPersisted(1000*1000, 50, true);
    }
    
    public void doReReReRebindDedupesCorrectlyBasedOnTagOrContentsPersisted(int datasize, int iterations, boolean soakTest) throws Exception {
        final int SYSTEM_TASK_COUNT = 2;  // normally 1, persistence; but as long as less than 4 (the original) we're fine
        final int MAX_ALLOWED_LEAK = 50*1000*1000;  // memory can vary wildly; but our test should eventually hit GB if there's a leak so this is fine
        
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntityWithNewFeedsEachTimeImpl.class)
            .configure(MyEntityWithNewFeedsEachTimeImpl.DATA_SIZE, datasize)
            .configure(MyEntityWithNewFeedsEachTimeImpl.MAKE_NEW, true));
        origApp.start(ImmutableList.<Location>of());

        List<Feed> knownFeeds = MutableList.of();
        TestEntity currentEntity = origEntity;
        Collection<Feed> currentFeeds = currentEntity.feeds().getFeeds();
        
        int expectedCount = 4;
        assertEquals(currentFeeds.size(), expectedCount);
        knownFeeds.addAll(currentFeeds);
        assertActiveFeedsEventually(knownFeeds, expectedCount);
        origEntity.config().set(MyEntityWithNewFeedsEachTimeImpl.MAKE_NEW, !soakTest);
        
        long usedOriginally = -1;
        
        for (int i=0; i<iterations; i++) {
            log.info("rebinding, iteration "+i);
            newApp = rebind();
            
            // should get 2 new ones
            if (!soakTest) expectedCount += 2;
            
            currentEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
            currentFeeds = currentEntity.feeds().getFeeds();
            assertEquals(currentFeeds.size(), expectedCount, "feeds are: "+currentFeeds);
            knownFeeds.addAll(currentFeeds);

            switchOriginalToNewManagementContext();
            waitForTaskCountToBecome(origManagementContext, expectedCount + SYSTEM_TASK_COUNT);
            assertActiveFeedsEventually(knownFeeds, expectedCount);
            knownFeeds.clear();
            knownFeeds.addAll(currentFeeds);
            
            if (soakTest) {
                System.gc(); System.gc();
                if (usedOriginally<0) {
                    Time.sleep(Duration.millis(200));  // give things time to settle; means this number should be larger than others, if anything
                    usedOriginally = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    log.info("Usage after first rebind: "+BrooklynGarbageCollector.makeBasicUsageString()+" ("+Strings.makeJavaSizeString(usedOriginally)+")");
                } else {
                    long usedNow = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    log.info("Usage: "+BrooklynGarbageCollector.makeBasicUsageString()+" ("+Strings.makeJavaSizeString(usedNow)+")");
                    Assert.assertFalse(usedNow-usedOriginally > MAX_ALLOWED_LEAK, "Leaked too much memory: "+Strings.makeJavaSizeString(usedNow)+" now used, was "+Strings.makeJavaSizeString(usedOriginally));
                }
            }
        }
    }

    // Feeds take a while to start, also they do it asynchronously from the rebind. Wait for them to catch up.
    private void assertActiveFeedsEventually(List<Feed> knownFeeds, int expectedCount) {
        Asserts.eventually(new CountActiveSupplier(knownFeeds), Predicates.equalTo(expectedCount));
    }
    
    private static class CountActiveSupplier implements Supplier<Integer> {
        private List<Feed> knownFeeds;

        public CountActiveSupplier(List<Feed> knownFeeds) {
            this.knownFeeds = knownFeeds;
        }

        @Override
        public Integer get() {
            return countActive(knownFeeds);
        }
        
        private int countActive(List<Feed> knownFeeds) {
            int activeCount=0;
            for (Feed f: knownFeeds) {
                if (f.isRunning()) activeCount++;
            }
            return activeCount;
        }
        
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
    
    public static class MyEntityWithNewFeedsEachTimeImpl extends TestEntityWithoutEnrichers {
        public static final ConfigKey<Integer> DATA_SIZE = ConfigKeys.newIntegerConfigKey("datasize", "size of data", -1);
        public static final ConfigKey<Boolean> MAKE_NEW = ConfigKeys.newBooleanConfigKey("makeNew", "whether to make the 'new' ones each time", true);
        
        @Override
        public void init() {
            super.init();
            connectSensors();
        }

        @Override
        public void rebind() {
            super.rebind();
            connectSensors();
        }
        
        public static class BigStringSupplier implements Supplier<String> {
            final String prefix;
            final int size;
            // just to take up memory/disk space
            final String sample;
            public BigStringSupplier(String prefix, int size) {
                this.prefix = prefix;
                this.size = size;
                sample = get();
            }
            public String get() {
                return prefix + (size>=0 ? "-"+Identifiers.makeRandomId(size) : "");
            }
            @Override
            public boolean equals(Object obj) {
                return (obj instanceof BigStringSupplier) && prefix.equals(((BigStringSupplier)obj).prefix);
            }
            @Override
            public int hashCode() {
                return prefix.hashCode();
            }
        }
        
        public void connectSensors() {
            final Duration PERIOD = Duration.FIVE_SECONDS;
            int size = getConfig(DATA_SIZE);
            boolean makeNew = getConfig(MAKE_NEW);

            if (makeNew) addFeed(FunctionFeed.builder().entity(this).period(PERIOD)
                .poll(FunctionPollConfig.forSensor(SENSOR_STRING)
                    .supplier(new BigStringSupplier("new-each-time-entity-"+this+"-created-"+System.currentTimeMillis()+"-"+Identifiers.makeRandomId(4), size))
                    .onResult(new IdentityFunctionLogging())).build());

            addFeed(FunctionFeed.builder().entity(this).period(PERIOD)
                .poll(FunctionPollConfig.forSensor(SENSOR_STRING)
                    .supplier(new BigStringSupplier("same-each-time-entity-"+this, size))
                    .onResult(new IdentityFunctionLogging())).build());

            if (makeNew) addFeed(FunctionFeed.builder().entity(this).period(PERIOD)
                .uniqueTag("new-each-time-"+Identifiers.makeRandomId(4)+"-"+System.currentTimeMillis())
                .poll(FunctionPollConfig.forSensor(SENSOR_STRING)
                    .supplier(new BigStringSupplier("new-each-time-entity-"+this, size))
                    .onResult(new IdentityFunctionLogging())).build());

            addFeed(FunctionFeed.builder().entity(this).period(PERIOD)
                .uniqueTag("same-each-time-entity-"+this)
                .poll(FunctionPollConfig.forSensor(SENSOR_STRING)
                    .supplier(new BigStringSupplier("same-each-time-entity-"+this, size))
                    .onResult(new IdentityFunctionLogging())).build());
        }
    }
    
    public static class IdentityFunctionLogging implements Function<Object,String> {
        @Override
        public String apply(Object input) {
            System.out.println(Strings.maxlen(Strings.toString(input), 80));
            return Strings.toString(input);
        }
    }

}
