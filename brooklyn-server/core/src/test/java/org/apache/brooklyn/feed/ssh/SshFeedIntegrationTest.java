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
package org.apache.brooklyn.feed.ssh;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.EntityInitializer;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityInternal.FeedSupport;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshFeedIntegrationTest;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.feed.ssh.SshPollValue;
import org.apache.brooklyn.feed.ssh.SshValueFunctions;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.StringFunctions;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

public class SshFeedIntegrationTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(SshFeedIntegrationTest.class);
    
    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor("aLong", "");

    private LocalhostMachineProvisioningLocation loc;
    private SshMachineLocation machine;
    private EntityLocal entity;
    private SshFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newLocalhostProvisioningLocation();
        machine = loc.obtain();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        super.tearDown();
        if (loc != null) Streams.closeQuietly(loc);
    }
    
    /** this is one of the most common pattern */
    @Test(groups="Integration")
    public void testReturnsSshStdoutAndInfersMachine() throws Exception {
        final TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class)
            // inject the machine location, because the app was started with a provisioning location
            // and TestEntity doesn't provision
            .location(machine));
        
        feed = SshFeed.builder()
                .entity(entity2)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command("echo hello")
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        EntityTestUtils.assertAttributeEventuallyNonNull(entity2, SENSOR_STRING);
        String val = entity2.getAttribute(SENSOR_STRING);
        Assert.assertTrue(val.contains("hello"), "val="+val);
        Assert.assertEquals(val.trim(), "hello");
    }

    @Test(groups="Integration")
    public void testFeedDeDupe() throws Exception {
        testReturnsSshStdoutAndInfersMachine();
        entity.addFeed(feed);
        log.info("Feed 0 is: "+feed);
        
        testReturnsSshStdoutAndInfersMachine();
        log.info("Feed 1 is: "+feed);
        entity.addFeed(feed);
                
        FeedSupport feeds = ((EntityInternal)entity).feeds();
        Assert.assertEquals(feeds.getFeeds().size(), 1, "Wrong feed count: "+feeds.getFeeds());
    }
    
    @Test(groups="Integration")
    public void testReturnsSshExitStatus() throws Exception {
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<Integer>(SENSOR_INT)
                        .command("exit 123")
                        .checkSuccess(Predicates.alwaysTrue())
                        .onSuccess(SshValueFunctions.exitStatus()))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 123);
    }
    
    @Test(groups="Integration")
    public void testReturnsSshStdout() throws Exception {
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command("echo hello")
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        EntityTestUtils.assertAttributeEventually(entity, SENSOR_STRING, 
            Predicates.compose(Predicates.equalTo("hello"), StringFunctions.trim()));
    }

    @Test(groups="Integration")
    public void testReturnsSshStderr() throws Exception {
        final String cmd = "thiscommanddoesnotexist";
        
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command(cmd)
                        .onFailure(SshValueFunctions.stderr()))
                .build();
        
        EntityTestUtils.assertAttributeEventually(entity, SENSOR_STRING, StringPredicates.containsLiteral(cmd));
    }
    
    @Test(groups="Integration")
    public void testFailsOnNonZero() throws Exception {
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command("exit 123")
                        .onFailure(new Function<SshPollValue, String>() {
                            @Override
                            public String apply(SshPollValue input) {
                                return "Exit status " + input.getExitStatus();
                            }}))
                .build();
        
        EntityTestUtils.assertAttributeEventually(entity, SENSOR_STRING, StringPredicates.containsLiteral("Exit status 123"));
    }
    
    @Test(groups="Integration")
    public void testAddedEarly() throws Exception {
        final TestEntity entity2 = app.addChild(EntitySpec.create(TestEntity.class)
            .location(machine)
            .addInitializer(new EntityInitializer() {
                @Override
                public void apply(EntityLocal entity) {
                    SshFeed.builder()
                        .entity(entity)
                        .onlyIfServiceUp()
                        .poll(new SshPollConfig<String>(SENSOR_STRING)
                            .command("echo hello")
                            .onSuccess(SshValueFunctions.stdout()))
                        .build();
                }
            }));

        // TODO would be nice to hook in and assert no errors
        EntityTestUtils.assertAttributeEqualsContinually(entity2, SENSOR_STRING, null);

        entity2.sensors().set(Attributes.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEventually(entity2, SENSOR_STRING, StringPredicates.containsLiteral("hello"));
    }

    
    @Test(groups="Integration")
    public void testDynamicEnvAndCommandSupplier() throws Exception {
        final TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class).location(machine));
        
        final AtomicInteger count = new AtomicInteger();
        Supplier<Map<String, String>> envSupplier = new Supplier<Map<String,String>>() {
            @Override
            public Map<String, String> get() {
                return MutableMap.of("COUNT", ""+count.incrementAndGet());
            }
        };
        Supplier<String> cmdSupplier = new Supplier<String>() {
            @Override
            public String get() {
                return "echo count-"+count.incrementAndGet()+"-$COUNT";
            }
        };
        
        feed = SshFeed.builder()
                .entity(entity2)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .env(envSupplier)
                        .command(cmdSupplier)
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        EntityTestUtils.assertAttributeEventuallyNonNull(entity2, SENSOR_STRING);        
        final String val1 = assertDifferentOneInOutput(entity2);
        
        EntityTestUtils.assertAttributeEventually(entity2, SENSOR_STRING, Predicates.not(Predicates.equalTo(val1)));        
        final String val2 = assertDifferentOneInOutput(entity2);
        log.info("vals from dynamic sensors are: "+val1.trim()+" and "+val2.trim());
    }

    private String assertDifferentOneInOutput(final TestEntity entity2) {
        String val = entity2.getAttribute(SENSOR_STRING);
        Assert.assertTrue(val.startsWith("count"), "val="+val);
        try {
            String[] fields = val.trim().split("-");
            int field1 = Integer.parseInt(fields[1]); 
            int field2 = Integer.parseInt(fields[2]);
            Assert.assertEquals(Math.abs(field2-field1), 1, "expected difference of 1");
        } catch (Throwable t) {
            Exceptions.propagateIfFatal(t);
            Assert.fail("Wrong output from sensor, got '"+val.trim()+"', giving error: "+t);
        }
        return val;
    }

}
