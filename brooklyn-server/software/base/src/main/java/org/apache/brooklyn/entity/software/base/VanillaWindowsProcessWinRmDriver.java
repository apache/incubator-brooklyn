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

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.net.UserAndHostAndPort;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanillaWindowsProcessWinRmDriver extends AbstractSoftwareProcessWinRmDriver implements VanillaWindowsProcessDriver {
    private static final Logger LOG = LoggerFactory.getLogger(VanillaWindowsProcessWinRmDriver.class);

    public VanillaWindowsProcessWinRmDriver(EntityLocal entity, WinRmMachineLocation location) {
        super(entity, location);
    }

    @Override
    public void start() {
        WinRmMachineLocation machine = (WinRmMachineLocation) location;
        UserAndHostAndPort winrmAddress = UserAndHostAndPort.fromParts(machine.getUser(), machine.getAddress().getHostName(), machine.config().get(WinRmMachineLocation.WINRM_PORT));
        getEntity().sensors().set(Attributes.WINRM_ADDRESS, winrmAddress);

        super.start();
    }

    @Override
    public void install() {
        // TODO: Follow install path of VanillaSoftwareProcessSshDriver
        if(Strings.isNonBlank(getEntity().getConfig(VanillaWindowsProcess.INSTALL_COMMAND)) || Strings.isNonBlank(getEntity().getConfig(VanillaWindowsProcess.INSTALL_POWERSHELL_COMMAND))) {
            executeCommandInTask(
                    getEntity().getConfig(VanillaWindowsProcess.INSTALL_COMMAND),
                    getEntity().getConfig(VanillaWindowsProcess.INSTALL_POWERSHELL_COMMAND),
                    "install-command");
        }
        if (entity.getConfig(VanillaWindowsProcess.INSTALL_REBOOT_REQUIRED)) {
            rebootAndWait();
        }
    }

    @Override
    public void customize() {
        if(Strings.isNonBlank(getEntity().getConfig(VanillaWindowsProcess.CUSTOMIZE_COMMAND)) || Strings.isNonBlank(getEntity().getConfig(VanillaWindowsProcess.CUSTOMIZE_POWERSHELL_COMMAND))) {
            executeCommandInTask(
                    getEntity().getConfig(VanillaWindowsProcess.CUSTOMIZE_COMMAND),
                    getEntity().getConfig(VanillaWindowsProcess.CUSTOMIZE_POWERSHELL_COMMAND),
                    "customize-command");
        }
        if (entity.getConfig(VanillaWindowsProcess.CUSTOMIZE_REBOOT_REQUIRED)) {
            rebootAndWait();
        }
    }

    @Override
    public void launch() {
        executeCommandInTask(
                getEntity().getConfig(VanillaWindowsProcess.LAUNCH_COMMAND),
                getEntity().getConfig(VanillaWindowsProcess.LAUNCH_POWERSHELL_COMMAND),
                "launch-command");
    }

    @Override
    public boolean isRunning() {
        int exitCode = executeCommandInTask(
                getEntity().getConfig(VanillaWindowsProcess.CHECK_RUNNING_COMMAND),
                getEntity().getConfig(VanillaWindowsProcess.CHECK_RUNNING_POWERSHELL_COMMAND), "is-running-command");
        if(exitCode != 0) {
            LOG.info(getEntity() + " isRunning check failed: exit code "  + exitCode);
        }
        return exitCode == 0;
    }

    @Override
    public void stop() {
        executeCommandInTask(
                getEntity().getConfig(VanillaWindowsProcess.STOP_COMMAND),
                getEntity().getConfig(VanillaWindowsProcess.STOP_POWERSHELL_COMMAND),
                "stop-command");
    }

}
