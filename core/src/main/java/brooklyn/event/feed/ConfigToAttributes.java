package brooklyn.event.feed;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.Sensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;


/** simple config adapter for setting config-attributes from config values */ 
public class ConfigToAttributes {

    //normally just applied once, statically, not registered...
    public static void apply(EntityLocal entity) {
        for (Sensor<?> it : entity.getEntityType().getSensors()) {
            if (it instanceof AttributeSensorAndConfigKey) {
                apply(entity, (AttributeSensorAndConfigKey<?,?>)it);
            }
        }
    }

    //for selectively applying once (e.g. sub-classes of DynamicWebAppCluster that don't want to set HTTP_PORT etc!)
    public static <T> T apply(EntityLocal entity, AttributeSensorAndConfigKey<?,T> key) {
        T v = entity.getAttribute(key);
        if (v!=null) return v;
        v = key.getAsSensorValue(entity);
        if (v!=null) entity.setAttribute(key, v);
        return v;
    }
}
