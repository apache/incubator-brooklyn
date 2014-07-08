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
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

/** 
 * downloads and unpacks the archive indicated (optionally), 
 * then runs the management commands (scripts) indicated
 * (relative to the root of the archive if supplied, otherwise in a tmp working dir) to manage
 * <p>
 * uses config keys to identify the files / commands to use
 * <p>
 * in simplest mode, simply provide either:
 * <li> an archive in {@link #DOWNLOAD_URL} containing a <code>./start.sh</code>
 * <li> a start command to invoke in {@link #LAUNCH_COMMAND}
 * <p>
 * the only constraint is that the start command must write the PID into the file pointed to by the injected environment variable PID_FILE,
 * unless one of the options below is supported.
 * <p>
 * the start command can be a complex bash command, downloading and unpacking files, and/or handling the PID_FILE requirement 
 * (e.g. <code>export MY_PID_FILE=$PID_FILE ; ./my_start.sh</code> or <code>nohup ./start.sh & ; echo $! > $PID_FILE ; sleep 5</code>),
 * and of course you can supply both {@link #DOWNLOAD_URL} and {@link #LAUNCH_COMMAND}.
 * <p>
 * by default the PID is used to stop the process (kill followed by kill -9 if needed) and restart (process stop followed by process start),
 * but it is possible to override this behavior through config keys:
 * <li> a custom {@link #CHECK_RUNNING_COMMAND}
 * <li> a custom {@link #STOP_COMMAND}
 * <li> specify which {@link SoftwareProcess#PID_FILE} to use
 */
@ImplementedBy(VanillaSoftwareProcessImpl.class)
public interface VanillaSoftwareProcess extends SoftwareProcess {
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = SoftwareProcess.DOWNLOAD_URL;
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.0.0");

    ConfigKey<String> LAUNCH_COMMAND = ConfigKeys.newStringConfigKey("launch.command", "command to run to launch the process", "./start.sh");
    ConfigKey<String> CHECK_RUNNING_COMMAND = ConfigKeys.newStringConfigKey("checkRunning.command", "command to determine whether the process is running");
    ConfigKey<String> STOP_COMMAND = ConfigKeys.newStringConfigKey("stop.command", "command to run to stop the process");
}
