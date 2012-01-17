package brooklyn.location.basic

import java.util.Collection
import java.util.Map

import brooklyn.location.CoordinatesProvider
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions

/**
 * A provisioner of {@link MachineLocation}s which takes a list of machines it can connect to.
 * The collection of initial machines should be supplied in the 'machines' flag in the constructor,
 * for example a list of machines which can be SSH'd to. 
 * 
 * This can be extended to have a mechanism to make more machines to be available
 * (override provisionMore and canProvisionMore).
 */
//TODO combine with jclouds BYON
public class FixedListMachineProvisioningLocation<T extends MachineLocation> extends AbstractLocation implements MachineProvisioningLocation<T>, CoordinatesProvider {

    private Object lock;
    @SetFromFlag('machines')
    protected Set<T> machines;
    protected Set<T> inUse;

    public FixedListMachineProvisioningLocation(Map properties = [:]) {
        super(properties)

        for (SshMachineLocation location: machines) {
            if (location.parentLocation != null && !location.parentLocation.equals(this))
                throw new IllegalStateException("Machines must not have a parent location, but machine '"+location.name+"' has its parent location set");
	        addChildLocation(location)
	        location.setParentLocation(this)
        }
    }

    protected void configure(Map properties) {
        if (!lock) {
            lock = new Object();
            machines = []
            inUse = []
        }
        super.configure(properties);
    }
    
    public double getLatitude() {
        return leftoverProperties.latitude;
    }
    
    public double getLongitude() {
        return leftoverProperties.longitude;
    }

    public Set getAvailable() {
        Set a = []
        a.addAll(machines)
        a.removeAll(inUse)
        a
    }   
     
    @Override
    protected void addChildLocation(Location child) {
        super.addChildLocation(child);
        machines.add(child);
    }

    @Override
    protected boolean removeChildLocation(Location child) {
        if (inUse.contains(child)) {
            throw new IllegalStateException("Child location $child is in use; cannot remove from $this");
        }
        machines.remove(child);
        return super.removeChildLocation(child);
    }

    public boolean canProvisionMore() { return false; }
    public void provisionMore(int size) { throw new IllegalStateException("more not permitted"); }
    
    public T obtain(Map<String,? extends Object> flags) {
        T machine;
        synchronized (lock) {
            Set a = getAvailable()
            if (!a) {
                if (canProvisionMore()) {
                    provisionMore(1);
                    a = getAvailable();
                }
                if (!a)
                    throw new NoMachinesAvailableException(this);
            }
            machine = a.iterator().next();
            inUse.add(machine);
        }
        return machine;
    }

    public void release(T machine) {
        synchronized (lock) {
            if (inUse.contains(machine) == false)
                throw new IllegalStateException("Request to release machine $machine, but this machine is not currently allocated")
            inUse.remove(machine);
        }
    }
    
    Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return [:]
    }
} 
