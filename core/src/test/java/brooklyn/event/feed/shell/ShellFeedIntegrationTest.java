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
package brooklyn.event.feed.shell;

import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.function.FunctionFeedTest;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.event.feed.ssh.SshValueFunctions;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.stream.Streams;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ShellFeedIntegrationTest extends BrooklynAppUnitTestSupport {

    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor("anInt", "");
    final static AttributeSensor<Long> SENSOR_LONG = Sensors.newLongSensor("aLong", "");

    private LocalhostMachineProvisioningLocation loc;
    private EntityLocal entity;
    private ShellFeed feed;
    
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
        super.tearDown();
        if (loc != null) Streams.closeQuietly(loc);
    }
    
    @Test(groups="Integration")
    public void testReturnsShellExitStatus() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<Integer>(SENSOR_INT)
                        .command("exit 123")
                        .onFailure(SshValueFunctions.exitStatus()))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 123);
    }
    
    // TODO timeout no longer supported; would be nice to have a generic task-timeout feature,
    // now that the underlying impl uses SystemProcessTaskFactory
    @Test(enabled=false, groups={"Integration", "WIP"})
    public void testShellTimesOut() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .command("sleep 10")
                        .timeout(1, TimeUnit.MILLISECONDS)
                        .onException(new FunctionFeedTest.ToStringFunction()))
                .build();

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("timed out after 1ms"), "val=" + val);
            }});
    }
    
    @Test(groups="Integration")
    public void testShellUsesEnv() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .env(ImmutableMap.of("MYENV", "MYVAL"))
                        .command("echo hello $MYENV")
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("hello MYVAL"), "val="+val);
            }});
    }
    
    @Test(groups="Integration")
    public void testReturnsShellStdout() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .command("echo hello")
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("hello"), "val="+val);
            }});
    }

    @Test(groups="Integration")
    public void testReturnsShellStderr() throws Exception {
        final String cmd = "thiscommanddoesnotexist";
        
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .command(cmd)
                        .onFailure(SshValueFunctions.stderr()))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains(cmd), "val="+val);
            }});
    }
    
    @Test(groups="Integration")
    public void testFailsOnNonZero() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .command("exit 123")
                        .onSuccess(new Function<SshPollValue, String>() {
                            @Override
                            public String apply(SshPollValue input) {
                                return "Exit status (on success) " + input.getExitStatus();
                            }})
                        .onFailure(new Function<SshPollValue, String>() {
                            @Override
                            public String apply(SshPollValue input) {
                                return "Exit status (on failure) " + input.getExitStatus();
                            }}))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Exit status (on failure) 123"), "val="+val);
            }});
    }
    
    // Example in ShellFeed javadoc
    @Test(groups="Integration")
    public void testDiskUsage() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<Long>(SENSOR_LONG)
                        .command("df -P | tail -1")
                        .onSuccess(new Function<SshPollValue, Long>() {
                            public Long apply(SshPollValue input) {
                                String[] parts = input.getStdout().split("[ \\t]+");
                                System.out.println("input="+input+"; parts="+Arrays.toString(parts));
                                return Long.parseLong(parts[2]);
                            }}))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                Long val = entity.getAttribute(SENSOR_LONG);
                assertTrue(val != null && val >= 0, "val="+val);
            }});
    }
}
