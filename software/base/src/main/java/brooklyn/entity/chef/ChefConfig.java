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
package brooklyn.entity.chef;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.SetConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;

/** {@link ConfigKey}s used to configure the chef driver */
@Beta
public interface ChefConfig {

    public static final ConfigKey<String> CHEF_COOKBOOK_PRIMARY_NAME = ConfigKeys.newStringConfigKey("brooklyn.chef.cookbook.primary.name",
        "Namespace to use for passing data to Chef and for finding effectors");
    
    @Deprecated /** @deprecatd since 0.7.0 use #CHEF_COOKBOOK_URLS */
    @SetFromFlag("cookbooks")
    public static final MapConfigKey<String> CHEF_COOKBOOKS = new MapConfigKey<String>(String.class, "brooklyn.chef.cookbooksUrls");

    @SetFromFlag("cookbook_urls")
    public static final MapConfigKey<String> CHEF_COOKBOOK_URLS = new MapConfigKey<String>(String.class, "brooklyn.chef.cookbooksUrls");

    @SetFromFlag("converge_twice")
    public static final ConfigKey<Boolean> CHEF_RUN_CONVERGE_TWICE = ConfigKeys.newBooleanConfigKey("brooklyn.chef.converge.twice",
            "Whether to run converge commands twice if the first one fails; needed in some contexts, e.g. when switching between chef-server and chef-solo mode", false);

    @Deprecated /** @deprecated since 0.7.0 use #CHEF_LAUNCH_RUN_LIST */
    public static final SetConfigKey<String> CHEF_RUN_LIST = new SetConfigKey<String>(String.class, "brooklyn.chef.runList");
    
    /** typically set from spec, to customize the launch part of the start effector */
    @SetFromFlag("launch_run_list")
    public static final SetConfigKey<String> CHEF_LAUNCH_RUN_LIST = new SetConfigKey<String>(String.class, "brooklyn.chef.launch.runList");
    /** typically set from spec, to customize the launch part of the start effector */
    @SetFromFlag("launch_attributes")
    public static final MapConfigKey<Object> CHEF_LAUNCH_ATTRIBUTES = new MapConfigKey<Object>(Object.class, "brooklyn.chef.launch.attributes");
    
    public static enum ChefModes {
        /** Force use of Chef Solo */
        SOLO, 
        /** Force use of Knife; knife must be installed, and either 
         *  {@link ChefConfig#KNIFE_EXECUTABLE} and {@link ChefConfig#KNIFE_CONFIG_FILE} must be set 
         *  or knife on the path with valid global config set up */
        KNIFE,
        // TODO server via API
        /** Tries {@link #KNIFE} if valid, else {@link #SOLO} */
        AUTODETECT
    };

    @SetFromFlag("chef_mode")
    public static final ConfigKey<ChefModes> CHEF_MODE = ConfigKeys.newConfigKey(ChefModes.class, "brooklyn.chef.mode",
            "Whether Chef should run in solo mode, knife mode, or auto-detect", ChefModes.AUTODETECT);

    // TODO server-url for server via API mode
    
    public static final ConfigKey<String> KNIFE_SETUP_COMMANDS = ConfigKeys.newStringConfigKey("brooklyn.chef.knife.setupCommands",
            "An optional set of commands to run on localhost before invoking knife; useful if using ruby version manager for example");
    public static final ConfigKey<String> KNIFE_EXECUTABLE = ConfigKeys.newStringConfigKey("brooklyn.chef.knife.executableFile",
            "Knife command to run on the Brooklyn machine, including full path; defaults to scanning the path");
    public static final ConfigKey<String> KNIFE_CONFIG_FILE = ConfigKeys.newStringConfigKey("brooklyn.chef.knife.configFile",
            "Knife config file (typically knife.rb) to use, including full path; defaults to knife default/global config");

    // for providing some simple (ssh-based) lifecycle operations and checks
    @SetFromFlag("pid_file")
    public static final ConfigKey<String> PID_FILE = ConfigKeys.newStringConfigKey("brooklyn.chef.lifecycle.pidFile",
        "Path to PID file on remote machine, for use in checking running and stopping; may contain wildcards");
    @SetFromFlag("service_name")
    public static final ConfigKey<String> SERVICE_NAME = ConfigKeys.newStringConfigKey("brooklyn.chef.lifecycle.serviceName",
        "Name of OS service this will run as, for use in checking running and stopping");
    @SetFromFlag("windows_service_name")
    public static final ConfigKey<String> WINDOWS_SERVICE_NAME = ConfigKeys.newStringConfigKey("brooklyn.chef.lifecycle.windowsServiceName",
        "Name of OS service this will run as on Windows, if different there, for use in checking running and stopping");
    
}
