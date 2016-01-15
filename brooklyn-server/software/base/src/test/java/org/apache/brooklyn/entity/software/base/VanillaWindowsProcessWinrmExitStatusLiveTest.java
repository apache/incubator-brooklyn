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
package org.apache.brooklyn.entity.software.base;

import static org.testng.Assert.fail;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.test.location.WindowsTestFixture;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class VanillaWindowsProcessWinrmExitStatusLiveTest {
    private static final Logger LOG = LoggerFactory.getLogger(VanillaWindowsProcessWinrmExitStatusLiveTest.class);

    private static final String INVALID_CMD = "thisCommandDoesNotExistAEFafiee3d";
    
    protected ManagementContextInternal mgmt;
    protected TestApplication app;
    protected MachineProvisioningLocation<WinRmMachineLocation> location;
    protected WinRmMachineLocation machine;

    @BeforeClass(alwaysRun=true)
    public void setUpClass() throws Exception {
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());

        location = WindowsTestFixture.setUpWindowsLocation(mgmt);
        machine = location.obtain(ImmutableMap.of());
    }

    @AfterClass(alwaysRun=true)
    public void tearDownClass() throws Exception {
        try {
            try {
                if (location != null) location.release(machine);
            } finally {
                if (mgmt != null) Entities.destroyAll(mgmt);
            }
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDownClass method", t);
        } finally {
            mgmt = null;
        }
    }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true);
        app = ApplicationBuilder.newManagedApp(appSpec, mgmt);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            try {
                if (app != null) Entities.destroy(app);
            } catch (Throwable t) {
                LOG.error("Caught exception in tearDown method", t);
            }
        } finally {
            app = null;
        }
    }

    @Test(groups = "Live")
    public void testExecWithZeroExitCodes() {
        VanillaWindowsProcess entity = app.createAndManageChild(EntitySpec.create(VanillaWindowsProcess.class)
                .configure(VanillaWindowsProcess.PRE_INSTALL_COMMAND, "echo preinstall")
                .configure(VanillaWindowsProcess.INSTALL_COMMAND, "echo install")
                .configure(VanillaWindowsProcess.POST_INSTALL_COMMAND, "echo postinstall")
                .configure(VanillaWindowsProcess.CUSTOMIZE_COMMAND, "echo customize")
                .configure(VanillaWindowsProcess.PRE_LAUNCH_COMMAND, "echo prelaunch")
                .configure(VanillaWindowsProcess.LAUNCH_COMMAND, "echo launch")
                .configure(VanillaWindowsProcess.POST_LAUNCH_COMMAND, "echo postlaunch")
                .configure(VanillaWindowsProcess.CHECK_RUNNING_COMMAND, "echo checkrunning")
                .configure(VanillaWindowsProcess.STOP_COMMAND, "echo stop"));
        
        app.start(ImmutableList.of(machine));
        LOG.info("app started; asserting up");
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        entity.stop();
        LOG.info("stopping entity");
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, false);
    }

    @Test(groups = "Live")
    public void testExecPsWithZeroExitCodes() {
        VanillaWindowsProcess entity = app.createAndManageChild(EntitySpec.create(VanillaWindowsProcess.class)
                .configure(VanillaWindowsProcess.PRE_INSTALL_POWERSHELL_COMMAND, "Write-Host preinstall")
                .configure(VanillaWindowsProcess.INSTALL_POWERSHELL_COMMAND, "Write-Host install")
                .configure(VanillaWindowsProcess.POST_INSTALL_POWERSHELL_COMMAND, "Write-Host postinstall")
                .configure(VanillaWindowsProcess.CUSTOMIZE_POWERSHELL_COMMAND, "Write-Host customize")
                .configure(VanillaWindowsProcess.PRE_LAUNCH_POWERSHELL_COMMAND, "Write-Host prelaunch")
                .configure(VanillaWindowsProcess.LAUNCH_POWERSHELL_COMMAND, "Write-Host launch")
                .configure(VanillaWindowsProcess.POST_LAUNCH_POWERSHELL_COMMAND, "Write-Host postlaunch")
                .configure(VanillaWindowsProcess.CHECK_RUNNING_POWERSHELL_COMMAND, "Write-Host checkrunning")
                .configure(VanillaWindowsProcess.STOP_POWERSHELL_COMMAND, "Write-Host stop"));
        
        app.start(ImmutableList.of(machine));
        LOG.info("app started; asserting up");
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        entity.stop();
        LOG.info("stopping entity");
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, false);
    }

    @Test(groups = "Live")
    public void testPreInstallNonZeroExitCode() {
        runExecNonZeroExitCode("pre-install-command");
    }

    @Test(groups = "Live")
    public void testInstallNonZeroExitCode() {
        runExecNonZeroExitCode("install-command");
    }

    @Test(groups = "Live")
    public void testPostInstallNonZeroExitCode() {
        runExecNonZeroExitCode("post-install-command");
    }

    @Test(groups = "Live")
    public void testCustomizeNonZeroExitCode() {
        runExecNonZeroExitCode("customize-command");
    }

    @Test(groups = "Live")
    public void testPreLaunchNonZeroExitCode() {
        runExecNonZeroExitCode("pre-launch-command");
    }

    @Test(groups = "Live")
    public void testLaunchNonZeroExitCode() {
        runExecNonZeroExitCode("launch-command");
    }

    @Test(groups = "Live")
    public void testCheckRunningNonZeroExitCode() {
        runExecNonZeroExitCode("is-running-command");
    }

    @Test(groups = "Live")
    public void testStopNonZeroExitCode() {
        runExecNonZeroExitCode("stop-command");
    }
    
    @Test(groups = "Live")
    public void testPsPreInstallNonZeroExitCode() {
        runExecPsNonZeroExitCode("pre-install-command");
    }

    @Test(groups = "Live")
    public void testPsInstallNonZeroExitCode() {
        runExecPsNonZeroExitCode("install-command");
    }

    @Test(groups = "Live")
    public void testPsPostInstallNonZeroExitCode() {
        runExecPsNonZeroExitCode("post-install-command");
    }

    @Test(groups = "Live")
    public void testPsCustomizeNonZeroExitCode() {
        runExecPsNonZeroExitCode("customize-command");
    }

    @Test(groups = "Live")
    public void testPsPreLaunchNonZeroExitCode() {
        runExecPsNonZeroExitCode("pre-launch-command");
    }

    @Test(groups = "Live")
    public void testPsLaunchNonZeroExitCode() {
        runExecPsNonZeroExitCode("launch-command");
    }

    @Test(groups = "Live")
    public void testPsCheckRunningNonZeroExitCode() {
        runExecPsNonZeroExitCode("is-running-command");
    }

    @Test(groups = "Live")
    public void testPsStopNonZeroExitCode() {
        runExecPsNonZeroExitCode("stop-command");
    }

    protected void runExecNonZeroExitCode(String phase) {
        VanillaWindowsProcess entity = app.createAndManageChild(EntitySpec.create(VanillaWindowsProcess.class)
                .configure(VanillaWindowsProcess.PRE_INSTALL_COMMAND, phase.equals("pre-install-command") ? INVALID_CMD : "echo install")
                .configure(VanillaWindowsProcess.INSTALL_COMMAND, phase.equals("install-command") ? INVALID_CMD : "echo install")
                .configure(VanillaWindowsProcess.POST_INSTALL_COMMAND, phase.equals("post-install-command") ? INVALID_CMD : "echo postinstall")
                .configure(VanillaWindowsProcess.CUSTOMIZE_COMMAND, phase.equals("customize-command") ? INVALID_CMD : "echo customize")
                .configure(VanillaWindowsProcess.PRE_LAUNCH_COMMAND, phase.equals("pre-launch-command") ? INVALID_CMD : "echo prelaunch")
                .configure(VanillaWindowsProcess.LAUNCH_COMMAND, phase.equals("launch-command") ? INVALID_CMD : "echo launch")
                .configure(VanillaWindowsProcess.POST_LAUNCH_COMMAND, phase.equals("post-launch-command") ? INVALID_CMD : "echo postlaunch")
                .configure(VanillaWindowsProcess.CHECK_RUNNING_COMMAND, phase.equals("is-running-command") ? INVALID_CMD : "echo checkrunning")
                .configure(VanillaWindowsProcess.STOP_COMMAND, phase.equals("stop-command") ? INVALID_CMD : "echo stop")
                .configure(BrooklynConfigKeys.START_TIMEOUT, Duration.ONE_MINUTE));

        if (phase.equals("stop-command")) {
            app.start(ImmutableList.of(machine));
            try {
                entity.stop();
                fail();
            } catch (Exception e) {
                if (!(e.toString().contains("invalid result") && e.toString().contains("for "+phase))) throw e;
            }
        } else {
            try {
                app.start(ImmutableList.of(machine));
                fail();
            } catch (Exception e) {
                if (!(e.toString().contains("invalid result") && e.toString().contains("for "+phase))) throw e;
            }
        }
    }
    
    protected void runExecPsNonZeroExitCode(String phase) {
        VanillaWindowsProcess entity = app.createAndManageChild(EntitySpec.create(VanillaWindowsProcess.class)
                .configure(VanillaWindowsProcess.PRE_INSTALL_POWERSHELL_COMMAND, phase.equals("pre-install-command") ? INVALID_CMD : "Write-Host install")
                .configure(VanillaWindowsProcess.INSTALL_POWERSHELL_COMMAND, phase.equals("install-command") ? INVALID_CMD : "Write-Host install")
                .configure(VanillaWindowsProcess.POST_INSTALL_POWERSHELL_COMMAND, phase.equals("post-install-command") ? INVALID_CMD : "Write-Host postinstall")
                .configure(VanillaWindowsProcess.CUSTOMIZE_POWERSHELL_COMMAND, phase.equals("customize-command") ? INVALID_CMD : "Write-Host customize")
                .configure(VanillaWindowsProcess.PRE_LAUNCH_POWERSHELL_COMMAND, phase.equals("pre-launch-command") ? INVALID_CMD : "Write-Host prelaunch")
                .configure(VanillaWindowsProcess.LAUNCH_POWERSHELL_COMMAND, phase.equals("launch-command") ? INVALID_CMD : "Write-Host launch")
                .configure(VanillaWindowsProcess.POST_LAUNCH_POWERSHELL_COMMAND, phase.equals("post-launch-command") ? INVALID_CMD : "Write-Host postlaunch")
                .configure(VanillaWindowsProcess.CHECK_RUNNING_POWERSHELL_COMMAND, phase.equals("is-running-command") ? INVALID_CMD : "Write-Host checkrunning")
                .configure(VanillaWindowsProcess.STOP_POWERSHELL_COMMAND, phase.equals("stop-command") ? INVALID_CMD : "Write-Host stop")
                .configure(BrooklynConfigKeys.START_TIMEOUT, Duration.ONE_MINUTE));

        if (phase.equals("stop-command")) {
            app.start(ImmutableList.of(machine));
            try {
                entity.stop();
                fail();
            } catch (Exception e) {
                if (!(e.toString().contains("invalid result") && e.toString().contains("for "+phase))) throw e;
            }
        } else {
            try {
                app.start(ImmutableList.of(machine));
                fail();
            } catch (Exception e) {
                if (!(e.toString().contains("invalid result") && e.toString().contains("for "+phase))) throw e;
            }
        }
    }
}
