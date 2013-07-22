package brooklyn.location.cloud;

import brooklyn.config.ConfigKey;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.util.flags.SetFromFlag;

public interface CloudLocationConfig {

    public static final ConfigKey<String> CLOUD_ENDPOINT = LocationConfigKeys.CLOUD_ENDPOINT;
    public static final ConfigKey<String> CLOUD_REGION_ID = LocationConfigKeys.CLOUD_REGION_ID;
        
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

    public static final ConfigKey<Object> CALLER_CONTEXT = LocationConfigKeys.CALLER_CONTEXT;

}
