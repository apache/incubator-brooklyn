package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.PortRange;
import brooklyn.location.basic.HasSubnetHostname;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.Tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class SameServerEntityImpl extends AbstractEntity implements SameServerEntity {

    // TODO Duplication of code in SoftwareProcessImpl; could review and tidy
    
    private static final Logger log = LoggerFactory.getLogger(SameServerEntityImpl.class);
    
    protected void setProvisioningLocation(MachineProvisioningLocation val) {
        if (getAttribute(PROVISIONING_LOCATION) != null) throw new IllegalStateException("Cannot change provisioning location: existing="+getAttribute(PROVISIONING_LOCATION)+"; new="+val);
        setAttribute(PROVISIONING_LOCATION, val);
    }

    protected MachineProvisioningLocation getProvisioningLocation() {
        return getAttribute(PROVISIONING_LOCATION);
    }
    
    @Override
    public void restart() {
        Collection<Location> locations = getLocations();
        stop();
        start(locations);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        checkNotNull(locations, "locations");
        setAttribute(SERVICE_STATE, Lifecycle.STARTING);
        try {
            startInLocation(locations);
            
            if (getAttribute(SERVICE_STATE) == Lifecycle.STARTING) 
                setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
        } catch (Throwable t) {
            setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    protected void startInLocation(Collection<? extends Location> locations) {
        if (locations.isEmpty()) locations = getLocations();
        if (locations.size() != 1 || Iterables.getOnlyElement(locations)==null)
            throw new IllegalArgumentException("Expected one non-null location when starting "+this+", but given "+locations);
            
        startInLocation( Iterables.getOnlyElement(locations) );
    }

    protected void startInLocation(Location location) {
        if (location instanceof MachineProvisioningLocation) {
            startInLocation((MachineProvisioningLocation<? extends MachineLocation>)location);
        } else if (location instanceof MachineLocation) {
            startInLocation((MachineLocation)location);
        } else {
            throw new IllegalArgumentException("Unsupported location "+location+", when starting "+this);
        }
    }

    protected Map<String,Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        Map<String,Object> result = Maps.newLinkedHashMap();
        result.putAll(Maps.newLinkedHashMap(location.getProvisioningFlags(ImmutableList.of(getEntityType().getName()))));
        result.putAll(getConfig(PROVISIONING_PROPERTIES));
        
        for (Entity child : getChildren()) {
            result.putAll(Maps.newLinkedHashMap(location.getProvisioningFlags(ImmutableList.of(child.getEntityType().getName()))));
            result.putAll(child.getConfig(PROVISIONING_PROPERTIES));
        }
        
        if (result.get("inboundPorts") == null) {
            Collection<Integer> ports = getRequiredOpenPorts();
            if (ports != null && ports.size() > 0) result.put("inboundPorts", ports);
        }
        result.put(LocationConfigKeys.CALLER_CONTEXT.getName(), this);
        return result;
    }
    
    protected void startInLocation(final MachineProvisioningLocation<?> location) {
        final Map<String,Object> flags = obtainProvisioningFlags(location);
        if (!(location instanceof LocalhostMachineProvisioningLocation))
            log.info("Starting {}, obtaining a new location instance in {} with ports {}", new Object[] {this, location, flags.get("inboundPorts")});
        setAttribute(PROVISIONING_LOCATION, location);
        MachineLocation machine;
        try {
            machine = Tasks.withBlockingDetails("Provisioning machine in "+location, new Callable<MachineLocation>() {
                public MachineLocation call() throws NoMachinesAvailableException {
                    return location.obtain(flags);
                }});
            if (machine == null) throw new NoMachinesAvailableException("Failed to obtain machine in "+location.toString());
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        
        if (log.isDebugEnabled())
            log.debug("While starting {}, obtained new location instance {}", this, 
                    (machine instanceof SshMachineLocation ? 
                            machine+", details "+((SshMachineLocation)machine).getUser()+":"+Entities.sanitize(((SshMachineLocation)machine).getAllConfig()) 
                            : machine));
        if (!(location instanceof LocalhostMachineProvisioningLocation))
            log.info("While starting {}, obtained a new location instance {}, now preparing process there", this, machine);
        
        startInLocation(machine);
    }

    /** returns the ports that this entity wants to use, aggregated for all its child entities.
     */
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> result = Sets.newLinkedHashSet();
        result.addAll(getRequiredOpenPorts(this));
        for (Entity child : getChildren()) {
            result.addAll(getRequiredOpenPorts(child));
        }
        log.debug("getRequiredOpenPorts detected aggregated default {} for {}", result, this);
        return result;
    }

    protected Collection<Integer> getRequiredOpenPorts(Entity entity) {
        Set<Integer> ports = MutableSet.of(22);
        for (ConfigKey k: entity.getEntityType().getConfigKeys()) {
            if (PortRange.class.isAssignableFrom(k.getType())) {
                PortRange p = (PortRange) entity.getConfig(k);
                if (p != null && !p.isEmpty()) ports.add(p.iterator().next());
            }
        }
        log.debug("getRequiredOpenPorts detected default {} for {}", ports, entity);
        return ports;
    }

    public String getLocalHostname() {
        Location where = Iterables.getFirst(getLocations(), null);
        String hostname = null;
        if (where instanceof HasSubnetHostname) {
            hostname = ((HasSubnetHostname) where).getSubnetHostname();
        }
        if (hostname == null && where instanceof MachineLocation) {
            InetAddress addr = ((MachineLocation) where).getAddress();
            if (addr != null) hostname = addr.getHostAddress();
        }
        log.debug("computed hostname {} for {}", hostname, this);
        if (hostname == null)
            throw new IllegalStateException("Cannot find hostname for "+this+" at location "+where);
        return hostname;
    }

    protected void startInLocation(MachineLocation machine) {
        log.info("Starting {} on machine {}", this, machine);
        addLocations(ImmutableList.of((Location)machine));
        
        ConfigToAttributes.apply(this);
        
        StartableMethods.start(this, ImmutableList.of(machine));
        
        if (getAttribute(HOSTNAME)==null)
            setAttribute(HOSTNAME, machine.getAddress().getHostName());
        if (getAttribute(ADDRESS)==null)
            setAttribute(ADDRESS, machine.getAddress().getHostAddress());
    }
    
    @Override
    public void stop() {
        // TODO See comment in SoftwareProcessImpl.stop about race where we set 
        // SERVICE_UP=false while sensor-adapter threads may still be polling.
        
        if (getAttribute(SERVICE_STATE)==Lifecycle.STOPPED) {
            log.warn("Skipping stop of software process entity "+this+" when already stopped");
            return;
        }
        
        log.info("Stopping {} in {}", this, getLocations());
        setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
        setAttribute(SERVICE_UP, false);
        
        StartableMethods.stop(this);
        
        MachineLocation machine = removeFirstMachineLocation();
        if (machine != null) {
            stopInLocation(machine);
        }
        setAttribute(HOSTNAME, null);
        setAttribute(ADDRESS, null);
        setAttribute(SERVICE_UP, false);
        setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
        if (log.isDebugEnabled()) log.debug("Stopped software process entity "+this);
    }

    private MachineLocation removeFirstMachineLocation() {
        for (Location loc : getLocations()) {
            if (loc instanceof MachineLocation) {
                removeLocations(ImmutableList.of(loc));
                return (MachineLocation) loc;
            }
        }
        return null;
    }

    public void stopInLocation(MachineLocation machine) {
        MachineProvisioningLocation provisioner = getAttribute(PROVISIONING_LOCATION);
        
        // Release this machine (even if error trying to stop it)
        // Only release this machine if we ourselves provisioned it (e.g. it might be running other services)
        try {
            if (provisioner != null) provisioner.release(machine);
        } catch (Throwable t) {
            log.warn("Error releasing machine "+machine+" while stopping "+this+"; rethrowing ("+t+")");
            throw Exceptions.propagate(t);
        }
    }
}
