package brooklyn.location.basic

import java.util.Collection
import java.util.Map

import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException

import com.google.common.base.Preconditions

/**
 * A provisioner of {@link MachineLocation}s.
 */
public class FixedListMachineProvisioningLocation<T extends MachineLocation> extends AbstractLocation implements MachineProvisioningLocation<T> {

    private final Object lock = new Object();
    private final List<T> available;
    private final List<T> inUse;

    public FixedListMachineProvisioningLocation(Map properties = [:]) {
        super(properties)

        Preconditions.checkArgument properties.containsKey('machines'), "properties must include a 'machines' key"
        Preconditions.checkArgument properties.machines instanceof Collection<T>, "'machines' value must be a collection"
        Collection<T> machines = properties.remove('machines')

        machines.each {
            Preconditions.checkArgument it.parentLocation == null,
                "Machines must not have a parent location, but machine '%s' has its parent location set", it.name;
        }

        available = new ArrayList(machines);
        inUse = new ArrayList();
        available.each { it.setParentLocation(this); }
    }

    public T obtain(Map<String,? extends Object> flags) {
        T machine;
        synchronized (lock) {
            if (available.empty)
                throw new NoMachinesAvailableException(this);
            machine = available.remove(0);
            inUse.add(machine);
        }
        return machine;
    }

    public void release(T machine) {
        synchronized (lock) {
            if (inUse.contains(machine) == false)
                throw new IllegalStateException("Request to release machine $machine, but this machine is not currently allocated")
            inUse.remove(machine);
            available.add(machine);
        }
    }
    
    Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return [:]
    }
} 
