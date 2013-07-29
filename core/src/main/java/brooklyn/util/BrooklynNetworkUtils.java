package brooklyn.util;

import java.net.InetAddress;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.location.geo.UtraceHostGeoLookup;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.net.Networking;

public class BrooklynNetworkUtils {

    /** returns the externally-facing IP address from which this host comes */
    public static String getLocalhostExternalIp() {
        return UtraceHostGeoLookup.getLocalhostExternalIp();
    }

    /** returns a IP address for localhost paying attention to a system property to prevent lookup in some cases */ 
    public static InetAddress getLocalhostInetAddress() {
        return TypeCoercions.coerce(JavaGroovyEquivalents.elvis(BrooklynServiceAttributes.LOCALHOST_IP_ADDRESS.getValue(), 
                Networking.getLocalHost()), InetAddress.class);
    }

}
