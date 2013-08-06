package brooklyn.rest.security.provider;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynWebConfig;

/** security provider which validates users against passwords according to property keys,
 * as set in {@link BrooklynWebConfig#USERS} and {@link BrooklynWebConfig#PASSWORD_FOR_USER(String)}*/
public class ExplicitUsersSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(ExplicitUsersSecurityProvider.class);
    
    public static final String AUTHENTICATION_KEY = ExplicitUsersSecurityProvider.class.getCanonicalName()+"."+"AUTHENTICATED";

    private static final Set<String> DEPRECATED_WARNING_EXPLICIT_USERS = Collections.synchronizedSet(new HashSet<String>());
    
    protected final ManagementContext mgmt;
    
    public ExplicitUsersSecurityProvider(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    @Override
    public boolean isAuthenticated(HttpSession session) {
        if (session==null) return false;
        Object value = session.getAttribute(AUTHENTICATION_KEY);
        return (value!=null);
    }

    private boolean allowAnyUserWithValidPass = false;
    
    private Set<String> allowedUsers = null;
    
    @SuppressWarnings("deprecation")
    private synchronized void initialize() {
        if (allowedUsers!=null) return;

        StringConfigMap properties = mgmt.getConfig();

        allowedUsers = new LinkedHashSet<String>();
        String users = properties.getConfig(BrooklynWebConfig.USERS);
        if (users==null) {
            users = properties.getConfig(BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__USERS);
            if (users!=null) 
                LOG.warn("Using deprecated config key "+BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__USERS.getName()+"; " +
            		"use "+BrooklynWebConfig.USERS.getName()+" instead");
        }
        if (users==null) {
            LOG.warn("Web console has no users configured; no one will be able to log in!");
        } else if ("*".equals(users)) {
            LOG.info("Web console allowing any user (so long as valid password is set)");
            allowAnyUserWithValidPass = true;
        } else {
            StringTokenizer t = new StringTokenizer(users, ",");
            while (t.hasMoreElements()) {
                allowedUsers.add((""+t.nextElement()).trim());
            }
            LOG.info("Web console allowing users: "+allowedUsers);
        }       
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        if (session==null || user==null) return false;
        
        initialize();
        
        if (!allowAnyUserWithValidPass) {
            if (!allowedUsers.contains(user)) {
                LOG.info("Web console rejecting unknown user "+user);
                return false;                
            }
        }

        BrooklynProperties properties = (BrooklynProperties) mgmt.getConfig();
        String actualP = properties.getConfig(BrooklynWebConfig.PASSWORD_FOR_USER(user));
        if (actualP==null) {
            actualP = properties.getConfig(BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__PASSWORD(user));
            if (actualP!=null) {
                if (DEPRECATED_WARNING_EXPLICIT_USERS.add(user)) {
                    LOG.warn("Web console user password set using deprecated property "+BrooklynWebConfig.SECURITY_PROVIDER_EXPLICIT__PASSWORD(user).getName()+"; " +
                		"configure using "+BrooklynWebConfig.PASSWORD_FOR_USER(user).getName()+" instead");
                }
            }
        }
        if (actualP==null) {
            LOG.warn("Web console rejecting passwordless user "+user);
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
