package brooklyn.entity.basic;

import static brooklyn.entity.basic.ConfigKeys.newBooleanConfigKey;
import static brooklyn.entity.basic.ConfigKeys.newConfigKey;
import static brooklyn.entity.basic.ConfigKeys.newConfigKeyWithPrefix;
import static brooklyn.entity.basic.ConfigKeys.newStringConfigKey;
import brooklyn.config.ConfigKey;
import brooklyn.util.internal.ssh.ShellToolConfigKeysForRemote;
import brooklyn.util.internal.ssh.SshTool;

import com.google.common.base.Preconditions;

@SuppressWarnings("deprecation")
public class BrooklynConfigKeys {

    public static final ConfigKey<String> BROOKLYN_DATA_DIR = newStringConfigKey(
            "brooklyn.datadir", "Directory for writing all brooklyn data", "/tmp/brooklyn-"+System.getProperty("user.name"));

    // FIXME Rename to VERSION, instead of SUGGESTED_VERSION? And declare as BasicAttributeSensorAndConfigKey?
    public static final ConfigKey<String> SUGGESTED_VERSION = newStringConfigKey("install.version", "Suggested version");
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = newStringConfigKey("install.dir", "Suggested installation directory");
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = newStringConfigKey("run.dir", "Suggested working directory for the running app");
    
    /**
     * Intention is to use this with DependentConfiguration.attributeWhenReady, to allow an entity's start
     * to block until dependents are ready. This is particularly useful when we want to block until a dependent
     * component is up, but this entity does not care about the dependent component's actual config values.
     */
    public static final ConfigKey<Boolean> START_LATCH = newBooleanConfigKey("start.latch", "Latch for blocking start until ready");
    public static final ConfigKey<Boolean> INSTALL_LATCH = newBooleanConfigKey("install.latch", "Latch for blocking install until ready");
    public static final ConfigKey<Boolean> CUSTOMIZE_LATCH = newBooleanConfigKey("customize.latch", "Latch for blocking customize until ready");
    public static final ConfigKey<Boolean> LAUNCH_LATCH = newBooleanConfigKey("launch.latch", "Latch for blocking launch until ready");

    public static final ConfigKey<Integer> START_TIMEOUT = newConfigKey(
            "start.timeout", "Time to wait for process and for SERVICE_UP before failing (in seconds, default 2m)", 120);
        
    /* selected properties from SshTool for external public access (e.g. putting on entities) */
    
    /** Public-facing global config keys for Brooklyn are defined in ConfigKeys, 
     * and have this prefix pre-prended to the config keys in this class. */
    public static final String BROOKLYN_SSH_CONFIG_KEY_PREFIX = "brooklyn.ssh.config.";
    
    // some checks (this line, and a few Preconditions below) that the remote values aren't null, 
    // because they have some funny circular references
    // TODO reinstate this once circular references are resolved!
//    static { assert BROOKLYN_SSH_CONFIG_KEY_PREFIX.equals(SshTool.BROOKLYN_CONFIG_KEY_PREFIX) : "static final initializer classload ordering problem"; }
    
    public static final ConfigKey<String> SSH_TOOL_CLASS = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, 
            Preconditions.checkNotNull(SshTool.PROP_TOOL_CLASS, "static final initializer classload ordering problem"));
    
    public static final ConfigKey<String> SSH_CONFIG_HOST = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_HOST);
    public static final ConfigKey<Integer> SSH_CONFIG_PORT = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_PORT);
    public static final ConfigKey<String> SSH_CONFIG_USER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_USER);
    public static final ConfigKey<String> SSH_CONFIG_PASSWORD = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_PASSWORD);
    
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_DIR = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, 
            Preconditions.checkNotNull(ShellToolConfigKeysForRemote.PROP_SCRIPT_DIR, "static final initializer classload ordering problem"));
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_HEADER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, ShellToolConfigKeysForRemote.PROP_SCRIPT_HEADER);
    public static final ConfigKey<String> SSH_CONFIG_DIRECT_HEADER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, ShellToolConfigKeysForRemote.PROP_DIRECT_HEADER);

}
