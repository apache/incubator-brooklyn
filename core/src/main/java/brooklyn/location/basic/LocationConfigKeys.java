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
package brooklyn.location.basic;

import java.io.File;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.os.Os;

import com.google.common.base.CaseFormat;
import com.google.common.reflect.TypeToken;

public class LocationConfigKeys {

    public static final ConfigKey<String> LOCATION_ID = ConfigKeys.newStringConfigKey("id");
    public static final ConfigKey<String> DISPLAY_NAME = ConfigKeys.newStringConfigKey("displayName");
    public static final ConfigKey<Boolean> ENABLED = ConfigKeys.newBooleanConfigKey("enabled", "Whether the location is enabled for listing and use "
        + "(only supported for selected locations)", true);
    
    public static final ConfigKey<String> ACCESS_IDENTITY = ConfigKeys.newStringConfigKey("identity"); 
    public static final ConfigKey<String> ACCESS_CREDENTIAL = ConfigKeys.newStringConfigKey("credential"); 

    public static final ConfigKey<Double> LATITUDE = new BasicConfigKey<Double>(Double.class, "latitude"); 
    public static final ConfigKey<Double> LONGITUDE = new BasicConfigKey<Double>(Double.class, "longitude"); 

    public static final ConfigKey<String> CLOUD_PROVIDER = ConfigKeys.newStringConfigKey("provider");
    public static final ConfigKey<String> CLOUD_ENDPOINT = ConfigKeys.newStringConfigKey("endpoint");
    public static final ConfigKey<String> CLOUD_REGION_ID = ConfigKeys.newStringConfigKey("region");
    public static final ConfigKey<String> CLOUD_AVAILABILITY_ZONE_ID = ConfigKeys.newStringConfigKey("availabilityZone");

    @SuppressWarnings("serial")
    public static final ConfigKey<Set<String>> ISO_3166 = ConfigKeys.newConfigKey(new TypeToken<Set<String>>() {}, "iso3166", "ISO-3166 or ISO-3166-2 location codes"); 

    public static final ConfigKey<String> USER = ConfigKeys.newStringConfigKey("user", 
            "user account for normal access to the remote machine, defaulting to local user", System.getProperty("user.name"));
    
    public static final ConfigKey<String> PASSWORD = ConfigKeys.newStringConfigKey("password");
    public static final ConfigKey<String> PUBLIC_KEY_FILE = ConfigKeys.newStringConfigKey("publicKeyFile", "colon-separated list of ssh public key file(s) to use; if blank will infer from privateKeyFile by appending \".pub\"");
    public static final ConfigKey<String> PUBLIC_KEY_DATA = ConfigKeys.newStringConfigKey("publicKeyData", "ssh public key string to use (takes precedence over publicKeyFile)");
    public static final ConfigKey<String> PRIVATE_KEY_FILE = ConfigKeys.newStringConfigKey("privateKeyFile", "a '" + File.pathSeparator + "' separated list of ssh private key files; uses first in list that can be read",
                                                                                           Os.fromHome(".ssh/id_rsa") + File.pathSeparator + Os.fromHome(".ssh/id_dsa"));
    public static final ConfigKey<String> PRIVATE_KEY_DATA = ConfigKeys.newStringConfigKey("privateKeyData", "ssh private key string to use (takes precedence over privateKeyFile)");
    public static final ConfigKey<String> PRIVATE_KEY_PASSPHRASE = ConfigKeys.newStringConfigKey("privateKeyPassphrase");

    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PUBLIC_KEY_FILE = ConfigKeys.convert(PUBLIC_KEY_FILE, CaseFormat.LOWER_CAMEL, CaseFormat.LOWER_HYPHEN);
    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PUBLIC_KEY_DATA = ConfigKeys.convert(PUBLIC_KEY_DATA, CaseFormat.LOWER_CAMEL, CaseFormat.LOWER_HYPHEN);
    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PRIVATE_KEY_FILE = ConfigKeys.convert(PRIVATE_KEY_FILE, CaseFormat.LOWER_CAMEL, CaseFormat.LOWER_HYPHEN);
    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PRIVATE_KEY_DATA = ConfigKeys.convert(PRIVATE_KEY_DATA, CaseFormat.LOWER_CAMEL, CaseFormat.LOWER_HYPHEN);
    /** @deprecated since 0.6.0; included here so it gets picked up in auto-detect routines */ @Deprecated
    public static final ConfigKey<String> LEGACY_PRIVATE_KEY_PASSPHRASE = ConfigKeys.convert(PRIVATE_KEY_PASSPHRASE, CaseFormat.LOWER_CAMEL, CaseFormat.LOWER_HYPHEN);

    public static final ConfigKey<Object> CALLER_CONTEXT = new BasicConfigKey<Object>(Object.class, "callerContext",
            "An object whose toString is used for logging, to indicate wherefore a VM is being created");
    public static final ConfigKey<String> CLOUD_MACHINE_NAMER_CLASS = ConfigKeys.newStringConfigKey("cloudMachineNamer", "fully qualified class name of a class that extends CloudMachineNamer and has a single-parameter constructor that takes a ConfigBag");
    
}
