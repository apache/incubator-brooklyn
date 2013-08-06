package brooklyn.location.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;

@SuppressWarnings("serial")
public class SingleMachineProvisioningLocation<T extends MachineLocation> extends FixedListMachineProvisioningLocation<T> {

    private String location = null;
    private T singleLocation;
    private int referenceCount;
    private MachineProvisioningLocation<T> provisioningLocation;

    public static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);

    @SuppressWarnings("rawtypes")
    private ImmutableMap locationFlags;
    

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
        } else {
            if (flags != null && locationFlags == null) {
                locationFlags = ImmutableMap.builder().putAll(flags).build();
            }
            
            if (!Objects.equal(flags, locationFlags)) {
                log.warn("Flags {} passed to subsequent call to newLocationFromString will be ignored, using {}", flags, locationFlags);
            }
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
