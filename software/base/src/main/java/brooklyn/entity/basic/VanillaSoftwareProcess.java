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
import brooklyn.event.basic.AttributeSensorAndConfigKey;

/** 
 * A {@link SoftwareProcess} entity that runs commands from an archive.
 * <p>
 * Downloads and unpacks the archive indicated (optionally) then runs the management commands (scripts) indicated
 * (relative to the root of the archive if supplied, otherwise in a tmp working dir) to manage. Uses config keys
 * to identify the files or commands to use.
 * <p>
 * In the simplest mode, simply provide either:
 * <ul>
 * <li> an archive in {@link #DOWNLOAD_URL} containing a <code>./start.sh</code>
 * <li> a start command to invoke in {@link #LAUNCH_COMMAND}
 * </ul>
 * The only constraint is that the start command must write the PID into the file pointed to by the injected environment
 * variable {@code PID_FILE} unless one of the options below is supported.
 * <p>
 * The start command can be a complex bash command, downloading and unpacking files, and handling the {@code PID_FILE} requirement.
 * For example {@code export MY_PID_FILE=$PID_FILE ; ./my_start.sh} or {@code nohup ./start.sh & ; echo $! > $PID_FILE ; sleep 5}.
 * </pre>
 * You can supply both {@link #DOWNLOAD_URL} and {@link #LAUNCH_COMMAND} configuration as well..
 * <p>
 * By default the PID is used to stop the process using {@code kill} followed by {@code kill -9} if needed and restart
 * is implemented by stopping the process and then running {@link VanillaSoftwareProcessSshDriver#launch()}, but it is
 * possible to override this behavior through config keys:
 * <ul>
 * <li> A custom {@link #CHECK_RUNNING_COMMAND}
 * <li> A custom {@link #STOP_COMMAND}
 * <li> A different {@link SoftwareProcess#PID_FILE} to use
 * <li>
 */
@ImplementedBy(VanillaSoftwareProcessImpl.class)
public interface VanillaSoftwareProcess extends SoftwareProcess {

    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = SoftwareProcess.DOWNLOAD_URL;

    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.0.0");

    ConfigKey<String> LAUNCH_COMMAND = ConfigKeys.newStringConfigKey("launch.command", "command to run to launch the process", "./start.sh");
    ConfigKey<String> CHECK_RUNNING_COMMAND = ConfigKeys.newStringConfigKey("checkRunning.command", "command to determine whether the process is running");
    ConfigKey<String> STOP_COMMAND = ConfigKeys.newStringConfigKey("stop.command", "command to run to stop the process");

}
