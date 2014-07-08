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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.EffectorTaskTest;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineDetails;
import brooklyn.location.MachineLocation;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges.LinearPortRange;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.file.ArchiveUtils;
import brooklyn.util.guava.Maybe;
import brooklyn.util.internal.ssh.RecordingSshTool;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.time.Duration;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

/**
 * Test the {@link SshMachineLocation} implementation of the {@link brooklyn.location.Location} interface.
 */
public class SshMachineLocationTest {

    private SshMachineLocation host;
    private ManagementContext mgmt;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        host = new SshMachineLocation(MutableMap.of("address", Networking.getLocalHost()));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (host != null) Streams.closeQuietly(host);
        if (mgmt != null) Entities.destroyAll(mgmt);
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
    
    // Wow, this is hard to test (until I accepted creating the entity + effector)! Code smell?
    // Need to call getMachineDetails in a DynamicSequentialTask so that the "innessential" takes effect,
    // to not fail its caller. But to get one of those outside of an effector is non-obvious.
    @Test(groups = "Integration")
    public void testGetMachineIsInessentialOnFailure() throws Exception {
        ManagementContext mgmt = new LocalManagementContextForTests();
        
        SshMachineLocation host2 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getLocalHost())
                .configure(SshTool.PROP_TOOL_CLASS, FailingSshTool.class.getName()));

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
    @Test(groups = "Integration")
    public void testIsSshableWhenFalse() throws Exception {
        byte[] unreachableIp = new byte[] {123,123,123,123};
        final SshMachineLocation unreachableHost = new SshMachineLocation(MutableMap.of("address", InetAddress.getByAddress("unreachablename", unreachableIp)));
        Asserts.assertReturnsEventually(new Runnable() {
            public void run() {
                assertFalse(unreachableHost.isSshable());
            }},
            Duration.TWO_MINUTES);
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
        assertEquals(host.obtainPort(new LinearPortRange(port, port)), port);
        assertEquals(host.obtainPort(new LinearPortRange(port, port)), -1);
        host.releasePort(port);
        assertEquals(host.obtainPort(new LinearPortRange(port, port)), port);
    }
    
    @Test
    public void obtainPortInWideRange() {
        int lowerPort = 2345;
        int upperPort = 2350;
        PortRange range = new LinearPortRange(lowerPort, upperPort);
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
