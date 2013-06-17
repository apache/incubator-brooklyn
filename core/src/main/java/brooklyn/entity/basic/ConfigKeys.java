package brooklyn.entity.basic;

import javax.annotation.Nonnull;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.internal.ssh.SshTool;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;


/**
 * Dictionary of {@link ConfigKey} entries.
 */
public class ConfigKeys {

    public static <T> ConfigKey<T> newKey(Class<T> type, String name) {
        return new BasicConfigKey<T>(type, name);
    }

    public static <T> ConfigKey<T> newKey(Class<T> type, String name, String description) {
        return new BasicConfigKey<T>(type, name, description);
    }

    public static <T> ConfigKey<T> newKey(TypeToken<T> type, String name, String description) {
        return new BasicConfigKey<T>(type, name, description);
    }

    public static <T> ConfigKey<T> newKey(Class<T> type, String name, String description, T defaultValue) {
        return new BasicConfigKey<T>(type, name, description, defaultValue);
    }

    public static <T> ConfigKey<T> newKey(TypeToken<T> type, String name, String description, T defaultValue) {
        return new BasicConfigKey<T>(type, name, description, defaultValue);
    }

    /** Infers the type from the default value */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> ConfigKey<T> newKey(String name, String description, @Nonnull T defaultValue) {
        return new BasicConfigKey<T>((Class)Preconditions.checkNotNull(defaultValue, 
                "Type must be exlicit for ConfigKey if defaultValue is null").getClass(), 
                name, description, defaultValue);
    }

    // ---- extensions to keys
    
    public static <T> ConfigKey<T> newKey(ConfigKey<T> parent, T defaultValue) {
        return new BasicConfigKey<T>(parent, defaultValue);
    }
    
    public static <T> ConfigKey<T> newPrefixedKey(String prefix, ConfigKey<T> key) {
        return new BasicConfigKey<T>(key.getTypeToken(), prefix+key.getName(), key.getDescription(), key.getDefaultValue());
    }
    
    // ---- typed keys

    public static ConfigKey<String> newStringKey(String name) {
        return newKey(String.class, name);
    }

    public static ConfigKey<String> newStringKey(String name, String description) {
        return newKey(String.class, name, description);
    }
    
    public static ConfigKey<String> newStringKey(String name, String description, String defaultValue) {
        return newKey(String.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Integer> newIntegerKey(String name) {
        return newKey(Integer.class, name);
    }

    public static ConfigKey<Integer> newIntegerKey(String name, String description) {
        return newKey(Integer.class, name, description);
    }
    
    public static ConfigKey<Integer> newIntegerKey(String name, String description, Integer defaultValue) {
        return newKey(Integer.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Long> newLongKey(String name) {
        return newKey(Long.class, name);
    }

    public static ConfigKey<Long> newLongKey(String name, String description) {
        return newKey(Long.class, name, description);
    }
    
    public static ConfigKey<Long> newLongKey(String name, String description, Long defaultValue) {
        return newKey(Long.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Double> newDoubleKey(String name) {
        return newKey(Double.class, name);
    }

    public static ConfigKey<Double> newDoubleKey(String name, String description) {
        return newKey(Double.class, name, description);
    }
    
    public static ConfigKey<Double> newDoubleKey(String name, String description, Double defaultValue) {
        return newKey(Double.class, name, description, defaultValue);
    }
    
    public static ConfigKey<Boolean> newBooleanKey(String name) {
        return newKey(Boolean.class, name);
    }

    public static ConfigKey<Boolean> newBooleanKey(String name, String description) {
        return newKey(Boolean.class, name, description);
    }
    
    public static ConfigKey<Boolean> newBooleanKey(String name, String description, Boolean defaultValue) {
        return newKey(Boolean.class, name, description, defaultValue);
    }

    // ------- keys
    
    public static final ConfigKey<String> BROOKLYN_DATA_DIR = newStringKey(
            "brooklyn.datadir", "Directory for writing all brooklyn data", "/tmp/brooklyn-"+System.getProperty("user.name"));

    // FIXME Rename to VERSION, instead of SUGGESTED_VERSION? And declare as BasicAttributeSensorAndConfigKey?
    public static final ConfigKey<String> SUGGESTED_VERSION = newStringKey("install.version", "Suggested version");
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = newStringKey("install.dir", "Suggested installation directory");
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = newStringKey("run.dir", "Suggested working directory for the running app");
    
    /**
     * Intention is to use this with DependentConfiguration.attributeWhenReady, to allow an entity's start
     * to block until dependents are ready. This is particularly useful when we want to block until a dependent
     * component is up, but this entity does not care about the dependent component's actual config values.
     */
    public static final ConfigKey<Boolean> START_LATCH = newBooleanKey("start.latch", "Latch for blocking start until ready");
    public static final ConfigKey<Boolean> INSTALL_LATCH = newBooleanKey("install.latch", "Latch for blocking install until ready");
    public static final ConfigKey<Boolean> CUSTOMIZE_LATCH = newBooleanKey("customize.latch", "Latch for blocking customize until ready");
    public static final ConfigKey<Boolean> LAUNCH_LATCH = newBooleanKey("launch.latch", "Latch for blocking launch until ready");

    public static final ConfigKey<Integer> START_TIMEOUT = newKey(
            "start.timeout", "Time to wait for SERVICE_UP to be set before failing (in seconds, default 60)", 60);
        
    /* selected properties from SshTool for external public access (e.g. putting on entities) */
    
    public static final ConfigKey<String> SSH_TOOL_CLASS = ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_TOOL_CLASS);
    public static final ConfigKey<String> SSH_CONFIG_HOST = ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_HOST);
    public static final ConfigKey<Integer> SSH_CONFIG_PORT = ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_PORT);
    public static final ConfigKey<String> SSH_CONFIG_USER = ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_USER);
    public static final ConfigKey<String> SSH_CONFIG_PASSWORD = ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_PASSWORD);
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_DIR = ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_SCRIPT_DIR);
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_HEADER = ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_SCRIPT_HEADER);
    public static final ConfigKey<String> SSH_CONFIG_DIRECT_HEADER = ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_DIRECT_HEADER);

}
