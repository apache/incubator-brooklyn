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
package brooklyn.launcher.config;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.launcher.config.BrooklynDevelopmentModes.BrooklynDevelopmentMode;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.management.internal.BrooklynGarbageCollector;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.util.internal.BrooklynSystemProperties;
import brooklyn.util.internal.StringSystemProperty;

/**
 * Convenience collection of popular global configuration values.
 * (Also a handy way to recall where config keys are set.)
 * <p>
 * These can typically be set in brooklyn.properties for global applicability.
 * In some cases (eg SSH_CONFIG_* keys) they can also be set on entities/locations 
 * for behaviour specific to that entity.
 * <p>
 * Also see:
 * <li> {@link BrooklynSystemProperties}
 * <li> {@link BrooklynServiceAttributes}
 * <li> {@link CloudLocationConfig} and classes in that hierarchy.
 */
public class BrooklynGlobalConfig {

    public static final ConfigKey<BrooklynDevelopmentMode> BROOKLYN_DEV_MODE = BrooklynDevelopmentModes.BROOKLYN_DEV_MODE;

    public static final ConfigKey<Boolean> REQUIRE_HTTPS = BrooklynWebConfig.HTTPS_REQUIRED;
    
    public static final ConfigKey<Long> GC_PERIOD = BrooklynGarbageCollector.GC_PERIOD;
    public static final ConfigKey<Boolean> DO_SYSTEM_GC = BrooklynGarbageCollector.DO_SYSTEM_GC;
    public static final ConfigKey<Integer> MAX_TASKS_PER_TAG = BrooklynGarbageCollector.MAX_TASKS_PER_TAG;
    public static final ConfigKey<Long> MAX_TASK_AGE = BrooklynGarbageCollector.MAX_TASK_AGE;

    public static final StringSystemProperty LOCALHOST_IP_ADDRESS = BrooklynServiceAttributes.LOCALHOST_IP_ADDRESS;
    
    // brooklyn.ssh.config.noDeleteAfterExec = true   will cause scripts to be left in situ for debugging
    public static final ConfigKey<Boolean> SSH_CONFIG_NO_DELETE_SCRIPT = BrooklynConfigKeys.SSH_CONFIG_NO_DELETE_SCRIPT;
    
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_DIR = BrooklynConfigKeys.SSH_CONFIG_SCRIPT_DIR;
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_HEADER = BrooklynConfigKeys.SSH_CONFIG_SCRIPT_HEADER;
    public static final ConfigKey<String> SSH_CONFIG_DIRECT_HEADER = BrooklynConfigKeys.SSH_CONFIG_DIRECT_HEADER;

    // TODO other constants from elsewhere
    
}
