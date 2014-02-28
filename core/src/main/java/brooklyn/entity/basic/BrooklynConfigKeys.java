package brooklyn.entity.basic;

import static brooklyn.entity.basic.ConfigKeys.newBooleanConfigKey;
import static brooklyn.entity.basic.ConfigKeys.newConfigKey;
import static brooklyn.entity.basic.ConfigKeys.newConfigKeyWithPrefix;
import static brooklyn.entity.basic.ConfigKeys.newStringConfigKey;

import java.io.File;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.TemplatedStringAttributeSensorAndConfigKey;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.os.Os;

import com.google.common.base.Preconditions;

public class BrooklynConfigKeys {

    public static final ConfigKey<String> BROOKLYN_PERSISTENCE_DIR = newStringConfigKey(
            "brooklyn.persistence.dir", "Directory for writing all brooklyn state", 
            Os.mergePaths("~", ".brooklyn", "brooklyn-persisted-state", "data"));

    public static final ConfigKey<String> BROOKLYN_DATA_DIR = newStringConfigKey(
            "brooklyn.datadir", "Directory for writing all brooklyn data", 
            Os.mergePaths(Os.tmp(), "brooklyn-"+Os.user())
            // TODO remove trailing separator and confirm all calls to this work
            // (also confirm all calls do a ResourceUtils.tidyPath to replace ~ with home dir!)
            +File.separator);

    // TODO Rename to VERSION, instead of SUGGESTED_VERSION? And declare as BasicAttributeSensorAndConfigKey?
    public static final ConfigKey<String> SUGGESTED_VERSION = newStringConfigKey("install.version", "Suggested version");
    
    public static final BasicAttributeSensorAndConfigKey<String> BROOKLYN_WEB_SERVER_BASE_DIR = new TemplatedStringAttributeSensorAndConfigKey("brooklyn.webserverdir", "Base directory for web-server",
            "${config['brooklyn.datadir']!'"+Os.mergePathsUnix(Os.tmp(),"brooklyn-webserver")+"'}");

    public static final BasicAttributeSensorAndConfigKey<String> INSTALL_DIR = new TemplatedStringAttributeSensorAndConfigKey("install.dir", "Directory for this software to be installed in",
        "${config['brooklyn.datadir']!'"+Os.mergePathsUnix(Os.tmp(),"brooklyn-"+Os.user())+"'}/"
            + "installs/${entity.entityType.simpleName}"
            + "${(config['install.version']??)?string('_'+(config['install.version']!'0'),'')}" // '0' not used but required by freemarker
            );
    
    public static final BasicAttributeSensorAndConfigKey<String> RUN_DIR = new TemplatedStringAttributeSensorAndConfigKey("run.dir", "Directory for this software to be run from",
        "${config['brooklyn.datadir']!'"+Os.mergePathsUnix(Os.tmp(),"brooklyn-"+Os.user())+"'}/"
            + "apps/${entity.applicationId}/"
            + "entities/${entity.entityType.simpleName}_"
            + "${entity.id}");

    public static final BasicAttributeSensorAndConfigKey<String> EXPANDED_INSTALL_DIR = new TemplatedStringAttributeSensorAndConfigKey(
            "expandedinstall.dir", 
            "Directory for installed artifacts (e.g. expanded dir after unpacking .tgz)", 
            null);

    /** @deprecated since 0.7.0; use {@link #INSTALL_DIR} */
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = INSTALL_DIR.getConfigKey();
    /** @deprecated since 0.7.0; use {@link #RUN_DIR} */
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = RUN_DIR.getConfigKey();

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
    static { assert BROOKLYN_SSH_CONFIG_KEY_PREFIX.equals(SshTool.BROOKLYN_CONFIG_KEY_PREFIX) : "static final initializer classload ordering problem"; }
    
    public static final ConfigKey<String> SSH_TOOL_CLASS = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, 
            Preconditions.checkNotNull(SshTool.PROP_TOOL_CLASS, "static final initializer classload ordering problem"));
    
    public static final ConfigKey<String> SSH_CONFIG_HOST = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_HOST);
    public static final ConfigKey<Integer> SSH_CONFIG_PORT = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_PORT);
    public static final ConfigKey<String> SSH_CONFIG_USER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_USER);
    public static final ConfigKey<String> SSH_CONFIG_PASSWORD = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, SshTool.PROP_PASSWORD);
    
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_DIR = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, 
            Preconditions.checkNotNull(ShellTool.PROP_SCRIPT_DIR, "static final initializer classload ordering problem"));
    public static final ConfigKey<String> SSH_CONFIG_SCRIPT_HEADER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, ShellTool.PROP_SCRIPT_HEADER);
    public static final ConfigKey<String> SSH_CONFIG_DIRECT_HEADER = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, ShellTool.PROP_DIRECT_HEADER);
    public static final ConfigKey<Boolean> SSH_CONFIG_NO_DELETE_SCRIPT = newConfigKeyWithPrefix(BROOKLYN_SSH_CONFIG_KEY_PREFIX, ShellTool.PROP_NO_DELETE_SCRIPT);

}
