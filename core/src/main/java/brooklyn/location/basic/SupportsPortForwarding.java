package brooklyn.location.basic;

import brooklyn.util.net.Cidr;

import com.google.common.net.HostAndPort;

public interface SupportsPortForwarding {

    /** returns an endpoint suitable for contacting the indicated private port on this object,
     * from the given Cidr, creating it if necessary and possible; 
     * may return null if forwarding not available 
     */
    public HostAndPort getSocketEndpointFor(Cidr accessor, int privatePort);
    
    /** marker on a location to indicate that port forwarding should be done automatically
     * for attempts to access from Brooklyn
     */
    public interface RequiresPortForwarding extends SupportsPortForwarding {
    }
    
}
