package brooklyn.web.console.security;

import brooklyn.config.BrooklynProperties;

public class WebConsoleSecurity {

    static SecurityProvider instance;

    private static  BrooklynProperties brooklynProperties;

    public static synchronized void setBrooklynProperties(BrooklynProperties brooklynProperties) {
        WebConsoleSecurity.brooklynProperties = brooklynProperties;
    }

    public static synchronized SecurityProvider getInstance() {
        if (instance==null) instance = new DelegatingSecurityProvider(brooklynProperties);
        return instance;
    }
}
