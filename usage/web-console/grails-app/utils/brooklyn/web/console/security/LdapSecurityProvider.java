package brooklyn.web.console.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.servlet.http.HttpSession;
import java.util.Hashtable;

public class LdapSecurityProvider implements SecurityProvider {


    public static final Logger LOG = LoggerFactory.getLogger(LdapSecurityProvider.class);

    public static final String AUTHENTICATED_SESSION_TOKEN_NAME = "authenticated";

    private final String ldapUrl;
    private final String ldapRealm;

    public LdapSecurityProvider() {
        //TODO: The correct settings need to be used
        this(System.getProperty(""), System.getProperty(""));
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
            LOG.warn("Failed to authenticate user: " + user, e);
        }

        session.setAttribute(AUTHENTICATED_SESSION_TOKEN_NAME, authenticated);
        return authenticated;
    }

    private String getUserDN(String user) {
        String m_usersDn = "cn=Users,your realm";
        return "cn=" + user + "," + m_usersDn;
    }

    @Override
    public boolean isAuthenticated(HttpSession session) {
        Boolean authenticatedToken = (Boolean) session.getAttribute(AUTHENTICATED_SESSION_TOKEN_NAME);
        return authenticatedToken == null ? false : authenticatedToken;
    }

    @Override
    public boolean logout(HttpSession session) {
        session.setAttribute(AUTHENTICATED_SESSION_TOKEN_NAME, null);
        return true;
    }
}
