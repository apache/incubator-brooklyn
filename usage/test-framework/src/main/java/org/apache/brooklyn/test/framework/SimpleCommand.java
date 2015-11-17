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
package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import static org.apache.brooklyn.core.config.ConfigKeys.newConfigKey;

/**
 * Entity to invoke on a node a simple command that will immediately succeed or fail.
 *
 * Invokes the command in the start operation, and declares itself RUNNING.
 */
@ImplementedBy(SimpleCommandImpl.class)
public interface SimpleCommand extends Entity, Startable {

    String TMP_DEFAULT = "/tmp";

    /**
     * Result of a command invocation.
     */
    interface Result {
        int getExitCode();
        String getStdout();
        String getStderr();

    }

    @SetFromFlag(nullable = false)
    ConfigKey<String> DEFAULT_COMMAND = ConfigKeys.newConfigKey(String.class, "defaultCommand",
            "Command to invoke if no script is provided via a downloadUrl");

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = SoftwareProcess.DOWNLOAD_URL;

    @SetFromFlag("scriptDir")
    ConfigKey<String> SCRIPT_DIR = newConfigKey("scriptDir", "directory where downloaded scripts should be put", TMP_DEFAULT);
}
