package brooklyn.web.console.security;

import brooklyn.config.BrooklynProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebConsoleSecurity {
    public static final Logger LOG = LoggerFactory.getLogger(WebConsoleSecurity.class);

    private static SecurityProvider instance;

    private static BrooklynProperties brooklynProperties;

    public static synchronized void setBrooklynProperties(BrooklynProperties brooklynProperties) {
        WebConsoleSecurity.brooklynProperties = brooklynProperties;
    }

    public static synchronized SecurityProvider getInstance() {
        LOG.info("=========================== WebConsoleSecurity ==============================================");
        LOG.info(brooklynProperties.toString());
        LOG.info("=========================== WebConsoleSecurity ==============================================");

        if (instance == null) {
            instance = new DelegatingSecurityProvider(brooklynProperties);
        }
        return instance;
    }
}
