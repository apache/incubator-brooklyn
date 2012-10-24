package brooklyn.web.console.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebConsoleSecurity {
    public static final Logger LOG = LoggerFactory.getLogger(WebConsoleSecurity.class);

    private static SecurityProvider instance;

    public static synchronized SecurityProvider getInstance() {
        if (instance == null) {
            instance = new DelegatingSecurityProvider();
        }
        return instance;
    }
}
