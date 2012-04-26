package brooklyn.location.geo;

import java.net.InetAddress;

public interface HostGeoLookup {

    public HostGeoInfo getHostGeoInfo(InetAddress address) throws Exception;
    
}
