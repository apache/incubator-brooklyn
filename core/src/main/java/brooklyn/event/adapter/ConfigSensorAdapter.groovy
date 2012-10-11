package brooklyn.event.adapter;

import groovy.transform.InheritConstructors
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.basic.AttributeSensorAndConfigKey;


/** simple config adapter which, on registration, sets all config-attributes from config values */ 
@InheritConstructors
public class ConfigSensorAdapter extends AbstractSensorAdapter {

    void register(SensorRegistry registry) {
        super.register(registry)
        addActivationLifecycleListeners({ apply() }, {})
    }

    public void apply() {
        apply(entity)
    }

    //normally just applied once, statically, not registered...
    public static void apply(EntityLocal entity) {
        for (Sensor it : entity.getEntityType().getSensors()) {
            if (it in AttributeSensorAndConfigKey && entity.getAttribute(it)==null) {
                entity.setAttribute(it)
            }
        }
    }

    //for selectively applying once (e.g. sub-classes of DynamicWebAppCluster that don't want to set HTTP_PORT etc!)
    public static void apply(EntityLocal entity, AttributeSensorAndConfigKey key) {
        if (entity.getAttribute(key)==null) {
            entity.setAttribute(key)
        }
    }
}
