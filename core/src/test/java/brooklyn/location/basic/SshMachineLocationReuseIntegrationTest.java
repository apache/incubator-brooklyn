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
package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.sshj.SshjTool;
import brooklyn.util.net.Networking;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

/**
 * Tests the re-use of SshTools in SshMachineLocation
 */
public class SshMachineLocationReuseIntegrationTest {

    public static class RecordingSshjTool extends SshjTool {
        static int connectionCount = 0;
        static AtomicInteger disconnectionCount = new AtomicInteger();
        
        public RecordingSshjTool(Map<String, ?> map) {
            super(map);
        }

        @Override
        public void connect() {
            connectionCount += 1;
            super.connect();
        }

        @Override
        public void disconnect() {
            disconnectionCount.incrementAndGet();
            super.disconnect();
        }

        public static void reset() {
            connectionCount = 0;
            disconnectionCount.set(0);
        }
    }

    private SshMachineLocation host;
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
        host = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getLocalHost())
                .configure(SshTool.PROP_TOOL_CLASS, RecordingSshjTool.class.getName()));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (host != null) Streams.closeQuietly(host);
        if (managementContext != null) Entities.destroyAll(managementContext);
        RecordingSshjTool.reset();
    }

    @Test(groups = "Integration")
    public void testBasicReuse() throws Exception {
        host.execScript("mysummary", ImmutableList.of("exit"));
        host.execScript("mysummary", ImmutableList.of("exit"));
        assertEquals(RecordingSshjTool.connectionCount, 1, "Expected one SSH connection to have been recorded");
    }

    @Test(groups = "Integration")
    public void testReuseWithInterestingProps() throws Exception {
        host.execScript(customSshConfigKeys(), "mysummary", ImmutableList.of("exit"));
        host.execScript(customSshConfigKeys(), "mysummary", ImmutableList.of("exit"));
        assertEquals(RecordingSshjTool.connectionCount, 1, "Expected one SSH connection to have been recorded");
    }

    @Test(groups = "Integration")
    public void testNewConnectionForDifferentProps() throws Exception {
        host.execScript("mysummary", ImmutableList.of("exit"));
        host.execScript(customSshConfigKeys(), "mysummary", ImmutableList.of("exit"));
        assertEquals(RecordingSshjTool.connectionCount, 2, "Expected two SSH connections to have been recorded");
    }

    @Test(groups = "Integration")
    public void testSshToolReusedWhenConfigDiffers() throws Exception {
        Map<String, Object> props = customSshConfigKeys();
        host.execScript(props, "mysummary", ImmutableList.of("exit"));

        // Use another output stream for second request
        props.put(SshTool.PROP_SCRIPT_HEADER.getName(), "#!/bin/bash -e\n");
        host.execScript(props, "mysummary", ImmutableList.of("exit"));
        assertEquals(RecordingSshjTool.connectionCount, 1, "Expected one SSH connection to have been recorded even though out script header differed.");
    }

    @Test(groups = "Integration")
    public void testSshCacheExpiresEvenIfNotUsed() throws Exception {
        SshMachineLocation host2 = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", InetAddress.getLocalHost())
                .configure(SshMachineLocation.SSH_CACHE_EXPIRY_DURATION, Duration.ONE_SECOND)
                .configure(SshTool.PROP_TOOL_CLASS, RecordingSshjTool.class.getName()));
        
        Map<String, Object> props = customSshConfigKeys();
        host2.execScript(props, "mysummary", ImmutableList.of("exit"));

        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(RecordingSshjTool.disconnectionCount.get(), 1);
            }});
    }

    public Map<String, Object> customSshConfigKeys() throws UnknownHostException {
        return MutableMap.<String, Object>of(
                "address", Networking.getLocalHost(),
                SshTool.PROP_SESSION_TIMEOUT.getName(), 20000,
                SshTool.PROP_CONNECT_TIMEOUT.getName(), 50000,
                SshTool.PROP_SCRIPT_HEADER.getName(), "#!/bin/bash");
    }
}
