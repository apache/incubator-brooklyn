package brooklyn.web.console.security;

import javax.servlet.http.HttpSession;

public interface SecurityProvider {

    public boolean isAuthenticated(HttpSession session);
    
    public boolean authenticate(HttpSession session, String user, String password);
    
    public boolean logout(HttpSession session);
}
