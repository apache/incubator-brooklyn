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

import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.test.location.WindowsTestFixture;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class VanillaWindowsProcessWinrmStreamsLiveTest extends AbstractSoftwareProcessStreamsTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(VanillaWindowsProcessWinrmStreamsLiveTest.class);

    protected MachineProvisioningLocation<WinRmMachineLocation> location;
    protected WinRmMachineLocation machine;
    
    // Using BeforeClass so that uses just a single VM for all tests
    @BeforeClass(alwaysRun = true)
    public void setUpClass() throws Exception {
        super.setUp();
        if (app != null) Entities.destroy(app);
        
        location = WindowsTestFixture.setUpWindowsLocation(mgmt);
        machine = location.obtain(ImmutableMap.of());
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {
        try {
            if (location != null && machine != null) location.release(machine);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDownClass method", t);
        } finally {
            super.tearDown();
        }
    }

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() throws Exception {
        try {
            if (app != null) Entities.destroy(app);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDown method", t);
        } finally {
            app = null;
        }
    }

    @Test(groups = "Live")
    @Override
    public void testGetsStreams() {
        VanillaWindowsProcess entity = app.createAndManageChild(EntitySpec.create(VanillaWindowsProcess.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true)
                .configure(VanillaSoftwareProcess.PRE_INSTALL_COMMAND, "echo " + getCommands().get("winrm: pre-install-command.*"))
                .configure(VanillaSoftwareProcess.INSTALL_COMMAND, "echo " + getCommands().get("winrm: install.*"))
                .configure(VanillaSoftwareProcess.POST_INSTALL_COMMAND, "echo " + getCommands().get("winrm: post-install-command.*"))
                .configure(VanillaSoftwareProcess.CUSTOMIZE_COMMAND, "echo " + getCommands().get("winrm: customize.*"))
                .configure(VanillaSoftwareProcess.PRE_LAUNCH_COMMAND, "echo " + getCommands().get("winrm: pre-launch-command.*"))
                .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "echo " + getCommands().get("winrm: launch.*"))
                .configure(VanillaSoftwareProcess.POST_LAUNCH_COMMAND, "echo " + getCommands().get("winrm: post-launch-command.*"))
                .configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "echo true"));
        app.start(ImmutableList.of(machine));
        assertStreams(entity);
    }

    @Test(groups = "Live")
    public void testGetsStreamsPowerShell() {
        VanillaWindowsProcess entity = app.createAndManageChild(EntitySpec.create(VanillaWindowsProcess.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true)
                .configure(VanillaWindowsProcess.PRE_INSTALL_POWERSHELL_COMMAND, "echo " + getCommands().get("winrm: pre-install-command.*"))
                .configure(VanillaWindowsProcess.INSTALL_POWERSHELL_COMMAND, "echo " + getCommands().get("winrm: install.*"))
                .configure(VanillaWindowsProcess.POST_INSTALL_POWERSHELL_COMMAND, "echo " + getCommands().get("winrm: post-install-command.*"))
                .configure(VanillaWindowsProcess.CUSTOMIZE_POWERSHELL_COMMAND, "echo " + getCommands().get("winrm: customize.*"))
                .configure(VanillaWindowsProcess.PRE_LAUNCH_POWERSHELL_COMMAND, "echo " + getCommands().get("winrm: pre-launch-command.*"))
                .configure(VanillaWindowsProcess.LAUNCH_POWERSHELL_COMMAND, "echo " + getCommands().get("winrm: launch.*"))
                .configure(VanillaWindowsProcess.POST_LAUNCH_POWERSHELL_COMMAND, "echo " + getCommands().get("winrm: post-launch-command.*"))
                .configure(VanillaWindowsProcess.CHECK_RUNNING_POWERSHELL_COMMAND, "echo true"));
        app.start(ImmutableList.of(machine));
        assertStreams(entity);
    }

    @Override
    protected Map<String, String> getCommands() {
        return ImmutableMap.<String, String>builder()
                .put("winrm: pre-install-command.*", "myPreInstall")
                .put("winrm: install.*", "myInstall")
                .put("winrm: post-install-command.*", "pre_install_command_output")
                .put("winrm: customize.*", "myCustomizing")
                .put("winrm: pre-launch-command.*", "pre_launch_command_output")
                .put("winrm: launch.*", "myLaunch")
                .put("winrm: post-launch-command.*", "post_launch_command_output")
                .build();
    }
}
