package brooklyn.management.entitlement;

import brooklyn.util.javalang.JavaClassNames;

/** indicates an authenticated web request as the entitlements context;
 * note user may still be null if no authentication was requested */
public class WebEntitlementContext implements EntitlementContext {

    final String user;
    final String sourceIp;
    final String requestUri;
    
    public WebEntitlementContext(String user, String sourceIp, String requestUri) {
        this.user = user;
        this.sourceIp = sourceIp;
        this.requestUri = requestUri;
    }
    
    @Override public String user() { return user; }
    public String sourceIp() { return sourceIp; }
    public String requestUri() { return requestUri; }

    @Override
    public String toString() {
        return JavaClassNames.simpleClassName(getClass())+"["+user+"@"+sourceIp+":"+requestUri+"]";
    }
}
