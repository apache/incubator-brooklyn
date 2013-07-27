package brooklyn.util.internal;

/** 
 * Convenience for retrieving well-defined system properties, including checking if they have been set etc.
 */
public class BrooklynSystemProperties {

    // TODO should these become ConfigKeys ?
    
    public static BooleanSystemProperty DEBUG = new BooleanSystemProperty("brooklyn.debug");
    public static BooleanSystemProperty EXPERIMENTAL = new BooleanSystemProperty("brooklyn.experimental");
    
    /** controls how long jsch delays between commands it issues */
    // -Dbrooklyn.jsch.exec.delay=100
    public static IntegerSystemProperty JSCH_EXEC_DELAY = new IntegerSystemProperty("brooklyn.jsch.exec.delay");

    /** allows specifying a particular geo lookup service (to lookup IP addresses), as the class FQN to use */
    // -Dbrooklyn.location.geo.HostGeoLookup=brooklyn.location.geo.UtraceHostGeoLookup
    public static StringSystemProperty HOST_GEO_LOOKUP_IMPL = new StringSystemProperty("brooklyn.location.geo.HostGeoLookup");

}
