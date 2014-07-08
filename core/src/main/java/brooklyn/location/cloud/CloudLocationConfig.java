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
package brooklyn.location.cloud;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.util.flags.SetFromFlag;

public interface CloudLocationConfig {

    public static final ConfigKey<String> CLOUD_ENDPOINT = LocationConfigKeys.CLOUD_ENDPOINT;
    public static final ConfigKey<String> CLOUD_REGION_ID = LocationConfigKeys.CLOUD_REGION_ID;
    public static final ConfigKey<String> CLOUD_AVAILABILITY_ZONE_ID = LocationConfigKeys.CLOUD_AVAILABILITY_ZONE_ID;
        
    @SetFromFlag("identity")
    public static final ConfigKey<String> ACCESS_IDENTITY = LocationConfigKeys.ACCESS_IDENTITY;
    @SetFromFlag("credential")
    public static final ConfigKey<String> ACCESS_CREDENTIAL = LocationConfigKeys.ACCESS_CREDENTIAL;

    public static final ConfigKey<String> USER = LocationConfigKeys.USER;
    
    public static final ConfigKey<String> PASSWORD = LocationConfigKeys.PASSWORD;
    public static final ConfigKey<String> PUBLIC_KEY_FILE = LocationConfigKeys.PUBLIC_KEY_FILE;
    public static final ConfigKey<String> PUBLIC_KEY_DATA = LocationConfigKeys.PUBLIC_KEY_DATA;
    public static final ConfigKey<String> PRIVATE_KEY_FILE = LocationConfigKeys.PRIVATE_KEY_FILE;
    public static final ConfigKey<String> PRIVATE_KEY_DATA = LocationConfigKeys.PRIVATE_KEY_DATA;
    public static final ConfigKey<String> PRIVATE_KEY_PASSPHRASE = LocationConfigKeys.PRIVATE_KEY_PASSPHRASE;

    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PUBLIC_KEY_FILE = LocationConfigKeys.LEGACY_PUBLIC_KEY_FILE;
    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PUBLIC_KEY_DATA = LocationConfigKeys.LEGACY_PUBLIC_KEY_DATA;
    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PRIVATE_KEY_FILE = LocationConfigKeys.LEGACY_PRIVATE_KEY_FILE;
    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PRIVATE_KEY_DATA = LocationConfigKeys.LEGACY_PRIVATE_KEY_DATA;
    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PRIVATE_KEY_PASSPHRASE = LocationConfigKeys.LEGACY_PRIVATE_KEY_PASSPHRASE;

    // default is just shy of common 64-char boundary (could perhaps increase slightly...)
    public static final ConfigKey<Integer> VM_NAME_MAX_LENGTH = ConfigKeys.newIntegerConfigKey(
            "vmNameMaxLength", "Maximum length of VM name", 61);

    public static final ConfigKey<String> WAIT_FOR_SSHABLE = ConfigKeys.newStringConfigKey("waitForSshable", 
            "Whether and how long to wait for a newly provisioned VM to be accessible via ssh; " +
            "if 'false', won't check; if 'true' uses default duration; otherwise accepts a time string e.g. '5m' (the default) or a number of milliseconds", "5m");

    public static final ConfigKey<Object> CALLER_CONTEXT = LocationConfigKeys.CALLER_CONTEXT;

    public static final ConfigKey<Boolean> DESTROY_ON_FAILURE = ConfigKeys.newBooleanConfigKey("destroyOnFailure", "Whether to destroy the VM if provisioningLocation.obtain() fails", true);
    
    public static final ConfigKey<Object> INBOUND_PORTS = new BasicConfigKey<Object>(Object.class, "inboundPorts", 
        "Inbound ports to be applied when creating a VM, on supported clouds " +
            "(either a single port as a String, or an Iterable<Integer> or Integer[])", null);
    public static final ConfigKey<Boolean> OS_64_BIT = ConfigKeys.newBooleanConfigKey("os64Bit", 
        "Whether to require 64-bit OS images (true), 32-bit images (false), or either (null)");
    public static final ConfigKey<Integer> MIN_RAM = new BasicConfigKey<Integer>(Integer.class, "minRam",
        "Minimum amount of RAM (in MB), for use in selecting the machine/hardware profile", null);
    public static final ConfigKey<Integer> MIN_CORES = new BasicConfigKey<Integer>(Integer.class, "minCores",
        "Minimum number of cores, for use in selecting the machine/hardware profile", null);
    public static final ConfigKey<Double> MIN_DISK = new BasicConfigKey<Double>(Double.class, "minDisk",
        "Minimum size of disk (in GB), for use in selecting the machine/hardware profile", null);

}
