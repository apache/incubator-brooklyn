package brooklyn.rest.security.provider;

import javax.servlet.http.HttpSession;

/**
 * The SecurityProvider is responsible for doing authentication.
 *
 * A class should either have a constructor receiving a BrooklynProperties or it should have a no-arg constructor.
 */
public interface SecurityProvider {

    public boolean isAuthenticated(HttpSession session);
    
    public boolean authenticate(HttpSession session, String user, String password);
    
    public boolean logout(HttpSession session);
}
