package brooklyn.location.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableMap;

@SuppressWarnings("serial")
public class SingleMachineProvisioningLocation<T extends MachineLocation> extends FixedListMachineProvisioningLocation<T> {

    private static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);
    
    @SetFromFlag(nullable=false)
    private String location;
    
    @SetFromFlag(nullable=false)
    private Map<?,?> locationFlags;
    
    private T singleLocation;
    private int referenceCount;
    private MachineProvisioningLocation<T> provisioningLocation;


    public SingleMachineProvisioningLocation() {
    }

    @SuppressWarnings("rawtypes")
    public SingleMachineProvisioningLocation(String location, Map locationFlags) {
        this.locationFlags = locationFlags;
        this.location = location;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public synchronized T obtain(Map flags) throws NoMachinesAvailableException {
        log.info("Flags {} passed to newLocationFromString will be ignored, using {}", flags, locationFlags);
        return obtain();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public synchronized T obtain() throws NoMachinesAvailableException {
        if (singleLocation == null) {
            if (provisioningLocation == null) {
                provisioningLocation = (MachineProvisioningLocation) getManagementContext().getLocationRegistry().resolve(
                    location, locationFlags);
            }
            singleLocation = provisioningLocation.obtain(ImmutableMap.of());
            inUse.add(singleLocation);
        }
        referenceCount++;
        return singleLocation;
    }

    @Override
    public synchronized void release(T machine) {
        if (!machine.equals(singleLocation)) {
            throw new IllegalArgumentException("Invalid machine " + machine + " passed to release, expecting: " + singleLocation);
        }
        if (--referenceCount == 0) {
            provisioningLocation.release(machine);
            singleLocation = null;
        }
        inUse.remove(machine);
    };

}
