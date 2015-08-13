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

import org.apache.brooklyn.api.entity.basic.EntityLocal;

import org.apache.brooklyn.location.basic.WinRmMachineLocation;
import brooklyn.util.net.UserAndHostAndPort;

public class VanillaWindowsProcessWinRmDriver extends AbstractSoftwareProcessWinRmDriver implements VanillaWindowsProcessDriver {

    public VanillaWindowsProcessWinRmDriver(EntityLocal entity, WinRmMachineLocation location) {
        super(entity, location);
    }

    @Override
    public void start() {
        WinRmMachineLocation machine = (WinRmMachineLocation) location;
        UserAndHostAndPort winrmAddress = UserAndHostAndPort.fromParts(machine.getUser(), machine.getAddress().getHostName(), machine.config().get(WinRmMachineLocation.WINRM_PORT));
        getEntity().setAttribute(Attributes.WINRM_ADDRESS, winrmAddress);

        super.start();
    }
    
    @Override
    public void preInstall() {
        super.preInstall();
        executeCommand(VanillaWindowsProcess.PRE_INSTALL_COMMAND, VanillaWindowsProcess.PRE_INSTALL_POWERSHELL_COMMAND, true);
        if (entity.getConfig(VanillaWindowsProcess.PRE_INSTALL_REBOOT_REQUIRED)) {
            rebootAndWait();
        }
    }

    @Override
    public void install() {
        // TODO: Follow install path of VanillaSoftwareProcessSshDriver
        executeCommand(VanillaWindowsProcess.INSTALL_COMMAND, VanillaWindowsProcess.INSTALL_POWERSHELL_COMMAND, true);
        if (entity.getConfig(VanillaWindowsProcess.INSTALL_REBOOT_REQUIRED)) {
            rebootAndWait();
        }
    }

    @Override
    public void customize() {
        // TODO: Follow customize path of VanillaSoftwareProcessSshDriver
        executeCommand(VanillaWindowsProcess.CUSTOMIZE_COMMAND, VanillaWindowsProcess.CUSTOMIZE_POWERSHELL_COMMAND, true);
        if (entity.getConfig(VanillaWindowsProcess.CUSTOMIZE_REBOOT_REQUIRED)) {
            rebootAndWait();
        }
    }

    @Override
    public void launch() {
        executeCommand(VanillaWindowsProcess.LAUNCH_COMMAND, VanillaWindowsProcess.LAUNCH_POWERSHELL_COMMAND, true);
    }

    @Override
    public boolean isRunning() {
        return executeCommand(VanillaWindowsProcess.CHECK_RUNNING_COMMAND,
                VanillaWindowsProcess.CHECK_RUNNING_POWERSHELL_COMMAND, false).getStatusCode() == 0;
    }

    @Override
    public void stop() {
        executeCommand(VanillaWindowsProcess.STOP_COMMAND, VanillaWindowsProcess.STOP_POWERSHELL_COMMAND, true);
    }

}
