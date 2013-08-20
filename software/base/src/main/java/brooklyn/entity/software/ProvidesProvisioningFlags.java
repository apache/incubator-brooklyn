package brooklyn.entity.software;

import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.config.ConfigBag;

/** Marker interface for an entity which supplies custom machine provisioning flags */
public interface ProvidesProvisioningFlags {

    public ConfigBag obtainProvisioningFlags(MachineProvisioningLocation<?> location);
    
}
