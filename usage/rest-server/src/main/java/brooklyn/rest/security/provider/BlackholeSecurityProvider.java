package brooklyn.rest.security.provider;

import javax.servlet.http.HttpSession;

/** provider who disallows everyone */
public class BlackholeSecurityProvider implements SecurityProvider {

    @Override
    public boolean isAuthenticated(HttpSession session) {
        return false;
    }

    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        return false;
    }

    @Override
    public boolean logout(HttpSession session) { 
        return true;
    }

}
