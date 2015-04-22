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

import brooklyn.config.ConfigKey;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(VanillaWindowsProcessImpl.class)
public interface VanillaWindowsProcess extends AbstractVanillaProcess {
    ConfigKey<String> LAUNCH_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("launch.powershell.command",
            "command to run to launch the process");
    ConfigKey<String> CHECK_RUNNING_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("checkRunning.powershell.command",
            "command to determine whether the process is running");
    ConfigKey<String> STOP_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("stop.powershell.command",
            "command to run to stop the process");
    ConfigKey<String> CUSTOMIZE_COMMAND = ConfigKeys.newStringConfigKey("customize.command",
            "command to run during the customization phase");
    ConfigKey<String> CUSTOMIZE_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("customize.powershell.command",
            "powershell command to run during the customization phase");
    ConfigKey<String> INSTALL_COMMAND = ConfigKeys.newStringConfigKey("install.command",
            "command to run during the install phase");
    ConfigKey<String> INSTALL_POWERSHELL_COMMAND = ConfigKeys.newStringConfigKey("install.powershell.command",
            "powershell command to run during the install phase");
}
