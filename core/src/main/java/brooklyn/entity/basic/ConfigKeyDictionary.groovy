package brooklyn.entity.basic

import brooklyn.event.basic.BasicConfigKey

class ConfigKeyDictionary {

    public static final BasicConfigKey<Integer> SUGGESTED_JMX_PORT = [ Integer, "jmx.port", "Suggested JMX port" ]
    public static final BasicConfigKey<String> SUGGESTED_JMX_HOST = [ String, "jmx.host", "Suggested JMX host" ]
    
    public static final BasicConfigKey<String> SUGGESTED_HTTP_PORT = [ Integer, "http.port", "Suggested HTTP port" ]
    
}
