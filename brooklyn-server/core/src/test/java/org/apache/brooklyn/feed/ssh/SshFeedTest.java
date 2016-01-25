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

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class SshFeedTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(SshFeedTest.class);
    
    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<String> SENSOR_STRING2 = Sensors.newStringSensor("aString2", "");

    private LocalhostMachineProvisioningLocation loc;
    private EntityLocal entity;
    private SshFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newLocalhostProvisioningLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        RecordingSshMachineLocation.execScriptCalls.clear();
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        super.tearDown();
        if (loc != null) Streams.closeQuietly(loc);
        RecordingSshMachineLocation.execScriptCalls.clear();
    }
    
    @Test(groups="Integration") // integration because slow 
    public void testSharesCallWhenSameCommand() throws Exception {
        final RecordingSshMachineLocation recordingMachine = mgmt.getLocationManager().createLocation(LocationSpec.create(RecordingSshMachineLocation.class));
        app.start(ImmutableList.of(recordingMachine));
        
        final String cmd = "myCommand";
        
        feed = SshFeed.builder()
                .period(Duration.PRACTICALLY_FOREVER)
                .entity(entity)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .env(ImmutableMap.of("mykey", "myval"))
                        .command(cmd)
                        .onSuccess(Functions.constant("success")))
                .poll(new SshPollConfig<String>(SENSOR_STRING2)
                        .env(ImmutableMap.of("mykey", "myval"))
                        .command(cmd)
                        .onSuccess(Functions.constant("success2")))
                .build();
        
        // Expect it to only execute once (i.e. share exec result for both sensors).
        // Wait several seconds, in case it takes a while to do the second exec.
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(RecordingSshMachineLocation.execScriptCalls, ImmutableList.of(ImmutableList.of(cmd)));
            }});
        Asserts.succeedsContinually(ImmutableMap.of("timeout", Duration.FIVE_SECONDS), new Runnable() {
            public void run() {
                assertEquals(RecordingSshMachineLocation.execScriptCalls, ImmutableList.of(ImmutableList.of(cmd)));
            }});
    }

    @Test
    public void testDifferentCallsWhenDifferentCommands() throws Exception {
        final RecordingSshMachineLocation recordingMachine = mgmt.getLocationManager().createLocation(LocationSpec.create(RecordingSshMachineLocation.class));
        app.start(ImmutableList.of(recordingMachine));
        
        final String cmd = "myCommand";
        final String cmd2 = "myCommand2";
        
        feed = SshFeed.builder()
                .period(Duration.PRACTICALLY_FOREVER)
                .entity(entity)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command(cmd)
                        .onSuccess(Functions.constant("success")))
                .poll(new SshPollConfig<String>(SENSOR_STRING2)
                        .command(cmd2)
                        .onSuccess(Functions.constant("success")))
                .build();
        
        // Expect it to execute the different commands (i.e. not share)
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(ImmutableSet.copyOf(RecordingSshMachineLocation.execScriptCalls), ImmutableSet.of(ImmutableList.of(cmd), ImmutableList.of(cmd2)));
            }});
    }

    @Test
    public void testDifferentCallsWhenDifferentEnv() throws Exception {
        final RecordingSshMachineLocation recordingMachine = mgmt.getLocationManager().createLocation(LocationSpec.create(RecordingSshMachineLocation.class));
        app.start(ImmutableList.of(recordingMachine));
        
        final String cmd = "myCommand";
        
        feed = SshFeed.builder()
                .period(Duration.PRACTICALLY_FOREVER)
                .entity(entity)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .env(ImmutableMap.of("mykey", "myval"))
                        .command(cmd)
                        .onSuccess(Functions.constant("success")))
                .poll(new SshPollConfig<String>(SENSOR_STRING2)
                        .env(ImmutableMap.of("mykey", "myval2"))
                        .command(cmd)
                        .onSuccess(Functions.constant("success")))
                .build();
        
        // Expect it to execute the command twice, with different envs (i.e. not share)
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(RecordingSshMachineLocation.execScriptCalls, ImmutableList.of(ImmutableList.of(cmd), ImmutableList.of(cmd)));
            }});
    }

    public static class RecordingSshMachineLocation extends SshMachineLocation {
        public static List<List<String>> execScriptCalls = Lists.newCopyOnWriteArrayList();

        @Override 
        public int execScript(String summary, List<String> cmds) {
            execScriptCalls.add(cmds);
            return 0;
        }
        @Override 
        public int execScript(Map<String,?> props, String summaryForLogging, List<String> cmds) {
            execScriptCalls.add(cmds);
            return 0;
        }
        @Override 
        public int execScript(String summaryForLogging, List<String> cmds, Map<String,?> env) {
            execScriptCalls.add(cmds);
            return 0;
        }
        @Override 
        public int execScript(Map<String,?> props, String summaryForLogging, List<String> cmds, Map<String,?> env) {
            execScriptCalls.add(cmds);
            return 0;
        }
    }
}
