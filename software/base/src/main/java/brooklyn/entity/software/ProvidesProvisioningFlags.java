package brooklyn.entity.software;

import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.config.ConfigBag;

import com.google.common.annotations.Beta;

/** Marker interface for an entity which supplies custom machine provisioning flags;
 * used e.g. in {@link MachineLifecycleEffectorTasks}.
 * @since 0.6.0 */
@Beta
public interface ProvidesProvisioningFlags {

    public ConfigBag obtainProvisioningFlags(MachineProvisioningLocation<?> location);
    
}
