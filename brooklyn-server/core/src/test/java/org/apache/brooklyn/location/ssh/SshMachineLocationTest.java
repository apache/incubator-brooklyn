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
package org.apache.brooklyn.location.ssh;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.EntityInitializer;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineDetails;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.EffectorTaskTest;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.location.BasicHardwareDetails;
import org.apache.brooklyn.core.location.BasicMachineDetails;
import org.apache.brooklyn.core.location.BasicOsDetails;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.core.internal.ssh.RecordingSshTool;
import org.apache.brooklyn.util.core.internal.ssh.SshException;
import org.apache.brooklyn.util.core.task.BasicExecutionContext;
import org.apache.brooklyn.util.core.task.BasicExecutionManager;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

/**
 * Test the {@link SshMachineLocation} implementation of the {@link Location} interface.
 */
public class SshMachineLocationTest extends BrooklynAppUnitTestSupport {

    private SshMachineLocation host;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        host = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getLocalHost()));
        RecordingSshTool.clear();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (host != null) Streams.closeQuietly(host);
        } finally {
            RecordingSshTool.clear();
            super.tearDown();
        }
    }

    @Test(groups = "Integration")
    public void testGetMachineDetails() throws Exception {
        BasicExecutionManager execManager = new BasicExecutionManager("mycontextid");
        BasicExecutionContext execContext = new BasicExecutionContext(execManager);
        try {
            MachineDetails details = execContext.submit(new Callable<MachineDetails>() {
                public MachineDetails call() {
                    return host.getMachineDetails();
                }}).get();
            assertNotNull(details);
        } finally {
            execManager.shutdownNow();
        }
    }
    
    @Test
    public void testSupplyingMachineDetails() throws Exception {
        MachineDetails machineDetails = new BasicMachineDetails(new BasicHardwareDetails(1, 1024), new BasicOsDetails("myname", "myarch", "myversion"));
        SshMachineLocation host2 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure(SshMachineLocation.MACHINE_DETAILS, machineDetails));
        
        assertSame(host2.getMachineDetails(), machineDetails);
    }
    
    @Test
    public void testConfigurePrivateAddresses() throws Exception {
        SshMachineLocation host2 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getLocalHost())
                .configure(SshMachineLocation.PRIVATE_ADDRESSES, ImmutableList.of("1.2.3.4"))
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true));

        assertEquals(host2.getPrivateAddresses(), ImmutableSet.of("1.2.3.4"));
    }
    
    // Wow, this is hard to test (until I accepted creating the entity + effector)! Code smell?
    // Need to call getMachineDetails in a DynamicSequentialTask so that the "innessential" takes effect,
    // to not fail its caller. But to get one of those outside of an effector is non-obvious.
    @Test(groups = "Integration")
    public void testGetMachineIsInessentialOnFailure() throws Exception {
        SshMachineLocation host2 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getLocalHost())
                .configure(SshMachineLocation.SSH_TOOL_CLASS, FailingSshTool.class.getName()));

        final Effector<MachineDetails> GET_MACHINE_DETAILS = Effectors.effector(MachineDetails.class, "getMachineDetails")
                .impl(new EffectorBody<MachineDetails>() {
                    public MachineDetails call(ConfigBag parameters) {
                        Maybe<MachineLocation> machine = Machines.findUniqueMachineLocation(entity().getLocations());
                        try {
                            machine.get().getMachineDetails();
                            throw new IllegalStateException("Expected failure in ssh");
                        } catch (RuntimeException e) {
                            return null;
                        }
                    }})
                .build();

        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true)
                .addInitializer(new EntityInitializer() {
                        public void apply(EntityLocal entity) {
                            ((EntityInternal)entity).getMutableEntityType().addEffector(EffectorTaskTest.DOUBLE_1);
                        }});

        TestApplication app = ApplicationBuilder.newManagedApp(appSpec, mgmt);

        app.start(ImmutableList.of(host2));
        
        MachineDetails details = app.invoke(GET_MACHINE_DETAILS, ImmutableMap.<String, Object>of()).get();
        assertNull(details);
    }
    public static class FailingSshTool extends RecordingSshTool {
        public FailingSshTool(Map<?, ?> props) {
            super(props);
        }
        @Override public int execScript(Map<String, ?> props, List<String> commands, Map<String, ?> env) {
            throw new RuntimeException("Simulating failure of ssh: cmds="+commands);
        }
        @Override public int execCommands(Map<String, ?> props, List<String> commands, Map<String, ?> env) {
            throw new RuntimeException("Simulating failure of ssh: cmds="+commands);
        }
    }
    
    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testSshExecScript() throws Exception {
        OutputStream outStream = new ByteArrayOutputStream();
        String expectedName = Os.user();
        host.execScript(MutableMap.of("out", outStream), "mysummary", ImmutableList.of("whoami; exit"));
        String outString = outStream.toString();
        
        assertTrue(outString.contains(expectedName), outString);
    }
    
    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testSshExecCommands() throws Exception {
        OutputStream outStream = new ByteArrayOutputStream();
        String expectedName = Os.user();
        host.execCommands(MutableMap.of("out", outStream), "mysummary", ImmutableList.of("whoami; exit"));
        String outString = outStream.toString();
        
        assertTrue(outString.contains(expectedName), outString);
    }
    
    // For issue #230
    @Test(groups = "Integration")
    public void testOverridingPropertyOnExec() throws Exception {
        SshMachineLocation host = new SshMachineLocation(MutableMap.of("address", Networking.getLocalHost(), "sshPrivateKeyData", "wrongdata"));
        
        OutputStream outStream = new ByteArrayOutputStream();
        String expectedName = Os.user();
        host.execCommands(MutableMap.of("sshPrivateKeyData", null, "out", outStream), "my summary", ImmutableList.of("whoami"));
        String outString = outStream.toString();
        
        assertTrue(outString.contains(expectedName), "outString="+outString);
    }

    @Test(groups = "Integration", expectedExceptions={IllegalStateException.class, SshException.class})
    public void testSshRunWithInvalidUserFails() throws Exception {
        SshMachineLocation badHost = new SshMachineLocation(MutableMap.of("user", "doesnotexist", "address", Networking.getLocalHost()));
        badHost.execScript("mysummary", ImmutableList.of("whoami; exit"));
    }
    
    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testCopyFileTo() throws Exception {
        File dest = Os.newTempFile(getClass(), ".dest.tmp");
        File src = Os.newTempFile(getClass(), ".src.tmp");
        try {
            Files.write("abc", src, Charsets.UTF_8);
            host.copyTo(src, dest);
            assertEquals("abc", Files.readFirstLine(dest, Charsets.UTF_8));
        } finally {
            src.delete();
            dest.delete();
        }
    }

    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testCopyStreamTo() throws Exception {
        String contents = "abc";
        File dest = new File(Os.tmp(), "sssMachineLocationTest_dest.tmp");
        try {
            host.copyTo(Streams.newInputStreamWithContents(contents), dest.getAbsolutePath());
            assertEquals("abc", Files.readFirstLine(dest, Charsets.UTF_8));
        } finally {
            dest.delete();
        }
    }

    @Test(groups = "Integration")
    public void testInstallUrlTo() throws Exception {
        File dest = new File(Os.tmp(), "sssMachineLocationTest_dir/");
        dest.mkdir();
        try {
            int result = host.installTo("https://raw.github.com/brooklyncentral/brooklyn/master/README.md", Urls.mergePaths(dest.getAbsolutePath(), "README.md"));
            assertEquals(result, 0);
            String contents = ArchiveUtils.readFullyString(new File(dest, "README.md"));
            assertTrue(contents.contains("http://brooklyncentral.github.com"), "contents missing expected phrase; contains:\n"+contents);
        } finally {
            dest.delete();
        }
    }
    
    @Test(groups = "Integration")
    public void testInstallClasspathCopyTo() throws Exception {
        File dest = new File(Os.tmp(), "sssMachineLocationTest_dir/");
        dest.mkdir();
        try {
            int result = host.installTo("classpath://brooklyn/config/sample.properties", Urls.mergePaths(dest.getAbsolutePath(), "sample.properties"));
            assertEquals(result, 0);
            String contents = ArchiveUtils.readFullyString(new File(dest, "sample.properties"));
            assertTrue(contents.contains("Property 1"), "contents missing expected phrase; contains:\n"+contents);
        } finally {
            dest.delete();
        }
    }

    // Note: requires `ssh localhost` to be setup such that no password is required    
    @Test(groups = "Integration")
    public void testIsSshableWhenTrue() throws Exception {
        assertTrue(host.isSshable());
    }
    
    // Note: on some (home/airport) networks, `ssh 123.123.123.123` hangs seemingly forever.
    // Make sure we fail, waiting for longer than the 70 second TCP timeout.
    //
    // Times out in 2m7s on Ubuntu Vivid (syn retries set to 6)
    @Test(groups = "Integration")
    public void testIsSshableWhenFalse() throws Exception {
        byte[] unreachableIp = new byte[] {123,123,123,123};
        final SshMachineLocation unreachableHost = new SshMachineLocation(MutableMap.of("address", InetAddress.getByAddress("unreachablename", unreachableIp)));
        Asserts.assertReturnsEventually(new Runnable() {
            public void run() {
                assertFalse(unreachableHost.isSshable());
            }},
            Duration.minutes(3));
    }
    
    @Test
    public void obtainSpecificPortGivesOutPortOnlyOnce() {
        int port = 2345;
        assertTrue(host.obtainSpecificPort(port));
        assertFalse(host.obtainSpecificPort(port));
        host.releasePort(port);
        assertTrue(host.obtainSpecificPort(port));
    }
    
    @Test
    public void obtainPortInRangeGivesBackRequiredPortOnlyIfAvailable() {
        int port = 2345;
        assertEquals(host.obtainPort(new PortRanges.LinearPortRange(port, port)), port);
        assertEquals(host.obtainPort(new PortRanges.LinearPortRange(port, port)), -1);
        host.releasePort(port);
        assertEquals(host.obtainPort(new PortRanges.LinearPortRange(port, port)), port);
    }
    
    @Test
    public void obtainPortInWideRange() {
        int lowerPort = 2345;
        int upperPort = 2350;
        PortRange range = new PortRanges.LinearPortRange(lowerPort, upperPort);
        for (int i = lowerPort; i <= upperPort; i++) {
            assertEquals(host.obtainPort(range), i);
        }
        assertEquals(host.obtainPort(range), -1);
        
        host.releasePort(lowerPort);
        assertEquals(host.obtainPort(range), lowerPort);
        assertEquals(host.obtainPort(range), -1);
    }
    
    @Test
    public void testObtainPortDoesNotUsePreReservedPorts() {
        host = new SshMachineLocation(MutableMap.of("address", Networking.getLocalHost(), "usedPorts", ImmutableSet.of(8000)));
        assertEquals(host.obtainPort(PortRanges.fromString("8000")), -1);
        assertEquals(host.obtainPort(PortRanges.fromString("8000+")), 8001);
    }
}
