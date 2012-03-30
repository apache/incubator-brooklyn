package brooklyn.entity.basic

import brooklyn.event.basic.BasicConfigKey


/**
 * Dictionary of {@link ConfigKey} entries.
 */
class ConfigKeys {
    
    // FIXME Rename to VERSION, instead of SUGGESTED_VERSION? And declare as BasicAttributeSensorAndConfigKey?
    
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ String, "install.version", "Suggested version" ]
    public static final BasicConfigKey<String> SUGGESTED_INSTALL_DIR = [ String, "install.dir", "Suggested installation directory" ]
    public static final BasicConfigKey<String> SUGGESTED_RUN_DIR = [ String, "run.dir", "Suggested working directory for the running app" ]
    
    /**
     * Intention is to use this with DependentConfiguration.attributeWhenReady, to allow an entity's start
     * to block until dependents are ready. This is particularly useful when we want to block until a dependent
     * component is up, but this entity does not care about the dependent component's actual config values.
     */
    public static final BasicConfigKey<Boolean> START_LATCH = [ Boolean, "start.latch", "Latch for blocking start until ready" ]
    public static final BasicConfigKey<Boolean> INSTALL_LATCH = [ Boolean, "install.latch", "Latch for blocking install until ready" ]
    public static final BasicConfigKey<Boolean> CUSTOMIZE_LATCH = [ Boolean, "customize.latch", "Latch for blocking customize until ready" ]
    public static final BasicConfigKey<Boolean> LAUNCH_LATCH = [ Boolean, "launch.latch", "Latch for blocking launch until ready" ]
}
