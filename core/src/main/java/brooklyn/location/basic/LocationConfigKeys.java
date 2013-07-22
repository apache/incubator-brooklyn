package brooklyn.location.basic;

import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;

import com.google.common.base.CaseFormat;
import com.google.common.reflect.TypeToken;

public class LocationConfigKeys {

    public static final ConfigKey<String> LOCATION_ID = ConfigKeys.newStringConfigKey("id");
    public static final ConfigKey<String> DISPLAY_NAME = ConfigKeys.newStringConfigKey("displayName");
    
    public static final ConfigKey<String> ACCESS_IDENTITY = ConfigKeys.newStringConfigKey("identity"); 
    public static final ConfigKey<String> ACCESS_CREDENTIAL = ConfigKeys.newStringConfigKey("credential"); 

    public static final ConfigKey<Double> LATITUDE = new BasicConfigKey<Double>(Double.class, "latitude"); 
    public static final ConfigKey<Double> LONGITUDE = new BasicConfigKey<Double>(Double.class, "longitude"); 

    public static final ConfigKey<String> CLOUD_PROVIDER = ConfigKeys.newStringConfigKey("provider");
    public static final ConfigKey<String> CLOUD_ENDPOINT = ConfigKeys.newStringConfigKey("endpoint");
    public static final ConfigKey<String> CLOUD_REGION_ID = ConfigKeys.newStringConfigKey("region");

    @SuppressWarnings("serial")
    public static final ConfigKey<Set<String>> ISO_3166 = ConfigKeys.newConfigKey(new TypeToken<Set<String>>() {}, "iso3166", "ISO-3166 or ISO-3166-2 location codes"); 

    public static final ConfigKey<String> USER = ConfigKeys.newStringConfigKey("user", 
            "user account for normal access to the remote machine, defaulting to local user", System.getProperty("user.name"));
    
    public static final ConfigKey<String> PASSWORD = ConfigKeys.newStringConfigKey("password");
    public static final ConfigKey<String> PUBLIC_KEY_FILE = ConfigKeys.newStringConfigKey("publicKeyFile");
    public static final ConfigKey<String> PUBLIC_KEY_DATA = ConfigKeys.newStringConfigKey("publicKeyData");
    public static final ConfigKey<String> PRIVATE_KEY_FILE = ConfigKeys.newStringConfigKey("privateKeyFile");
    public static final ConfigKey<String> PRIVATE_KEY_DATA = ConfigKeys.newStringConfigKey("privateKeyData");
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

}
