package brooklyn.entity.basic

import brooklyn.event.basic.BasicConfigKey


/**
 * Dictionary of {@link ConfigKey} entries.
 */
class ConfigKeys {
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ String, "install.version", "Suggested version" ]
    public static final BasicConfigKey<String> SUGGESTED_INSTALL_DIR = [ String, "install.dir", "Suggested installation directory" ]
    public static final BasicConfigKey<String> SUGGESTED_RUN_DIR = [ String, "run.dir", "Suggested working directory for the running app" ]
}
