package brooklyn.location.basic;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;

public class LocationConfigKeys {

    public static final ConfigKey<String> LOCATION_ID = ConfigKeys.newStringKey("id");
    public static final ConfigKey<String> DISPLAY_NAME = ConfigKeys.newStringKey("displayName");
    
    public static final ConfigKey<String> ACCESS_IDENTITY = ConfigKeys.newStringKey("identity"); 
    public static final ConfigKey<String> ACCESS_CREDENTIAL = ConfigKeys.newStringKey("credential"); 

    public static final ConfigKey<Double> LATITUDE = new BasicConfigKey<Double>(Double.class, "latitude"); 
    public static final ConfigKey<Double> LONGITUDE = new BasicConfigKey<Double>(Double.class, "longitude"); 

    public static final ConfigKey<String> CLOUD_PROVIDER = ConfigKeys.newStringKey("provider");
    public static final ConfigKey<String> CLOUD_ENDPOINT = ConfigKeys.newStringKey("endpoint");
    public static final ConfigKey<String> CLOUD_REGION_ID = ConfigKeys.newStringKey("region");

    public static final ConfigKey<String> USER = ConfigKeys.newStringKey("user", 
            "user account for normal access to the remote machine, defaulting to local user", System.getProperty("user.name"));
    
    public static final ConfigKey<String> PASSWORD = ConfigKeys.newStringKey("password");
    public static final ConfigKey<String> PUBLIC_KEY_FILE = ConfigKeys.newStringKey("publicKeyFile");
    public static final ConfigKey<String> PUBLIC_KEY_DATA = ConfigKeys.newStringKey("publicKeyData");
    public static final ConfigKey<String> PRIVATE_KEY_FILE = ConfigKeys.newStringKey("privateKeyFile");
    public static final ConfigKey<String> PRIVATE_KEY_DATA = ConfigKeys.newStringKey("privateKeyData");
    public static final ConfigKey<String> PRIVATE_KEY_PASSPHRASE = ConfigKeys.newStringKey("privateKeyPassphrase");

    public static final ConfigKey<Object> CALLER_CONTEXT = new BasicConfigKey<Object>(Object.class, "callerContext",
            "An object whose toString is used for logging, to indicate wherefore a VM is being created");

}
