package brooklyn.location.basic

import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.MachineLocation

/**
 * A provisioner of @{link MachineLocation}s.
 */
public class FixedListMachineProvisioningLocation<T extends MachineLocation> extends GeneralPurposeLocation implements MachineProvisioningLocation<T> {

    private Object lock = new Object();
    private List<T> available;
    private List<T> inUse;

    public FixedListMachineProvisioningLocation(Map attributes = [:], Collection<T> machines) {
        super(attributes)
        available = new ArrayList(machines);
        inUse = new ArrayList();
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
