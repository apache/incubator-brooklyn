package brooklyn.util;

import brooklyn.location.geo.UtraceHostGeoLookup;
import brooklyn.util.net.Networking;

/** @deprecated since 0.6.0; use {@link Networking} */
@Deprecated
public class NetworkUtils extends Networking {

    /** @deprecated since 0.6.0; use {@link BrooklynNetworkUtils#getLocalhostExternalIp()} */
    @Deprecated
    public static String getLocalhostExternalIp() {
        return UtraceHostGeoLookup.getLocalhostExternalIp();
    }
    
}
