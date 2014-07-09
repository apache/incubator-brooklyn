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
package brooklyn.entity.java;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.lifecycle.MyEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class JavaSoftwareProcessSshDriverIntegrationTest {

    private static final long TIMEOUT_MS = 10 * 1000;

    private static final Logger LOG = LoggerFactory.getLogger(JavaSoftwareProcessSshDriverIntegrationTest.class);

    private MachineProvisioningLocation<?> localhost;
    private TestApplication app;

    private static class ConcreteJavaSoftwareProcessSshDriver extends JavaSoftwareProcessSshDriver {
        public ConcreteJavaSoftwareProcessSshDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        @Override protected String getLogFileLocation() { return null; }
        @Override public boolean isRunning() { return false; }
        @Override public void stop() {}
        @Override public void install() {}
        @Override public void customize() {}
        @Override public void launch() {}
    }

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        setup(new LocalManagementContext());
    }
    
    protected void setup(ManagementContext mgmt) {
        app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        localhost = mgmt.getLocationManager().createLocation(
                LocationSpec.create(LocalhostMachineProvisioningLocation.class).configure("name", "localhost"));
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Integration")
    public void testJavaStartStopSshDriverStartsAndStopsApp() {
        final MyEntity entity = app.createAndManageChild(EntitySpec.create(MyEntity.class));
        app.start(ImmutableList.of(localhost));
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertTrue(entity.getAttribute(SoftwareProcess.SERVICE_UP));
            }});
        
        entity.stop();
        assertFalse(entity.getAttribute(SoftwareProcess.SERVICE_UP));
    }

    @Test(groups = "Integration")
    public void testGetJavaVersion() {
        SshMachineLocation sshLocation = app.getManagementContext().getLocationManager().createLocation(
                LocationSpec.create(SshMachineLocation.class).configure("address", "localhost"));
        JavaSoftwareProcessSshDriver driver = new ConcreteJavaSoftwareProcessSshDriver(app, sshLocation);
        Optional<String> version = driver.getCurrentJavaVersion();
        assertNotNull(version);
        assertTrue(version.isPresent());
        LOG.info("{}.testGetJavaVersion found: {} on localhost", getClass(), version.get());
    }

    @Test(groups = "Integration")
    public void testStartsInMgmtSpecifiedDirectory() {
        String dir = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        shutdown();
        LocalManagementContext mgmt = new LocalManagementContext();
        mgmt.getBrooklynProperties().put(BrooklynConfigKeys.ONBOX_BASE_DIR, dir);
        setup(mgmt);
        
        doTestSpecifiedDirectory(dir, dir);
        Os.deleteRecursively(dir);
    }
    
    @Test(groups = "Integration")
    public void testStartsInAppSpecifiedDirectoryUnderHome() {
        String dir = Os.mergePathsUnix("~/.brooklyn-test-"+Strings.makeRandomId(4));
        try {
            app.setConfig(BrooklynConfigKeys.ONBOX_BASE_DIR, dir);
            doTestSpecifiedDirectory(dir, dir);
        } finally {
            Os.deleteRecursively(dir);
        }
    }
    
    @Test(groups = "Integration")
    public void testStartsInDifferentRunAndInstallSpecifiedDirectories() {
        String dir1 = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        String dir2 = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        app.setConfig(BrooklynConfigKeys.INSTALL_DIR, dir1);
        app.setConfig(BrooklynConfigKeys.RUN_DIR, dir2);
        doTestSpecifiedDirectory(dir1, dir2);
        Os.deleteRecursively(dir1);
        Os.deleteRecursively(dir2);
    }
    
    @Test(groups = "Integration")
    public void testStartsInLegacySpecifiedDirectory() {
        String dir1 = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        String dir2 = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        shutdown();
        LocalManagementContext mgmt = new LocalManagementContext();
        mgmt.getBrooklynProperties().put("brooklyn.dirs.install", dir1);
        mgmt.getBrooklynProperties().put("brooklyn.dirs.run", dir2);
        setup(mgmt);
        
        app.setConfig(BrooklynConfigKeys.RUN_DIR, dir2);
        doTestSpecifiedDirectory(dir1, dir2);
        Os.deleteRecursively(dir1);
        Os.deleteRecursively(dir2);
    }
    
    protected void doTestSpecifiedDirectory(final String installDirPrefix, final String runDirPrefix) {
        final MyEntity entity = app.createAndManageChild(EntitySpec.create(MyEntity.class));
        app.start(ImmutableList.of(localhost));
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertTrue(entity.getAttribute(SoftwareProcess.SERVICE_UP));
                
                String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
                Assert.assertNotNull(installDir);
                
                String runDir = entity.getAttribute(SoftwareProcess.RUN_DIR);
                Assert.assertNotNull(runDir);
            }});
        
        String installDir = entity.getAttribute(SoftwareProcess.INSTALL_DIR);
        String runDir = entity.getAttribute(SoftwareProcess.RUN_DIR);
        LOG.info("dirs for " + app + " are: install=" + installDir + ", run=" + runDir);
        assertTrue(installDir.startsWith(Os.tidyPath(installDirPrefix)), "INSTALL_DIR is "+installDir+", does not start with expected prefix "+installDirPrefix);
        assertTrue(runDir.startsWith(Os.tidyPath(runDirPrefix)), "RUN_DIR is "+runDir+", does not start with expected prefix "+runDirPrefix);
        
        entity.stop();
        assertFalse(entity.getAttribute(SoftwareProcess.SERVICE_UP));
    }

}
