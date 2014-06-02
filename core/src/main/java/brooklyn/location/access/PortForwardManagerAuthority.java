package brooklyn.location.access;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.location.Location;

import com.google.common.net.HostAndPort;

/**
 * This implementation is not very efficient, and currently has a cap of about 50000 rules.
 * (TODO improve the efficiency and scale)
 */
public class PortForwardManagerAuthority implements PortForwardManager {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManagerAuthority.class);
    
    protected Entity owningEntity;
    
    protected final Map<String,PortMapping> mappings = new LinkedHashMap<String,PortMapping>();
    
    protected final Map<String,String> publicIpIdToHostname = new LinkedHashMap<String,String>();
    
    // horrible hack -- see javadoc above
    AtomicInteger portReserved = new AtomicInteger(11000);

    public PortForwardManagerAuthority() {
    }
    
    public PortForwardManagerAuthority(Entity owningEntity) {
        this.owningEntity = owningEntity;
    }
    
    /** reserves a unique public port on the given publicIpId
     * (often followed by {@link #associate(String, int, Location, int)}
     * to enable {@link #lookup(Location, int)}) */
    public int acquirePublicPort(String publicIpId) {
        int port;
        synchronized (this) {
            // far too simple -- see javadoc above
            port = portReserved.incrementAndGet();
            
            PortMapping mapping = new PortMapping(publicIpId, port, null, -1);
            log.debug("allocating public port "+port+" at "+publicIpId+" (no association info yet)");
            
            mappings.put(makeKey(publicIpId, port), mapping);
        }
        onChanged();
        return port;
    }

    /** returns old mapping if it existed, null if it is new */
    public PortMapping acquirePublicPortExplicit(String publicIpId, int port) {
        PortMapping mapping = new PortMapping(publicIpId, port, null, -1);
        log.debug("assigning explicit public port "+port+" at "+publicIpId);
        PortMapping result = mappings.put(makeKey(publicIpId, port), mapping);
        onChanged();
        return result;
    }

    protected String makeKey(String publicIpId, int publicPort) {
        return publicIpId+":"+publicPort;
    }

    /** returns the port mapping for a given publicIpId and public port */
    public synchronized PortMapping getPortMappingWithPublicSide(String publicIpId, int publicPort) {
        return mappings.get(makeKey(publicIpId, publicPort));
    }

    /** returns the subset of port mappings associated with a given public IP ID */
    public synchronized Collection<PortMapping> getPortMappingWithPublicIpId(String publicIpId) {
        List<PortMapping> result = new ArrayList<PortMapping>();
        for (PortMapping m: mappings.values())
            if (publicIpId.equals(m.publicIpId)) result.add(m);
        return result;
    }

    /** clears the given port mapping, returning the mapping if there was one */
    public PortMapping forgetPortMapping(String publicIpId, int publicPort) {
        PortMapping result;
        synchronized (this) {
            result = mappings.remove(makeKey(publicIpId, publicPort));
            log.debug("clearing port mapping for "+publicIpId+":"+publicPort+" - "+result);
        }
        if (result != null) onChanged();
        return result;
    }
    
    public boolean forgetPortMapping(PortMapping m) {
        return (forgetPortMapping(m.publicIpId, m.publicPort) != null);
    }

    // -----------------
    
    /** records a public hostname or address to be associated with the given publicIpId for lookup purposes */
    // conceivably this may have to be access-location specific
    public void recordPublicIpHostname(String publicIpId, String hostnameOrPublicIpAddress) {
        log.debug("recording public IP "+publicIpId+" associated with "+hostnameOrPublicIpAddress);
        synchronized (publicIpIdToHostname) {
            String old = publicIpIdToHostname.put(publicIpId, hostnameOrPublicIpAddress);
            if (old!=null && !old.equals(hostnameOrPublicIpAddress))
                log.warn("Changing hostname recorded against public IP "+publicIpId+"; from "+old+" to "+hostnameOrPublicIpAddress);
        }
        onChanged();
    }

    /** returns a recorded public hostname or address */
    public String getPublicIpHostname(String publicIpId) {
        synchronized (publicIpIdToHostname) {
            return publicIpIdToHostname.get(publicIpId);
        }
    }
    
    /** clears a previous call to {@link #recordPublicIpHostname(String, String)} */
    public boolean forgetPublicIpHostname(String publicIpId) {
        log.debug("forgetting public IP "+publicIpId+" association");
        boolean result;
        synchronized (publicIpIdToHostname) {
            result = (publicIpIdToHostname.remove(publicIpId) != null);
        }
        onChanged();
        return result;

    }

    /** returns the public host and port for use accessing the given mapping */
    // conceivably this may have to be access-location specific
    public HostAndPort getPublicHostAndPort(PortMapping m) {
        String hostname = getPublicIpHostname(m.publicIpId);
        if (hostname==null)
            throw new IllegalStateException("No public hostname associated with "+m.publicIpId+" (mapping "+m+")");
        return HostAndPort.fromParts(hostname, m.publicPort);
    }

    // -----------------------------
    
    /** reserves a unique public port for the purpose of forwarding to the given target,
     * associated with a given location for subsequent lookup purpose;
     * if already allocated, returns the previously allocated */
    public int acquirePublicPort(String publicIpId, Location l, int privatePort) {
        int publicPort;
        synchronized (this) {
            PortMapping old = getPortMappingWithPrivateSide(l, privatePort);
            // only works for 1 public IP ID per location (which is the norm)
            if (old!=null && old.publicIpId.equals(publicIpId)) {
                log.debug("request to acquire public port at "+publicIpId+" for "+l+":"+privatePort+", reusing old assignment "+old);
                return old.getPublicPort();
            }
            
            publicPort = acquirePublicPort(publicIpId);
            log.debug("request to acquire public port at "+publicIpId+" for "+l+":"+privatePort+", allocating "+publicPort);
            associateImpl(publicIpId, publicPort, l, privatePort);
        }
        onChanged();
        return publicPort;
    }

    /** returns the public ip hostname and public port for use contacting the given endpoint;
     * null if:
     * * no publicPort is associated with this location and private port
     * * no publicIpId is associated with this location and private port
     * * no publicIpHostname is recorded against the associated publicIpId
     */
    // conceivably this may have to be access-location specific -- see recordPublicIpHostname
    public synchronized HostAndPort lookup(Location l, int privatePort) {
        for (PortMapping m: mappings.values()) {
            if (l.equals(m.target) && privatePort==m.privatePort)
                return getPublicHostAndPort(m);
        }
        return null;
    }
    
    /** records a location and private port against a publicIp and public port,
     * to support {@link #lookup(Location, int)};
     * superfluous if {@link #acquirePublicPort(String, Location, int)} was used;
     * but strongly recommended if {@link #acquirePublicPortExplicit(String, int)} was used
     * e.g. if the location is not known ahead of time)
     */
    public synchronized void associate(String publicIpId, int publicPort, Location l, int privatePort) {
        synchronized (this) {
            associateImpl(publicIpId, publicPort, l, privatePort);
        }
        onChanged();
    }

    protected void associateImpl(String publicIpId, int publicPort, Location l, int privatePort) {
        PortMapping mapping = getPortMappingWithPublicSide(publicIpId, publicPort);
        log.debug("associating public port "+publicPort+" on "+publicIpId+" with private port "+privatePort+" at "+l+" ("+mapping+")");
        if (mapping==null)
            throw new IllegalStateException("No record of port mapping for "+publicIpId+":"+publicPort);
        PortMapping mapping2 = new PortMapping(publicIpId, publicPort, l, privatePort);
        mappings.put(makeKey(mapping.publicIpId, mapping.publicPort), mapping2);
    }

    /** returns the subset of port mappings associated with a given location */
    public synchronized Collection<PortMapping> getLocationPublicIpIds(Location l) {
        List<PortMapping> result = new ArrayList<PortMapping>();
        for (PortMapping m: mappings.values())
            if (l.equals(m.getTarget())) result.add(m);
        return result;
    }
        
    public synchronized PortMapping getPortMappingWithPrivateSide(Location l, int privatePort) {
        for (PortMapping m: mappings.values())
            if (l.equals(m.getTarget()) && privatePort==m.privatePort) return m;
        return null;
    }

    @Override
    public String toString() {
        return getClass().getName()+"["+mappings+"]";
    }

    @Override
    public boolean isClient() {
        return false;
    }

    protected void onChanged() {
        if (owningEntity != null) {
            ((EntityInternal)owningEntity).requestPersist();
        }
    }
}

