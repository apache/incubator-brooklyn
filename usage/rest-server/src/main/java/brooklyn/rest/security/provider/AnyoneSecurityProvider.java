package brooklyn.rest.security.provider;

import javax.servlet.http.HttpSession;

/** provider who allows everyone */
public class AnyoneSecurityProvider implements SecurityProvider {

    @Override
    public boolean isAuthenticated(HttpSession session) {
        return true;
    }

    @Override
    public boolean authenticate(HttpSession session, String user, String password) {
        return true;
    }

    @Override
    public boolean logout(HttpSession session) { 
        return true;
    }
    
}
