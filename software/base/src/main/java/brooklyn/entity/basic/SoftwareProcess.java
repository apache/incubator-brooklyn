package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

public interface SoftwareProcess extends Entity, Startable {

    @SetFromFlag("startTimeout")
    public static final ConfigKey<Integer> START_TIMEOUT = ConfigKeys.START_TIMEOUT;

    @SetFromFlag("startLatch")
    public static final ConfigKey<Boolean> START_LATCH = ConfigKeys.START_LATCH;
    
    @SetFromFlag("installLatch")
    public static final ConfigKey<Boolean> INSTALL_LATCH = ConfigKeys.INSTALL_LATCH;
    
    @SetFromFlag("customizeLatch")
    public static final ConfigKey<Boolean> CUSTOMIZE_LATCH = ConfigKeys.CUSTOMIZE_LATCH;
    
    @SetFromFlag("launchLatch")
    public static final ConfigKey<Boolean> LAUNCH_LATCH = ConfigKeys.LAUNCH_LATCH;
    
    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION;
    
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = Attributes.DOWNLOAD_URL;

    @SetFromFlag("downloadAddonUrls")
    BasicAttributeSensorAndConfigKey<Map<String,String>> DOWNLOAD_ADDON_URLS = Attributes.DOWNLOAD_ADDON_URLS;

    @SetFromFlag("installDir")
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR;
    
    @SetFromFlag("runDir")
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR;

    @SetFromFlag("env")
    public static final ConfigKey<Map> SHELL_ENVIRONMENT = new BasicConfigKey(
            Map.class, "shell.env", "Map of environment variables to pass to the runtime shell", MutableMap.of());

    @SetFromFlag("provisioningProperties")
    public static final ConfigKey<Map<String,Object>> PROVISIONING_PROPERTIES = new BasicConfigKey(
            Map.class, "provisioning.properties", 
            "Custom properties to be passed in when provisioning a new machine", MutableMap.of());
    
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final AttributeSensor<String> ADDRESS = Attributes.ADDRESS;

    public static final AttributeSensor<MachineProvisioningLocation> PROVISIONING_LOCATION = new BasicAttributeSensor<MachineProvisioningLocation>(
            MachineProvisioningLocation.class, "softwareservice.provisioningLocation", "Location used to provision a machine where this is running");
        
    public static final AttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
    
}
