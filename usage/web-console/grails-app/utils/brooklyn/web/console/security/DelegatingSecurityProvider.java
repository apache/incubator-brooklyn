package brooklyn.web.console.security;

import javax.servlet.http.HttpSession;

import brooklyn.config.StringConfigMap;
import brooklyn.web.console.BrooklynWebconsoleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelegatingSecurityProvider implements SecurityProvider {

    public static final Logger log = LoggerFactory.getLogger(DelegatingSecurityProvider.class);
    
    private SecurityProvider targetProvider;

    public synchronized SecurityProvider getTargetProvider() {
        if (this.targetProvider!=null) {
            return targetProvider;
        }

        StringConfigMap brooklynProperties = ManagementContextLocator.getManagementContext().getConfig();

        String className = brooklynProperties.getFirst(BrooklynWebconsoleProperties.SECURITY_PROVIDER.getPropertyName());
        if (className==null) {
            className = ExplicitUsersSecurityProvider.class.getCanonicalName();
            log.info("Web console using default security provider: "+className);
        } else {
            log.info("Web console using specified security provider: "+className);
        }

        try {
            @SuppressWarnings("unchecked")
            Class<? extends SecurityProvider> clazz = (Class<? extends SecurityProvider>)Class.forName("" + className);
            targetProvider = clazz.newInstance();
        } catch (Exception e) {
            log.warn("Web console unable to instantiate security provider "+className+"; all logins are being disallowed",e);
            targetProvider = new BlackholeSecurityProvider();
        }
        return targetProvider;
    }
    
    @Override
    public boolean isAuthenticated(HttpSession session) {
        return getTargetProvider().isAuthenticated(session);
    }

    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        return getTargetProvider().authenticate(session, user, password);
    }

    @Override
    public boolean logout(HttpSession session) { 
        return getTargetProvider().logout(session);
    }
}
