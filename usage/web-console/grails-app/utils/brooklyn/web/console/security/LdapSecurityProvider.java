package brooklyn.web.console.security;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.text.Strings;
import brooklyn.web.console.BrooklynWebconsoleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.servlet.http.HttpSession;
import java.util.Hashtable;

/**
 * A {@link SecurityProvider} implementation that relies on LDAP to authenticate.
 *
 * @author Peter Veentjer.
 */
public class LdapSecurityProvider implements SecurityProvider {

    public static final Logger LOG = LoggerFactory.getLogger(LdapSecurityProvider.class);

    public static final String AUTHENTICATED_SESSION_TOKEN_NAME = "authenticated";

    private final String ldapUrl;
    private final String ldapRealm;

    public LdapSecurityProvider() {
        ManagementContext managementContext = ManagementContextLocator.getManagementContext();
        StringConfigMap properties = managementContext.getConfig();
        ldapUrl = properties.getFirst(BrooklynWebconsoleProperties.LDAP_URL.getPropertyName());
        if (Strings.isEmpty(ldapUrl)) {
            throw new IllegalArgumentException(String.format("property %s is not defined", BrooklynWebconsoleProperties.LDAP_URL.getPropertyName()));
        }
        ldapRealm = properties.getFirst(BrooklynWebconsoleProperties.LDAP_REALM.getPropertyName());
        if (Strings.isEmpty(ldapUrl)) {
            throw new IllegalArgumentException(String.format("property %s is not defined", BrooklynWebconsoleProperties.LDAP_REALM.getPropertyName()));
        }
    }

    public LdapSecurityProvider(String ldapUrl, String ldapRealm) {
        this.ldapUrl = ldapUrl;
        this.ldapRealm = ldapRealm;
    }

    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
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
}
