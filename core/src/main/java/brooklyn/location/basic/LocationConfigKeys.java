package brooklyn.location.basic;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;

public class LocationConfigKeys {

    public static final ConfigKey<String> LOCATION_ID = new StringConfigKey("id");
    public static final ConfigKey<String> DISPLAY_NAME = new StringConfigKey("displayName");
    
    public static final ConfigKey<String> ACCESS_IDENTITY = new StringConfigKey("identity"); 
    public static final ConfigKey<String> ACCESS_CREDENTIAL = new StringConfigKey("credential"); 

    public static final ConfigKey<Double> LATITUDE = new BasicConfigKey<Double>(Double.class, "latitude"); 
    public static final ConfigKey<Double> LONGITUDE = new BasicConfigKey<Double>(Double.class, "longitude"); 

    public static final ConfigKey<String> CLOUD_PROVIDER = new StringConfigKey("provider");
    public static final ConfigKey<String> CLOUD_ENDPOINT = new StringConfigKey("endpoint");
    public static final ConfigKey<String> CLOUD_REGION_ID = new StringConfigKey("region");

    public static final ConfigKey<String> USER = new StringConfigKey("user", 
            "user account for normal access to the remote machine, defaulting to local user", System.getProperty("user.name"));
    
    // TODO is this used?
    public static final ConfigKey<String> PASSWORD = new StringConfigKey("password");
    public static final ConfigKey<String> PUBLIC_KEY_FILE = new StringConfigKey("publicKeyFile");
    public static final ConfigKey<String> PUBLIC_KEY_DATA = new StringConfigKey("publicKeyData");
    public static final ConfigKey<String> PRIVATE_KEY_FILE = new StringConfigKey("privateKeyFile");
    public static final ConfigKey<String> PRIVATE_KEY_DATA = new StringConfigKey("privateKeyData");
    public static final ConfigKey<String> PRIVATE_KEY_PASSPHRASE = new StringConfigKey("privateKeyPassphrase");

}
