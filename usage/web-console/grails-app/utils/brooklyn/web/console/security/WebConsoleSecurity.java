package brooklyn.web.console.security;

public class WebConsoleSecurity {

    static SecurityProvider instance;
    
    public static synchronized SecurityProvider getInstance() {
        if (instance==null) instance = new DelegatingSecurityProvider();
        return instance;
    }
    
}
