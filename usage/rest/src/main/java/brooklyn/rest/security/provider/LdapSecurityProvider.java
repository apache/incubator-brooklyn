package brooklyn.rest.security.provider;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

/**
 * A {@link SecurityProvider} implementation that relies on LDAP to authenticate.
 *
 * @author Peter Veentjer.
 */
public class LdapSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(LdapSecurityProvider.class);

    public static final String LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    public static final String AUTHENTICATED_SESSION_TOKEN_NAME = LdapSecurityProvider.class.getCanonicalName()+":"+"AUTHENTICATED";

    private final String ldapUrl;
    private final String ldapRealm;

    public LdapSecurityProvider(ManagementContext mgmt) {
        StringConfigMap properties = mgmt.getConfig();
        ldapUrl = properties.getConfig(BrooklynWebConfig.LDAP_URL);
        Strings.checkNonEmpty(ldapUrl, "LDAP security provider configuration missing required property "+BrooklynWebConfig.LDAP_URL);
        ldapRealm = properties.getConfig(BrooklynWebConfig.LDAP_REALM);
        Strings.checkNonEmpty(ldapRealm, "LDAP security provider configuration missing required property "+BrooklynWebConfig.LDAP_REALM);
    }

    public LdapSecurityProvider(String ldapUrl, String ldapRealm) {
        this.ldapUrl = ldapUrl;
        this.ldapRealm = ldapRealm;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        if (session==null || user==null) return false;
        checkCanLoad();
        
        Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, getUserDN(user));
        env.put(Context.SECURITY_CREDENTIALS, password);

        boolean authenticated = false;
        try {
            new InitialDirContext(env);
            authenticated = true;
        } catch (NamingException e) {
            LOG.warn("Failed to authenticate user: " + user);
        }

        if (session != null)
            session.setAttribute(AUTHENTICATED_SESSION_TOKEN_NAME, authenticated);
        return authenticated;
    }

    private String getUserDN(String user) {
        return "cn=" + user + "," + ldapRealm;
    }

    @Override
    public boolean isAuthenticated(HttpSession session) {
        if (session == null) return false;
        Boolean authenticatedToken = (Boolean) session.getAttribute(AUTHENTICATED_SESSION_TOKEN_NAME);
        return authenticatedToken == null ? false : authenticatedToken;
    }

    @Override
    public boolean logout(HttpSession session) {
        if (session != null)
            session.setAttribute(AUTHENTICATED_SESSION_TOKEN_NAME, null);
        return true;
    }
    
    static boolean triedLoading = false;
    public synchronized static void checkCanLoad() {
        if (triedLoading) return;
        try {
            Class.forName(LDAP_CONTEXT_FACTORY);
            triedLoading = true;
        } catch (Throwable e) {
            throw Exceptions.propagate(new ClassNotFoundException("Unable to load LDAP classes ("+LDAP_CONTEXT_FACTORY+") required for Brooklyn LDAP security provider"));
        }
    }

}
