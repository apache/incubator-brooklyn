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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.brooklyn.core.config.ConfigKeys.newConfigKey;

/**
 * Tests using a simple command execution.
 */
@ImplementedBy(SimpleShellCommandTestImpl.class)
public interface SimpleShellCommandTest extends BaseTest {

    /**
     * Default location for temporary files.
     */
    String TMP_DEFAULT = "/tmp";

    /**
     * Supply the command to invoke directly. Cannot be used together with {@link #DOWNLOAD_URL}.
     */
    @SetFromFlag(nullable = false)
    ConfigKey<String> COMMAND = ConfigKeys.newConfigKey(String.class, "command", "Command to invoke");

    /**
     * Download a script to invoke. Cannot be used together with {@link #COMMAND}.
     */
    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = SoftwareProcess.DOWNLOAD_URL;

    /**
     * Where the script will be downloaded on the target machine.
     */
    @SetFromFlag("scriptDir")
    ConfigKey<String> SCRIPT_DIR = newConfigKey("script.dir", "directory where downloaded scripts should be put", TMP_DEFAULT);

    /**
     * The working directory that the script will be run from on the target machine.
     */
    @SetFromFlag("runDir")
    ConfigKey<String> RUN_DIR = newConfigKey(String.class, "run.dir", "directory where downloaded scripts should be run from");


    /**
     * If no assertions are configured in the test then the default is this assertion that exit status of the command
     * is zero (successful).
     */
    Map<String, Object> DEFAULT_ASSERTION = ImmutableMap.<String,Object>of(TestFrameworkAssertions.EQUALS, 0);

    /**
     * Assertions on the exit code of the simple command.
     *
     * If not explicitly configured, the default assertion is a non-zero exit code.
     */
    @SetFromFlag("assertStatus")
    ConfigKey<Object> ASSERT_STATUS = ConfigKeys.newConfigKey(Object.class, "assert.status", "Assertions on command exit code",
        ImmutableList.<Map<String, Object>>of());

    /**
     * Assertions on the standard output of the command as a String.
     */
    @SetFromFlag("assertOut")
    ConfigKey<Object> ASSERT_OUT = ConfigKeys.newConfigKey(Object.class, "assert.out", "Assertions on command standard output",
        ImmutableList.<Map<String, Object>>of());

    /**
     * Assertions on the standard error of the command as a String.
     */
    @SetFromFlag("assertErr")
    ConfigKey<Object> ASSERT_ERR = ConfigKeys.newConfigKey(Object.class, "assert.err", "Assertions on command standard error",
        ImmutableList.<Map<String, Object>>of());

}
