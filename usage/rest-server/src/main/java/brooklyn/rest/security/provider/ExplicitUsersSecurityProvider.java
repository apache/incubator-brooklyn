package brooklyn.rest.security.provider;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.config.StringConfigMap;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynWebConfig;

public class ExplicitUsersSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(ExplicitUsersSecurityProvider.class);
    
    public static final String AUTHENTICATION_KEY = ExplicitUsersSecurityProvider.class.getCanonicalName()+"."+"AUTHENTICATED";

    protected final ManagementContext mgmt;
    
    public ExplicitUsersSecurityProvider(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    @Override
    public boolean isAuthenticated(HttpSession session) {
        if (session==null) return false;
        if (allowAnyUser) return true;
        Object value = session.getAttribute(AUTHENTICATION_KEY);
        return (value!=null);
    }

    private boolean allowAnyUserWithValidPass = false;
    private boolean allowDefaultUsers = false;
    private boolean allowAnyUser = false;
    
    private Set<String> allowedUsers = null;
    
    private synchronized void initialize() {
        if (allowedUsers!=null) return;

        StringConfigMap properties = mgmt.getConfig();

        allowedUsers = new LinkedHashSet<String>();
        String users = properties.getConfig(BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__USERS);
        if (users==null) {
            LOG.info("Web console allowing default user (admin)");
            allowDefaultUsers = true;
        } else if ("*".equals(users)) {
            LOG.info("Web console allowing any users");
            allowAnyUserWithValidPass = true;
        } else {
            LOG.info("Web console allowing users "+users);
            StringTokenizer t = new StringTokenizer(users, ",");
            while (t.hasMoreElements()) {
                allowedUsers.add((""+t.nextElement()).trim());
            }
        }       

        if (properties.getFirst(BrooklynServiceAttributes.BROOKLYN_AUTOLOGIN_USERNAME)!=null) {
            LOG.warn("Use of legacy AUTOLOGIN; replace with setting BrooklynSystemProperties.SECURITY_PROVIDER to "+AnyoneSecurityProvider.class.getCanonicalName());
            allowAnyUser = true;
        }
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        if (allowAnyUser) return true;
        if (session==null || user==null) return false;
        
        initialize();
        
        if (!allowAnyUserWithValidPass) {
            if (allowDefaultUsers) {
                if (user.equals("admin") && password.equals("password")) {
                    return allow(session, user);
                }
            } 
            if (!allowedUsers.contains(user)) {
                LOG.info("Web console rejecting unknown user "+user);
                return false;                
            }
        }

        BrooklynProperties properties = (BrooklynProperties) mgmt.getConfig();
        String actualP = properties.getConfig(BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__PASSWORD_FOR_USER(user));
        if (actualP==null) {
            actualP = properties.getConfig(BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__PASSWORD(user));
            if (actualP!=null) {
                LOG.warn("Web console user password set using legacy property "+BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__PASSWORD_FOR_USER(user).getName()+"; " +
            		"configure using "+BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__PASSWORD(user).getName()+" instead");
            }
        }
        if (actualP==null) {
            LOG.info("Web console rejecting passwordless user "+user);
            return false;
        } else if (!actualP.equals(password)){
            LOG.info("Web console rejecting bad password for user "+user);
            return false;
        } else {
            //password is good
            return allow(session, user);
        }
    }

    private boolean allow(HttpSession session, String user) {
        LOG.debug("Web console "+getClass().getSimpleName()+" authenticated user "+user);
        session.setAttribute(AUTHENTICATION_KEY, user);
        return true;
    }

    @Override
    public boolean logout(HttpSession session) { 
        if (session==null) return false;
        session.removeAttribute(AUTHENTICATION_KEY);
        return true;
    }

}
