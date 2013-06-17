package brooklyn.location.jclouds;

import java.io.File;
import java.util.Collection;

import org.jclouds.Constants;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.domain.LoginCredentials;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.internal.ssh.SshTool;

public interface JcloudsLocationConfig extends CloudLocationConfig {

    public static final ConfigKey<String> CLOUD_PROVIDER = LocationConfigKeys.CLOUD_PROVIDER;

    public static final ConfigKey<Boolean> RUN_AS_ROOT = new BasicConfigKey<Boolean>(Boolean.class, "runAsRoot", 
            "Whether to run initial setup as root (default true)", null);
    public static final ConfigKey<String> LOGIN_USER = ConfigKeys.newStringKey("loginUser", 
            "Override the user who logs in initially to perform setup " +
            "(otherwise it is detected from the cloud or known defaults in cloud or VM OS)", null);
    public static final ConfigKey<String> LOGIN_USER_PASSWORD = ConfigKeys.newStringKey("loginUser.password", 
            "Custom password for the user who logs in initially", null);
    public static final ConfigKey<String> LOGIN_USER_PRIVATE_KEY_DATA = ConfigKeys.newStringKey("loginUser.privateKeyData", 
            "Custom private key for the user who logs in initially", null);   
    public static final ConfigKey<String> KEY_PAIR = ConfigKeys.newStringKey("keyPair", 
            "Custom keypair name to be re-used", null);
    // not supported in jclouds
//    public static final ConfigKey<String> LOGIN_USER_PRIVATE_KEY_PASSPHRASE = ConfigKeys.newStringKey("loginUser.privateKeyPassphrase", 
//            "Passphrase for the custom private key for the user who logs in initially", null);
    public static final ConfigKey<String> LOGIN_USER_PRIVATE_KEY_FILE = ConfigKeys.newStringKey("loginUser.privateKeyFile", 
            "Custom private key for the user who logs in initially", null); 
    public static final ConfigKey<String> EXTRA_PUBLIC_KEY_DATA_TO_AUTH = ConfigKeys.newStringKey("extraSshPublicKeyData", 
            "Additional public key data to add to authorized_keys", null);
    
    public static final ConfigKey<Boolean> DONT_CREATE_USER = new BasicConfigKey<Boolean>(Boolean.class, "dontCreateUser", 
            "Whether to skip creation of 'user' when provisioning machines (default false)", false);

    public static final ConfigKey<LoginCredentials> CUSTOM_CREDENTIALS = new BasicConfigKey<LoginCredentials>(LoginCredentials.class, 
            "customCredentials", "Custom jclouds LoginCredentials object to be used to connect to the VM", null);
    
    public static final ConfigKey<String> GROUP_ID = ConfigKeys.newStringKey("groupId");
    
    // jclouds compatibility
    public static final ConfigKey<String> JCLOUDS_KEY_USERNAME = ConfigKeys.newStringKey("userName", "Equivalent to 'user'; provided for jclouds compatibility", null);
    public static final ConfigKey<String> JCLOUDS_KEY_ENDPOINT = ConfigKeys.newStringKey(Constants.PROPERTY_ENDPOINT, "Equivalent to 'endpoint'; provided for jclouds compatibility", null);

    public static final ConfigKey<String> WAIT_FOR_SSHABLE = new BasicConfigKey<String>(String.class, "waitForSshable", 
            "Whether and how long to wait for a newly provisioned VM to be accessible via ssh; " +
            "if 'false', won't check; if 'true' uses default duration; otherwise accepts a time string e.g. '5m' (the default) or a number of milliseconds", "5m");
    
    public static final ConfigKey<Integer> MIN_RAM = new BasicConfigKey<Integer>(Integer.class, "minRam", 
            "Minimum amount of RAM (in MB), for use in selecting the machine/hardware profile", null);
    public static final ConfigKey<Integer> MIN_CORES = new BasicConfigKey<Integer>(Integer.class, "minCores", 
            "Minimum number of cores, for use in selecting the machine/hardware profile", null);
    public static final ConfigKey<String> HARDWARE_ID = ConfigKeys.newStringKey("hardwareId", 
            "A system-specific identifier for the hardware profile or machine type to be used when creating a VM", null);
    
    public static final ConfigKey<String> IMAGE_ID = ConfigKeys.newStringKey("imageId", 
            "A system-specific identifier for the VM image to be used when creating a VM", null);
    public static final ConfigKey<String> IMAGE_NAME_REGEX = ConfigKeys.newStringKey("imageNameRegex", 
            "A regular expression to be compared against the 'name' when selecting the VM image to be used when creating a VM", null);
    public static final ConfigKey<String> IMAGE_DESCRIPTION_REGEX = ConfigKeys.newStringKey("imageDescriptionRegex", 
            "A regular expression to be compared against the 'description' when selecting the VM image to be used when creating a VM", null);

    public static final ConfigKey<String> TEMPLATE_SPEC = ConfigKeys.newStringKey("templateSpec", 
            "A jclouds 'spec' string consisting of properties and values to be used when creating a VM " +
            "(in most cases the properties can, and should, be specified individually using other Brooklyn location config keys)", null);

    public static final ConfigKey<String> DEFAULT_IMAGE_ID = ConfigKeys.newStringKey("defaultImageId", 
            "A system-specific identifier for the VM image to be used by default when creating a VM " +
            "(if no other VM image selection criteria are supplied)", null);

    public static final ConfigKey<TemplateBuilder> TEMPLATE_BUILDER = new BasicConfigKey<TemplateBuilder>(TemplateBuilder.class, "templateBuilder", 
            "A TemplateBuilder instance provided programmatically, to be used when creating a VM", null);


    public static final ConfigKey<Object> SECURITY_GROUPS = new BasicConfigKey<Object>(Object.class, "securityGroups", 
            "Security groups to be applied when creating a VM, on supported clouds " +
            "(either a single group identifier as a String, or an Iterable<String> or String[])", null);

    public static final ConfigKey<String> USER_DATA_UUENCODED = ConfigKeys.newStringKey("userData", 
            "Arbitrary user data, as a uuencoded string, on supported clouds", null);

    public static final ConfigKey<Object> INBOUND_PORTS = new BasicConfigKey<Object>(Object.class, "inboundPorts", 
            "Inbound ports to be applied when creating a VM, on supported clouds " +
            "(either a single port as a String, or an Iterable<Integer> or Integer[])", null);

    public static final ConfigKey<Object> USER_METADATA = new BasicConfigKey<Object>(Object.class, "userMetadata", 
            "Arbitrary user metadata, as a map (or String of comma-separated key=value pairs), on supported clouds", null);

    public static final ConfigKey<Boolean> MAP_DEV_RANDOM_TO_DEV_URANDOM = new BasicConfigKey<Boolean>(
            Boolean.class, "installDevUrandom", "Map /dev/random to /dev/urandom to prevent halting on insufficient entropy", false);

    public static final ConfigKey<JcloudsLocationCustomizer> JCLOUDS_LOCATION_CUSTOMIZER = new BasicConfigKey<JcloudsLocationCustomizer>(
            JcloudsLocationCustomizer.class, "customizer", "Optional location customizer", null);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static final ConfigKey<Collection<JcloudsLocationCustomizer>> JCLOUDS_LOCATION_CUSTOMIZERS = 
            new BasicConfigKey<Collection<JcloudsLocationCustomizer>>(
            // TODO messy uncast then cast ... how to fix this?
            (Class<Collection<JcloudsLocationCustomizer>>) (Class) Collection.class,
            "customizers", "Optional location customizers", null);

    public static final ConfigKey<File> LOCAL_TEMP_DIR = SshTool.PROP_LOCAL_TEMP_DIR;

    // TODO
    
//  "noDefaultSshKeys" - hints that local ssh keys should not be read as defaults
    // this would be useful when we need to indicate a password

}
