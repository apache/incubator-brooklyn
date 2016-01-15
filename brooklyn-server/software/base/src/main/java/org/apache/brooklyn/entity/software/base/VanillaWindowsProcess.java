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

import java.util.Collection;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableSet;

@Catalog(name="Vanilla Windows Process", description="A basic Windows entity configured with scripts, e.g. for launch, check-running and stop")
@ImplementedBy(VanillaWindowsProcessImpl.class)
public interface VanillaWindowsProcess extends AbstractVanillaProcess {
    // 3389 is RDP; 5985 is WinRM (3389 isn't used by Brooklyn, but useful for the end-user subsequently)
    ConfigKey<Collection<Integer>> REQUIRED_OPEN_LOGIN_PORTS = ConfigKeys.newConfigKeyWithDefault(
            SoftwareProcess.REQUIRED_OPEN_LOGIN_PORTS,
            ImmutableSet.of(5985, 3389));
    
    @CatalogConfig(label = "Install PowerShell command", priority=5.5)
    ConfigKey<String> INSTALL_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("install.powershell.command",
            "powershell command to run during the install phase");
    
    @CatalogConfig(label = "Install Command", priority=5)
    ConfigKey<String> INSTALL_COMMAND = VanillaSoftwareProcess.INSTALL_COMMAND;

    @CatalogConfig(label = "Customize PowerShell command", priority=4.5)
    ConfigKey<String> CUSTOMIZE_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("customize.powershell.command",
            "powershell command to run during the customization phase");
    
    @CatalogConfig(label = "Customize command", priority=4)
    ConfigKey<String> CUSTOMIZE_COMMAND = VanillaSoftwareProcess.CUSTOMIZE_COMMAND;
    
    @CatalogConfig(label = "Launch PowerShell command", priority=3.5)
    ConfigKey<String> LAUNCH_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("launch.powershell.command",
            "command to run to launch the process");

    @CatalogConfig(label = "Launch Command", priority=3)
    ConfigKey<String> LAUNCH_COMMAND = ConfigKeys.newConfigKeyWithDefault(VanillaSoftwareProcess.LAUNCH_COMMAND, null);

    @CatalogConfig(label = "Check-running PowerShell Command", priority=2.5)
    ConfigKey<String> CHECK_RUNNING_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("checkRunning.powershell.command",
            "command to determine whether the process is running");

    @CatalogConfig(label = "Check-running Command", priority=2)
    ConfigKey<String> CHECK_RUNNING_COMMAND = VanillaSoftwareProcess.CHECK_RUNNING_COMMAND;

    @CatalogConfig(label = "Stop PowerShell Command", priority=1.5)
    ConfigKey<String> STOP_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("stop.powershell.command",
            "command to run to stop the process");
    
    @CatalogConfig(label = "Stop Command", priority=1)
    ConfigKey<String> STOP_COMMAND = VanillaSoftwareProcess.STOP_COMMAND;

    ConfigKey<String> PRE_INSTALL_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("pre.install.powershell.command",
            "powershell command to run during the pre-install phase");
    
    ConfigKey<String> POST_INSTALL_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("post.install.powershell.command",
            "powershell command to run during the post-install phase");

    ConfigKey<String> PRE_LAUNCH_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("pre.launch.powershell.command",
            "powershell command to run during the pre-launch phase");
    
    ConfigKey<String> POST_LAUNCH_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("post.launch.powershell.command",
            "powershell command to run during the post-launch phase");

    ConfigKey<Boolean> PRE_INSTALL_REBOOT_REQUIRED = ConfigKeys.newBooleanConfigKey("pre.install.reboot.required",
            "indicates that a reboot should be performed after the pre-install command is run", false);
    
    ConfigKey<Boolean> INSTALL_REBOOT_REQUIRED = ConfigKeys.newBooleanConfigKey("install.reboot.required",
            "indicates that a reboot should be performed after the install command is run", false);
    
    ConfigKey<Boolean> CUSTOMIZE_REBOOT_REQUIRED = ConfigKeys.newBooleanConfigKey("customize.reboot.required",
            "indicates that a reboot should be performed after the customize command is run", false);

    ConfigKey<Duration> REBOOT_BEGUN_TIMEOUT = ConfigKeys.newDurationConfigKey("reboot.begun.timeout",
            "duration to wait whilst waiting for a machine to begin rebooting, and thus become unavailable", Duration.TWO_MINUTES);
    
    // TODO If automatic updates are enabled and there are updates waiting to be installed, thirty minutes may not be sufficient...
    ConfigKey<Duration> REBOOT_COMPLETED_TIMEOUT = ConfigKeys.newDurationConfigKey("reboot.completed.timeout",
            "duration to wait whilst waiting for a machine to finish rebooting, and thus to become available again", Duration.minutes(30));
    
    AttributeSensor<Integer> RDP_PORT = Sensors.newIntegerSensor("rdpPort");
    AttributeSensor<Integer> WINRM_PORT = Sensors.newIntegerSensor("winrmPort");
}
