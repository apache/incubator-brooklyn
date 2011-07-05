package brooklyn.location.basic

import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.MachineLocation
import brooklyn.location.Location
import com.google.common.base.Preconditions

/**
 * A provisioner of @{link MachineLocation}s.
 */
public class FixedListMachineProvisioningLocation<T extends MachineLocation> extends AbstractLocation implements MachineProvisioningLocation<T> {

    private final Object lock = new Object();
    private final List<T> available;
    private final List<T> inUse;

    public FixedListMachineProvisioningLocation(Collection<T> machines, String name = null, Location parentLocation = null) {
        super(name, parentLocation)

        Preconditions.checkNotNull machines, "machines must not be null"
        machines.each {
            Preconditions.checkArgument it.parentLocation == null,
                "Machines must not have a parent location, but machine '%s' has its parent location set", it.name;
        }

        available = new ArrayList(machines);
        inUse = new ArrayList();
        available.each { it.setParentLocation(this); }
    }

    public T obtain() {
        T machine;
        synchronized (lock) {
            if (available.empty)
                return null;
            machine = available.pop();
            inUse.add(machine);
        }
        return machine;
    }

    public void release(T machine) {
        synchronized (lock) {
            inUse.remove(machine);
            available.add(machine);
        }
    }
}
