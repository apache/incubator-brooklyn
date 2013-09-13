package brooklyn.location.cloud;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
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

    // default is just shy of common 64-char boundary (could perhaps increase slightly...)
    public static final ConfigKey<Integer> VM_NAME_MAX_LENGTH = ConfigKeys.newIntegerConfigKey(
            "vmNameMaxLength", "Maximum length of VM name", 61);

    public static final ConfigKey<Object> CALLER_CONTEXT = LocationConfigKeys.CALLER_CONTEXT;

    public static final ConfigKey<Boolean> DESTROY_ON_FAILURE = ConfigKeys.newBooleanConfigKey("destroyOnFailure", "Whether to destroy the VM if provisioningLocation.obtain() fails", true);
}
