package brooklyn.web.console.security;

import brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.servlet.http.HttpSession;
import java.util.Hashtable;

public class LdapSecurityProvider implements SecurityProvider {
    public final static String KEY_LDAP_URL = "brooklyn.security.ldap.url";
    public final static String KEY_LDAP_REALM = "brooklyn.security.ldap.realm";

    public static final Logger LOG = LoggerFactory.getLogger(LdapSecurityProvider.class);

    public static final String AUTHENTICATED_SESSION_TOKEN_NAME = "authenticated";

    private final String ldapUrl;
    private final String ldapRealm;

    public LdapSecurityProvider() {
        ldapUrl = (String) ConfigLoader.getConfig(KEY_LDAP_URL);
        if (Strings.isEmpty(ldapUrl)) {
            throw new IllegalArgumentException(String.format("%s is not defined in brooklyn.properties", KEY_LDAP_URL));
        }
        ldapRealm = (String) ConfigLoader.getConfig(KEY_LDAP_REALM);
        if (Strings.isEmpty(ldapUrl)) {
            throw new IllegalArgumentException(String.format("%s is not defined in brooklyn.properties", KEY_LDAP_REALM));
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
