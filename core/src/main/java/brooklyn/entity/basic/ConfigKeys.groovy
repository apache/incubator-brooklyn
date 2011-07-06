package brooklyn.entity.basic

import brooklyn.event.basic.BasicConfigKey


class ConfigKeys {

    public static final BasicConfigKey<Integer> SUGGESTED_JMX_PORT = [ Integer, "jmx.port", "Suggested JMX port" ]
    public static final BasicConfigKey<String> SUGGESTED_JMX_HOST = [ String, "jmx.host", "Suggested JMX host" ]
    
    public static final BasicConfigKey<String> SUGGESTED_HTTP_PORT = [ Integer, "http.port", "Suggested HTTP port" ]
    
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ String, "install.version", "Suggested version" ]
    public static final BasicConfigKey<String> SUGGESTED_INSTALL_DIR = [ String, "install.dir", "Suggested installation directory" ]
    public static final BasicConfigKey<String> SUGGESTED_RUN_DIR = [ String, "run.dir", "Suggested working directory for the running app" ]
    
}
