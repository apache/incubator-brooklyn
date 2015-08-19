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
package org.apache.brooklyn.location.cloud;

import java.util.Collection;

import com.google.common.annotations.Beta;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.location.MachineLocationCustomizer;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.location.core.LocationConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

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

    // default is just shy of common 64-char boundary, leaving 4 chars plus our salt allowance (default 4+1) which allows up to -12345678 by jclouds
    public static final ConfigKey<Integer> VM_NAME_MAX_LENGTH = ConfigKeys.newIntegerConfigKey(
        "vmNameMaxLength", "Maximum length of VM name", 60);

    public static final ConfigKey<Integer> VM_NAME_SALT_LENGTH = ConfigKeys.newIntegerConfigKey(
        "vmNameSaltLength", "Number of characters to use for a random identifier inserted in hostname "
            + "to uniquely identify machines", 4);

    public static final ConfigKey<String> WAIT_FOR_SSHABLE = ConfigKeys.newStringConfigKey("waitForSshable", 
            "Whether and how long to wait for a newly provisioned VM to be accessible via ssh; " +
            "if 'false', won't check; if 'true' uses default duration; otherwise accepts a time string e.g. '5m' (the default) or a number of milliseconds", "5m");

    public static final ConfigKey<String> WAIT_FOR_WINRM_AVAILABLE = ConfigKeys.newStringConfigKey("waitForWinRmAvailable",
            "Whether and how long to wait for a newly provisioned VM to be accessible via WinRm; " +
                    "if 'false', won't check; if 'true' uses default duration; otherwise accepts a time string e.g. '30m' (the default) or a number of milliseconds", "30m");

    public static final ConfigKey<Boolean> LOG_CREDENTIALS = ConfigKeys.newBooleanConfigKey(
            "logCredentials", 
            "Whether to log credentials of a new VM - strongly recommended never be used in production, as it is a big security hole!",
            false);

    public static final ConfigKey<Object> CALLER_CONTEXT = LocationConfigKeys.CALLER_CONTEXT;

    public static final ConfigKey<Boolean> DESTROY_ON_FAILURE = ConfigKeys.newBooleanConfigKey("destroyOnFailure", "Whether to destroy the VM if provisioningLocation.obtain() fails", true);
    
    public static final ConfigKey<Object> INBOUND_PORTS = new BasicConfigKey<Object>(Object.class, "inboundPorts", 
        "Inbound ports to be applied when creating a VM, on supported clouds " +
            "(either a single port as a String, or an Iterable<Integer> or Integer[])", null);
    @Beta
    public static final ConfigKey<Object> ADDITIONAL_INBOUND_PORTS = new BasicConfigKey<Object>(Object.class, "required.ports", 
            "Required additional ports to be applied when creating a VM, on supported clouds " +
                    "(either a single port as an Integer, or an Iterable<Integer> or Integer[])", null);
    
    public static final ConfigKey<Boolean> OS_64_BIT = ConfigKeys.newBooleanConfigKey("os64Bit", 
        "Whether to require 64-bit OS images (true), 32-bit images (false), or either (null)");
    
    public static final ConfigKey<Object> MIN_RAM = new BasicConfigKey<Object>(Object.class, "minRam",
        "Minimum amount of RAM, either as string (4gb) or number of MB (4096), for use in selecting the machine/hardware profile", null);
    
    public static final ConfigKey<Integer> MIN_CORES = new BasicConfigKey<Integer>(Integer.class, "minCores",
        "Minimum number of cores, for use in selecting the machine/hardware profile", null);
    
    public static final ConfigKey<Object> MIN_DISK = new BasicConfigKey<Object>(Object.class, "minDisk",
        "Minimum size of disk, either as string (100gb) or number of GB (100), for use in selecting the machine/hardware profile", null);

    public static final ConfigKey<String> DOMAIN_NAME = new BasicConfigKey<String>(String.class, "domainName",
        "DNS domain where the host should be created, e.g. yourdomain.com (selected clouds only)", null);

    @SuppressWarnings("serial")
    public static final ConfigKey<Collection<MachineLocationCustomizer>> MACHINE_LOCATION_CUSTOMIZERS = ConfigKeys.newConfigKey(
            new TypeToken<Collection<MachineLocationCustomizer>>() {},
            "machineCustomizers", "Optional machine customizers");
}
