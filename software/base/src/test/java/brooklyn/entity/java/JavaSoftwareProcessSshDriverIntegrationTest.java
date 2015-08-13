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

import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.lifecycle.MyEntity;
import org.apache.brooklyn.location.LocationSpec;
import org.apache.brooklyn.location.MachineProvisioningLocation;
import org.apache.brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class JavaSoftwareProcessSshDriverIntegrationTest extends BrooklynAppLiveTestSupport {

    private static final long TIMEOUT_MS = 10 * 1000;

    private static final Logger LOG = LoggerFactory.getLogger(JavaSoftwareProcessSshDriverIntegrationTest.class);

    private MachineProvisioningLocation<?> localhost;

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
    @Override
    public void setUp() throws Exception {
        super.setUp();
        localhost = app.newLocalhostProvisioningLocation();
    }

    @Test(groups = "Integration")
    public void testJavaStartStopSshDriverStartsAndStopsApp() throws Exception {
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
    public void testGetJavaVersion() throws Exception {
        SshMachineLocation sshLocation = app.getManagementContext().getLocationManager().createLocation(
                LocationSpec.create(SshMachineLocation.class).configure("address", "localhost"));
        JavaSoftwareProcessSshDriver driver = new ConcreteJavaSoftwareProcessSshDriver(app, sshLocation);
        Optional<String> version = driver.getInstalledJavaVersion();
        assertNotNull(version);
        assertTrue(version.isPresent());
        LOG.info("{}.testGetJavaVersion found: {} on localhost", getClass(), version.get());
    }

    @Test(groups = "Integration")
    public void testStartsInMgmtSpecifiedDirectory() throws Exception {
        String dir = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        tearDown();
        mgmt = new LocalManagementContextForTests();
        mgmt.getBrooklynProperties().put(BrooklynConfigKeys.ONBOX_BASE_DIR, dir);
        setUp();

        doTestSpecifiedDirectory(dir, dir);
        Os.deleteRecursively(dir);
    }

    @Test(groups = "Integration")
    public void testStartsInAppSpecifiedDirectoryUnderHome() throws Exception {
        String dir = Os.mergePathsUnix("~/.brooklyn-test-"+Strings.makeRandomId(4));
        try {
            app.config().set(BrooklynConfigKeys.ONBOX_BASE_DIR, dir);
            doTestSpecifiedDirectory(dir, dir);
        } finally {
            Os.deleteRecursively(dir);
        }
    }

    @Test(groups = "Integration")
    public void testStartsInDifferentRunAndInstallSpecifiedDirectories() throws Exception {
        String dir1 = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        String dir2 = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        app.config().set(BrooklynConfigKeys.INSTALL_DIR, dir1);
        app.config().set(BrooklynConfigKeys.RUN_DIR, dir2);
        doTestSpecifiedDirectory(dir1, dir2);
        Os.deleteRecursively(dir1);
        Os.deleteRecursively(dir2);
    }

    @Test(groups = "Integration")
    public void testStartsInLegacySpecifiedDirectory() throws Exception {
        String dir1 = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        String dir2 = Os.mergePathsUnix(Os.tmp(), "/brooklyn-test-"+Strings.makeRandomId(4));
        tearDown();
        mgmt = new LocalManagementContextForTests();
        mgmt.getBrooklynProperties().put("brooklyn.dirs.install", dir1);
        mgmt.getBrooklynProperties().put("brooklyn.dirs.run", dir2);
        setUp();

        app.config().set(BrooklynConfigKeys.RUN_DIR, dir2);
        doTestSpecifiedDirectory(dir1, dir2);
        Os.deleteRecursively(dir1);
        Os.deleteRecursively(dir2);
    }

    protected void doTestSpecifiedDirectory(final String installDirPrefix, final String runDirPrefix) throws Exception {
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
