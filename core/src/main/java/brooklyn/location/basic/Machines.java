package brooklyn.location.basic;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine;

import com.google.common.base.Optional;

/** utilities for working with MachineLocations */
public class Machines {

    private static final Logger log = LoggerFactory.getLogger(Machines.class);
    
    public static Optional<String> getSubnetHostname(Location where) {
        String hostname = null;
        if (where instanceof HasSubnetHostname) {
            hostname = ((HasSubnetHostname) where).getSubnetHostname();
        }
        if (hostname == null && where instanceof MachineLocation) {
            InetAddress addr = ((MachineLocation) where).getAddress();
            if (addr != null) hostname = addr.getHostAddress();
        }
        log.debug("computed hostname {} for {}", hostname, where);
        // TODO if Optional.absent(message) appears, could/should use that
        // TODO If no machine available, should we throw new IllegalStateException("Cannot find hostname for "+where);
        return Optional.fromNullable(hostname);
    }

    public static Optional<String> getSubnetIp(Location where) {
        // TODO Too much duplication between the ip and hostname methods
        String result = null;
        if (where instanceof HasSubnetHostname) {
            result = ((HasSubnetHostname) where).getSubnetIp();
        }
        if (result == null && where instanceof MachineLocation) {
            InetAddress addr = ((MachineLocation) where).getAddress();
            if (addr != null) result = addr.getHostAddress();
        }
        log.debug("computed hostname {} for {}", result, where);
        return Optional.fromNullable(result);
    }

    @SuppressWarnings("unchecked")
    protected static <T> Optional<T> findUniqueElement(Iterable<?> items, Class<T> type) {
        if (items==null) return null;
        Iterator<?> i = items.iterator();
        T result = null;
        while (i.hasNext()) {
            Object candidate = i.next();
            if (type.isInstance(candidate)) {
                if (result==null) result = (T)candidate;
                else {
                    if (log.isTraceEnabled())
                        log.trace("Multiple instances of "+type+" in "+items+"; ignoring");
                    return Optional.absent();
                }
            }
        }
        return Optional.fromNullable(result);
    }
    
    public static Optional<MachineLocation> findUniqueMachineLocation(Iterable<? extends Location> locations) {
        return findUniqueElement(locations, MachineLocation.class);
    }

    public static Optional<SshMachineLocation> findUniqueSshMachineLocation(Iterable<? extends Location> locations) {
        return findUniqueElement(locations, SshMachineLocation.class);
    }

    public static Optional<String> findSubnetHostname(Iterable<? extends Location> ll) {
        Optional<MachineLocation> l = findUniqueMachineLocation(ll);
        if (!l.isPresent()) {
            return Optional.absent();
//            throw new IllegalStateException("Cannot find hostname for among "+ll);
        }
        return Machines.getSubnetHostname(l.get());
    }

    public static Optional<String> findSubnetHostname(Entity entity) {
        String sh = entity.getAttribute(Attributes.SUBNET_HOSTNAME);
        if (sh!=null) return Optional.of(sh);
        return findSubnetHostname(entity.getLocations());
    }
    
    public static Optional<String> findSubnetOrPublicHostname(Entity entity) {
        String hn = entity.getAttribute(Attributes.HOSTNAME);
        if (hn!=null) {
            // attributes already set, see if there was a SUBNET_HOSTNAME set
            // note we rely on (public) hostname being set _after_ subnet_hostname,
            // to prevent tiny possibility of races resulting in hostname being returned
            // becasue subnet is still being looked up -- see MachineLifecycleEffectorTasks
            Optional<String> sn = findSubnetHostname(entity);
            if (sn.isPresent()) return sn;
            // short-circuit discovery if attributes have been set already
            return Optional.of(hn);
        }
        
        Optional<MachineLocation> l = findUniqueMachineLocation(entity.getLocations());
        if (!l.isPresent()) return Optional.absent();
        InetAddress addr = l.get().getAddress();
        if (addr==null) return Optional.absent();
        return Optional.fromNullable(addr.getHostName());
    }

    public static Optional<String> findSubnetOrPrivateIp(Entity entity) {
        // see comments in findSubnetOrPrivateHostname
        String hn = entity.getAttribute(Attributes.ADDRESS);
        if (hn!=null) {
            Optional<String> sn = findSubnetIp(entity);
            if (sn.isPresent()) return sn;
            return Optional.of(hn);
        }
        
        Optional<MachineLocation> l = findUniqueMachineLocation(entity.getLocations());
        if (!l.isPresent()) return Optional.absent();
        InetAddress addr = l.get().getAddress();
        if (addr==null) return Optional.absent();
        return Optional.fromNullable(addr.getHostAddress());
    }

    public static Optional<String> findSubnetIp(Entity entity) {
        String sh = entity.getAttribute(Attributes.SUBNET_ADDRESS);
        if (sh!=null) return Optional.of(sh);
        return findSubnetIp(entity.getLocations());
    }
    
    public static Optional<String> findSubnetIp(Iterable<? extends Location> ll) {
        // TODO Or if can't find MachineLocation, should we throw new IllegalStateException("Cannot find hostname for among "+ll);
        Optional<MachineLocation> l = findUniqueMachineLocation(ll);
        return (l.isPresent()) ? Machines.getSubnetIp(l.get()) : Optional.<String>absent();
    }

    /** returns whether it is localhost (and has warned) */
    public static boolean warnIfLocalhost(Collection<? extends Location> locations, String message) {
        if (locations.size()==1) {
            Location l = locations.iterator().next();
            if (l instanceof LocalhostMachineProvisioningLocation || l instanceof LocalhostMachine) {
                log.warn(message);
                return true;
            }
        }
        return false;
    }
    
}
