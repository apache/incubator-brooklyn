package brooklyn.util;

import java.net.InetAddress;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.location.geo.LocalhostExternalIpLoader;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.net.Networking;

public class BrooklynNetworkUtils {

    /** returns the externally-facing IP address from which this host comes, or 127.0.0.1 if not resolvable */
    public static String getLocalhostExternalIp() {
        return LocalhostExternalIpLoader.getLocalhostIpQuicklyOrDefault();
    }

    /** returns a IP address for localhost paying attention to a system property to prevent lookup in some cases */ 
    public static InetAddress getLocalhostInetAddress() {
        return TypeCoercions.coerce(JavaGroovyEquivalents.elvis(BrooklynServiceAttributes.LOCALHOST_IP_ADDRESS.getValue(), 
                Networking.getLocalHost()), InetAddress.class);
    }

}
