package brooklyn.util.internal;

/** 
 * Convenience for retrieving well-defined system properties, including checking if they have been set etc.
 */
public class BrooklynSystemProperties {

    public static BooleanSystemProperty DEBUG = new BooleanSystemProperty("brooklyn.debug");
    public static BooleanSystemProperty EXPERIMENTAL = new BooleanSystemProperty("brooklyn.experimental");
    
    /** controls how long jsch delays between commands it issues */
    // -Dbrooklyn.jsch.exec.delay=100
    public static IntegerSystemProperty JSCH_EXEC_DELAY = new IntegerSystemProperty("brooklyn.jsch.exec.delay");

    /** allows specifying a particular geo lookup service (to lookup IP addresses), as the class FQN to use */
    // -Dbrooklyn.location.geo.HostGeoLookup=brooklyn.location.geo.UtraceHostGeoLookup
    public static StringSystemProperty HOST_GEO_LOOKUP_IMPL = new StringSystemProperty("brooklyn.location.geo.HostGeoLookup");

    /** e.g. brooklyn.security.provider=brooklyn.web.console.security.AnyoneSecurityProvider will allow anyone to log in;
     * default is explicitly named users, using SECURITY_PROVIDER_EXPLICIT__USERS  */
    public static StringSystemProperty SECURITY_PROVIDER = new StringSystemProperty("brooklyn.security.provider");
    /** explicitly set the users/passwords, e.g. in brooklyn.properties: 
     * brooklyn.security.explicit.users=admin
     * brooklyn.security.explicit.user.admin=password
     */
    public static StringSystemProperty SECURITY_PROVIDER_EXPLICIT__USERS = new StringSystemProperty("brooklyn.security.explicit.users");
    public static StringSystemProperty SECURITY_PROVIDER_EXPLICIT__PASSWORD(String user) {
        return new StringSystemProperty("brooklyn.security.explicit.user."+user);
    }
    
    public static class StringSystemProperty {
        public StringSystemProperty(String name) {
            this.propertyName = name;
        }

        private final String propertyName;

        public String getPropertyName() {
            return propertyName;
        }

        public boolean isAvailable() {
            String property = System.getProperty(getPropertyName());
            return property!=null;
        }
        public boolean isNonEmpty() {
            String property = System.getProperty(getPropertyName());
            return property!=null && !property.equals("");
        }
        public String getValue() {
            return System.getProperty(getPropertyName());
        }
        @Override
        public String toString() {
            return getPropertyName()+(isAvailable()?"="+getValue():"(unset)");
        }
    }

    private static class BasicDelegatingSystemProperty {
        protected final StringSystemProperty delegate;
        
        public BasicDelegatingSystemProperty(String name) {
            delegate = new StringSystemProperty(name);
        }
        public String getPropertyName() {
            return delegate.getPropertyName();
        }
        public boolean isAvailable() {
            return delegate.isAvailable();
        }
        public String toString() {
            return delegate.toString();
        }
    }
    
    public static class BooleanSystemProperty extends BasicDelegatingSystemProperty {
        public BooleanSystemProperty(String name) {
            super(name);
        }
        public boolean isEnabled() {
            // actually access system property!
            return Boolean.getBoolean(getPropertyName());
        }
    }

    public static class IntegerSystemProperty extends BasicDelegatingSystemProperty {
        public IntegerSystemProperty(String name) {
            super(name);
        }
        public int getValue() {
            return Integer.parseInt(delegate.getValue());
        }
    }

    public static class DoubleSystemProperty extends BasicDelegatingSystemProperty {
        public DoubleSystemProperty(String name) {
            super(name);
        }
        public double getValue() {
            return Double.parseDouble(delegate.getValue());
        }
    }
}
