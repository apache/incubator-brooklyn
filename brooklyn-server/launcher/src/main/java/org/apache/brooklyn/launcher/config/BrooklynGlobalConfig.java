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
package org.apache.brooklyn.launcher.config;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.mgmt.internal.BrooklynGarbageCollector;
import org.apache.brooklyn.core.server.BrooklynServiceAttributes;
import org.apache.brooklyn.rest.BrooklynWebConfig;
import org.apache.brooklyn.util.internal.BrooklynSystemProperties;
import org.apache.brooklyn.util.internal.StringSystemProperty;
import org.apache.brooklyn.util.time.Duration;

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

    public static final ConfigKey<Boolean> REQUIRE_HTTPS = BrooklynWebConfig.HTTPS_REQUIRED;
    
    public static final ConfigKey<Duration> GC_PERIOD = BrooklynGarbageCollector.GC_PERIOD;
    public static final ConfigKey<Boolean> DO_SYSTEM_GC = BrooklynGarbageCollector.DO_SYSTEM_GC;
    public static final ConfigKey<Integer> MAX_TASKS_PER_TAG = BrooklynGarbageCollector.MAX_TASKS_PER_TAG;
    public static final ConfigKey<Integer> MAX_TASKS_PER_ENTITY = BrooklynGarbageCollector.MAX_TASKS_PER_ENTITY;
    public static final ConfigKey<Integer> MAX_TASKS_GLOBAL = BrooklynGarbageCollector.MAX_TASKS_GLOBAL;
    public static final ConfigKey<Duration> MAX_TASK_AGE = BrooklynGarbageCollector.MAX_TASK_AGE;

    public static final StringSystemProperty LOCALHOST_IP_ADDRESS = BrooklynServiceAttributes.LOCALHOST_IP_ADDRESS;
    
    // brooklyn.ssh.config.noDeleteAfterExec = true   will cause scripts to be left in situ for debugging
    public static final ConfigKey<Boolean> SSH_CONFIG_NO_DELETE_SCRIPT = BrooklynConfigKeys.SSH_CONFIG_NO_DELETE_SCRIPT;
    
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_DIR = BrooklynConfigKeys.SSH_CONFIG_SCRIPT_DIR;
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_HEADER = BrooklynConfigKeys.SSH_CONFIG_SCRIPT_HEADER;
    public static final ConfigKey<String> SSH_CONFIG_DIRECT_HEADER = BrooklynConfigKeys.SSH_CONFIG_DIRECT_HEADER;

    // TODO other constants from elsewhere
    
}
