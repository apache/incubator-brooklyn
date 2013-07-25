package brooklyn.location.basic;

import java.util.Map;

import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;

@SuppressWarnings("serial")
public class SingleMachineProvisioningLocation<T extends MachineLocation> extends FixedListMachineProvisioningLocation<T> {

    private String location = null;
    private T singleLocation;
    private int referenceCount;
    private MachineProvisioningLocation<T> provisioningLocation;

    public SingleMachineProvisioningLocation(String location) {
        this.location = location;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public synchronized T obtain(Map flags) throws NoMachinesAvailableException {
        if (singleLocation == null) {
            if (provisioningLocation == null)
                provisioningLocation = (MachineProvisioningLocation) getManagementContext().getLocationRegistry().resolve(
                    location);
            singleLocation = provisioningLocation.obtain(flags);
            inUse.add(singleLocation);
        }
        referenceCount++;
        return singleLocation;
    }

    @Override
    public synchronized T obtain() throws NoMachinesAvailableException {
        // TODO: Is getAllConfig correct?
        return obtain(getAllConfig(true));
    }

    public synchronized void release(T machine) {
        if (--referenceCount == 0) {
            provisioningLocation.release(machine);
            singleLocation = null;
        }
    };

}
