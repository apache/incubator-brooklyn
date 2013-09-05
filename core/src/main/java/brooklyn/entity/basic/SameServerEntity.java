package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

/**
 * An entity that, on start({@link MachineProvisioningLocation}), will obtain a machine
 * and pass that to each of its children by calling their {@link Startable#start(java.util.Collection)}
 * methods with that machine.
 * 
 * Thus multiple entities can be set up to run on the same machine.
 * 
 * @author aled
 */
@ImplementedBy(SameServerEntityImpl.class)
public interface SameServerEntity extends Entity, Startable {

    @SetFromFlag("provisioningProperties")
    ConfigKey<Map<String,Object>> PROVISIONING_PROPERTIES = new BasicConfigKey(
            Map.class, "provisioning.properties", 
            "Custom properties to be passed in when provisioning a new machine", MutableMap.of());
    
    AttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;

    AttributeSensor<MachineProvisioningLocation> PROVISIONING_LOCATION = new BasicAttributeSensor<MachineProvisioningLocation>(
            MachineProvisioningLocation.class, "softwareservice.provisioningLocation", "Location used to provision a machine where this is running");
    
    AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    
    AttributeSensor<String> ADDRESS = Attributes.ADDRESS;
}
