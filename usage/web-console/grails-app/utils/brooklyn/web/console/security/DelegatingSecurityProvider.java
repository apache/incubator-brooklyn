package brooklyn.web.console.security;

import javax.servlet.http.HttpSession;

import brooklyn.config.BrooklynProperties;
import brooklyn.web.console.BrooklynWebconsoleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

public class DelegatingSecurityProvider implements SecurityProvider {

    public static final Logger log = LoggerFactory.getLogger(DelegatingSecurityProvider.class);
    
    private SecurityProvider targetProvider;

    public synchronized SecurityProvider getTargetProvider() {
        if (this.targetProvider!=null) {
            return targetProvider;
        }

        BrooklynProperties brooklynProperties = (BrooklynProperties) ManagementContextLocator.getManagementContext().getConfig();

        log.info("==================== DelegatingSecurityProvider =============================");
        for(Object key: brooklynProperties.keySet()){
            log.info(key+" "+brooklynProperties.get(key));
        }
        log.info("==================== DelegatingSecurityProvider =============================");


        String className = brooklynProperties.getFirst(BrooklynWebconsoleProperties.SECURITY_PROVIDER.getPropertyName());
        if (className==null) {
            className = ExplicitUsersSecurityProvider.class.getCanonicalName();
            log.info("Web console using default security provider: "+className);
        } else {
            log.info("Web console using specified security provider: "+className);
        }

        try {
            Class<? extends SecurityProvider> clazz = (Class<? extends SecurityProvider>)Class.forName("" + className);
            //first we are going to see if the SecurityProvider has a constructor that BrooklynProperties can be passed
            //if that fails, we are going to call the no arg constructor.
            try{
                Constructor<? extends SecurityProvider> c= clazz.getConstructor(BrooklynProperties.class);
                return targetProvider = c.newInstance(brooklynProperties);
            }catch(NoSuchMethodException ex){
                //the class didn't have a constructor with argment BrooklynProperties, so we are going to make use of the no-arg constructor
                targetProvider = clazz.newInstance();
            }
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
