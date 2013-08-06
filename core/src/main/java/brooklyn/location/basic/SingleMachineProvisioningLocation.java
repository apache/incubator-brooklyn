package brooklyn.location.basic;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

@SuppressWarnings("serial")
public class SingleMachineProvisioningLocation<T extends MachineLocation> extends FixedListMachineProvisioningLocation<T> {

    private static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);
    
    private String location = null;
    private T singleLocation;
    private int referenceCount;
    private MachineProvisioningLocation<T> provisioningLocation;


    @SuppressWarnings("rawtypes")
    private Map locationFlags;
    

    public SingleMachineProvisioningLocation(String location) {
        this.location = location;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public synchronized T obtain(Map flags) throws NoMachinesAvailableException {
        if (singleLocation == null) {
            if (provisioningLocation == null) {
                provisioningLocation = (MachineProvisioningLocation) getManagementContext().getLocationRegistry().resolve(
                    location);
                if (flags != null) {
                    locationFlags = Collections.unmodifiableMap(Maps.newLinkedHashMap(flags));
                }
            }
            singleLocation = provisioningLocation.obtain(flags);
            inUse.add(singleLocation);
        } else {
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
