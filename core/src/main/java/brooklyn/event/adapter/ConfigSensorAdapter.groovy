package brooklyn.event.adapter;

import groovy.transform.InheritConstructors
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.AttributeSensorAndConfigKey
import brooklyn.event.feed.ConfigToAttributes


/** 
 * Simple config adapter which, on registration, sets all config-attributes from config values
 * 
 * @deprecated since 0.5; use ConfigToAttributes instead
 */ 
@InheritConstructors
@Deprecated
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
        ConfigToAttributes.apply(entity);
    }

    //for selectively applying once (e.g. sub-classes of DynamicWebAppCluster that don't want to set HTTP_PORT etc!)
    public static void apply(EntityLocal entity, AttributeSensorAndConfigKey key) {
        ConfigToAttributes.apply(entity, key);
    }
}
