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

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;

public interface AbstractVanillaProcess extends SoftwareProcess {
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = SoftwareProcess.DOWNLOAD_URL;

    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.0.0");
    
    ConfigKey<String> INSTALL_COMMAND = ConfigKeys.newStringConfigKey("install.command", "command to run during the install phase");
    ConfigKey<String> CUSTOMIZE_COMMAND = ConfigKeys.newStringConfigKey("customize.command", "command to run during the customization phase");
    ConfigKey<String> LAUNCH_COMMAND = ConfigKeys.newStringConfigKey("launch.command", "command to run to launch the process");
    ConfigKey<String> CHECK_RUNNING_COMMAND = ConfigKeys.newStringConfigKey("checkRunning.command", "command to determine whether the process is running");
    ConfigKey<String> STOP_COMMAND = ConfigKeys.newStringConfigKey("stop.command", "command to run to stop the process");
}
