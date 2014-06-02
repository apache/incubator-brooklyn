package brooklyn.location.access;

import java.util.Collection;

import brooklyn.location.Location;

import com.google.common.annotations.Beta;
import com.google.common.net.HostAndPort;

/** Records port mappings against public IP addresses with given identifiers 
 * <p>
 * To use, create a new authoritative instance (e.g. {@link PortForwardManagerAuthority}) which will live in one
 * canonical place, then set config to be a client (e.g. {@link PortForwardManagerClient} which delegates to the
 * primary instance) so the authority is shared among all communicating parties but only persisted in one place.
 * <p>
 * One Location side (e.g. a software process in a VM) can request ({@link #acquirePublicPort(String, Location, int)})
 * an unused port on a firewall / public IP address.
 * He may then go on actually to talk to that firewall/IP to provision the forwarding rule.
 * <p>
 * Subseequently the other side can use this class {@link #lookup(Location, int)} if it knows the
 * location and private port it wishes to talk to.
 * <p>
 * Implementations typically not know anything about what the firewall/IP actually is; 
 * it just handles a unique identifier for it.
 * It is recommended, however, to {@link #recordPublicIpHostname(String, String)} an accessible hostname with the identifier 
 * (this is required in order to use {@link #lookup(Location, int)}).
 **/
@Beta
public interface PortForwardManager {

    /** reserves a unique public port on the given publicIpId
     * (often followed by {@link #associate(String, int, Location, int)}
     * to enable {@link #lookup(Location, int)}) */
    public int acquirePublicPort(String publicIpId);

    /** returns old mapping if it existed, null if it is new */
    public PortMapping acquirePublicPortExplicit(String publicIpId, int port);

    /** returns the port mapping for a given publicIpId and public port */
    public PortMapping getPortMappingWithPublicSide(String publicIpId, int publicPort);

    /** returns the subset of port mappings associated with a given public IP ID */
    public Collection<PortMapping> getPortMappingWithPublicIpId(String publicIpId);

    /** clears the given port mapping, returning the mapping if there was one */
    public PortMapping forgetPortMapping(String publicIpId, int publicPort);
    
    public boolean forgetPortMapping(PortMapping m);

    // -----------------
    
    /** records a public hostname or address to be associated with the given publicIpId for lookup purposes */
    // conceivably this may have to be access-location specific
    public void recordPublicIpHostname(String publicIpId, String hostnameOrPublicIpAddress);

    /** returns a recorded public hostname or address */
    public String getPublicIpHostname(String publicIpId);
    
    /** clears a previous call to {@link #recordPublicIpHostname(String, String)} */
    public boolean forgetPublicIpHostname(String publicIpId);

    /** returns the public host and port for use accessing the given mapping */
    // conceivably this may have to be access-location specific
    public HostAndPort getPublicHostAndPort(PortMapping m);
    // -----------------------------
    
    /** reserves a unique public port for the purpose of forwarding to the given target,
     * associated with a given location for subsequent lookup purpose;
     * if already allocated, returns the previously allocated */
    public int acquirePublicPort(String publicIpId, Location l, int privatePort);

    /** returns the public ip hostname and public port for use contacting the given endpoint;
     * null if:
     * * no publicPort is associated with this location and private port
     * * no publicIpId is associated with this location and private port
     * * no publicIpHostname is recorded against the associated publicIpId
     */
    // conceivably this may have to be access-location specific -- see recordPublicIpHostname
    public HostAndPort lookup(Location l, int privatePort);
    
    /** records a location and private port against a publicIp and public port,
     * to support {@link #lookup(Location, int)};
     * superfluous if {@link #acquirePublicPort(String, Location, int)} was used;
     * but strongly recommended if {@link #acquirePublicPortExplicit(String, int)} was used
     * e.g. if the location is not known ahead of time)
     */
    public void associate(String publicIpId, int publicPort, Location l, int privatePort);

    /** returns the subset of port mappings associated with a given location */
    public Collection<PortMapping> getLocationPublicIpIds(Location l);
        
    /** returns the mapping to a given private port, or null if none */
    public PortMapping getPortMappingWithPrivateSide(Location l, int privatePort);

    /** true if this implementation is a client which is immutable/safe for serialization
     * (ie it delegates to something on an entity or location elsewhere) */
    public boolean isClient();
    
}
