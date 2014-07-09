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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.os.Os;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.stream.Streams;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;


public class SoftwareProcessSshDriverIntegrationTest {

    private LocalManagementContext managementContext;
    private LocalhostMachineProvisioningLocation localhost;
    private SshMachineLocation machine127;
    private TestApplication app;
    private File tempDataDir;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        tempDataDir = Files.createTempDir();
        managementContext = new LocalManagementContext();
        
        localhost = managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        machine127 = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (tempDataDir != null) Os.deleteRecursively(tempDataDir);
    }

    // Integration test because requires ssh'ing (and takes about 5 seconds)
    // See also SoftwareProcessEntityTest.testCustomInstallDirX for a lot more mocked variants
    @Test(groups="Integration")
    public void testCanInstallMultipleVersionsOnSameMachine() throws Exception {
        managementContext.getBrooklynProperties().put(BrooklynConfigKeys.ONBOX_BASE_DIR, tempDataDir.getAbsolutePath());

        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class)
                .configure(SoftwareProcess.SUGGESTED_VERSION, "0.1.0"));
        MyService entity2 = app.createAndManageChild(EntitySpec.create(MyService.class)
                .configure(SoftwareProcess.SUGGESTED_VERSION, "0.2.0"));
        app.start(ImmutableList.of(machine127));
        
        String installDir1 = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        String installDir2 = entity2.getAttribute(SoftwareProcess.INSTALL_DIR);
        
        assertNotEquals(installDir1, installDir2);
        assertTrue(installDir1.contains("0.1.0"), "installDir1="+installDir1);
        assertTrue(installDir2.contains("0.2.0"), "installDir2="+installDir2);
        assertTrue(new File(new File(installDir1), "myfile").isFile());
        assertTrue(new File(new File(installDir2), "myfile").isFile());
    }

    @Test(groups="Integration")
    public void testLocalhostInTmp() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(localhost));

        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        assertTrue(installDir.startsWith("/tmp/brooklyn-"+Os.user()+"/installs/"), "installed in "+installDir);
    }

    @Test(groups="Integration")
    public void testMachine127InHome() throws Exception {
        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(machine127));

        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        assertTrue(installDir.startsWith(Os.home()+"/brooklyn-managed-processes/installs/"), "installed in "+installDir);
    }

    @Test(groups="Integration")
    public void testLocalhostInCustom() throws Exception {
        localhost.setConfig(BrooklynConfigKeys.ONBOX_BASE_DIR, tempDataDir.getAbsolutePath());

        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(localhost));

        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        assertTrue(installDir.startsWith(tempDataDir.getAbsolutePath()+"/installs/"), "installed in "+installDir);
    }

    @Test(groups="Integration")
    @Deprecated
    public void testMachineInCustomFromDataDir() throws Exception {
        managementContext.getBrooklynProperties().put(BrooklynConfigKeys.BROOKLYN_DATA_DIR, tempDataDir.getAbsolutePath());

        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(machine127));

        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        assertTrue(installDir.startsWith(tempDataDir.getAbsolutePath()+"/installs/"), "installed in "+installDir);
    }

    @Test(groups="Integration")
    public void testCopyResource() throws Exception {
        File tempDest = new File(tempDataDir, "tempDest.txt");
        String tempLocalContent = "abc";
        File tempLocal = new File(tempDataDir, "tempLocal.txt");
        Files.write(tempLocalContent, tempLocal, Charsets.UTF_8);
        
        localhost.setConfig(BrooklynConfigKeys.ONBOX_BASE_DIR, tempDataDir.getAbsolutePath());

        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(localhost));

        // Copy local file
        entity.getDriver().copyResource(tempLocal, tempDest.getAbsolutePath());
        assertEquals(Files.readLines(tempDest, Charsets.UTF_8), ImmutableList.of(tempLocalContent));
        tempDest.delete();
        
        // Copy local file using url
        entity.getDriver().copyResource(tempLocal.toURI().toString(), tempDest.getAbsolutePath());
        assertEquals(Files.readLines(tempDest, Charsets.UTF_8), ImmutableList.of(tempLocalContent));
        tempDest.delete();
        
        // Copy reader
        entity.getDriver().copyResource(new StringReader(tempLocalContent), tempDest.getAbsolutePath());
        assertEquals(Files.readLines(tempDest, Charsets.UTF_8), ImmutableList.of(tempLocalContent));
        tempDest.delete();
        
        // Copy stream
        entity.getDriver().copyResource(ByteSource.wrap(tempLocalContent.getBytes()).openStream(), tempDest.getAbsolutePath());
        assertEquals(Files.readLines(tempDest, Charsets.UTF_8), ImmutableList.of(tempLocalContent));
        tempDest.delete();
        
        // Copy known-size stream
        entity.getDriver().copyResource(new KnownSizeInputStream(Streams.newInputStreamWithContents(tempLocalContent), tempLocalContent.length()), tempDest.getAbsolutePath());
        assertEquals(Files.readLines(tempDest, Charsets.UTF_8), ImmutableList.of(tempLocalContent));
        tempDest.delete();
    }

    @Test(groups="Integration")
    public void testCopyResourceCreatingParentDir() throws Exception {
        File tempDataDirSub = new File(tempDataDir, "subdir");
        File tempDest = new File(tempDataDirSub, "tempDest.txt");
        String tempLocalContent = "abc";
        File tempLocal = new File(tempDataDir, "tempLocal.txt");
        Files.write(tempLocalContent, tempLocal, Charsets.UTF_8);
        
        localhost.setConfig(BrooklynConfigKeys.ONBOX_BASE_DIR, tempDataDir.getAbsolutePath());

        MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class));
        app.start(ImmutableList.of(localhost));

        // First confirm would get exception in createeParentDir==false
        try {
            entity.getDriver().copyResource(tempLocal.toURI().toString(), tempDest.getAbsolutePath(), false);
            assertEquals(Files.readLines(tempDest, Charsets.UTF_8), ImmutableList.of(tempLocalContent));
            fail("Should have failed to create "+tempDest);
        } catch (SshException e) {
            // success
        } finally {
            Os.deleteRecursively(tempDataDirSub);
        }
        
        // Copy to absolute path
        try {
            entity.getDriver().copyResource(tempLocal.toURI().toString(), tempDest.getAbsolutePath(), true);
            assertEquals(Files.readLines(tempDest, Charsets.UTF_8), ImmutableList.of(tempLocalContent));
        } finally {
            Os.deleteRecursively(tempDataDirSub);
        }
        
        // Copy to absolute path
        String runDir = entity.getDriver().getRunDir();
        String tempDataDirRelativeToRunDir = "subdir";
        String tempDestRelativeToRunDir = Os.mergePaths(tempDataDirRelativeToRunDir, "tempDest.txt");
        File tempDestInRunDir = new File(Os.mergePaths(runDir, tempDestRelativeToRunDir));
        try {
            entity.getDriver().copyResource(tempLocal.toURI().toString(), tempDestRelativeToRunDir, true);
            assertEquals(Files.readLines(tempDestInRunDir, Charsets.UTF_8), ImmutableList.of(tempLocalContent));
        } finally {
            Os.deleteRecursively(new File(runDir, tempDataDirRelativeToRunDir));
        }
    }

    @Test(groups="Integration")
    public void testPreAndPostLaunchCommands() throws IOException {
        File tempFile = new File(tempDataDir, "tempFile.txt");
        localhost.setConfig(BrooklynConfigKeys.ONBOX_BASE_DIR, tempDataDir.getAbsolutePath());
        app.createAndManageChild(EntitySpec.create(VanillaSoftwareProcess.class)
                .configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "")
                .configure(SoftwareProcess.PRE_LAUNCH_COMMAND, String.format("echo inPreLaunch >> %s", tempFile.getAbsoluteFile()))
                .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, String.format("echo inLaunch >> %s", tempFile.getAbsoluteFile()))
                .configure(SoftwareProcess.POST_LAUNCH_COMMAND, String.format("echo inPostLaunch >> %s", tempFile.getAbsoluteFile())));
        app.start(ImmutableList.of(localhost));
        List<String> output = Files.readLines(tempFile, Charsets.UTF_8);
        assertEquals(output.size(), 3);
        assertEquals(output.get(0), "inPreLaunch");
        assertEquals(output.get(1), "inLaunch");
        assertEquals(output.get(2), "inPostLaunch");
        tempFile.delete();
    }

    @ImplementedBy(MyServiceImpl.class)
    public interface MyService extends SoftwareProcess {
        public SimulatedDriver getDriver();
    }
    
    public static class MyServiceImpl extends SoftwareProcessImpl implements MyService {
        public MyServiceImpl() {
        }

        @Override
        public Class<?> getDriverInterface() {
            return SimulatedDriver.class;
        }
        
        @Override
        public SimulatedDriver getDriver() {
            return (SimulatedDriver) super.getDriver();
        }
    }

    public static class SimulatedDriver extends AbstractSoftwareProcessSshDriver {
        public List<String> events = new ArrayList<String>();
        private volatile boolean launched = false;
        
        public SimulatedDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }

        @Override
        public void install() {
            events.add("install");
            newScript(INSTALLING)
                    .failOnNonZeroResultCode()
                    .body.append("touch myfile")
                    .execute();
        }
        
        @Override
        public void customize() {
            events.add("customize");
        }
    
        @Override
        public void launch() {
            events.add("launch");
            launched = true;
            entity.setAttribute(Startable.SERVICE_UP, true);
        }
        
        @Override
        public boolean isRunning() {
            return launched;
        }
    
        @Override
        public void stop() {
            events.add("stop");
            launched = false;
            entity.setAttribute(Startable.SERVICE_UP, false);
        }
    
        @Override
        public void kill() {
            events.add("kill");
            launched = false;
            entity.setAttribute(Startable.SERVICE_UP, false);
        }
    }
}
