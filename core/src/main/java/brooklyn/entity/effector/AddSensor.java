package brooklyn.entity.effector;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/** Entity initializer which adds a sensor to an entity's type definition.
 * TODO this does not add the feed. 
 * @since 0.7.0 */
@Beta
public class AddSensor<RT,T extends Sensor<RT>> implements EntityInitializer {
    protected final T sensor;
    
    public static final ConfigKey<String> SENSOR_NAME = ConfigKeys.newStringConfigKey("name");
    public static final ConfigKey<Duration> SENSOR_PERIOD = ConfigKeys.newConfigKey(Duration.class, "period", "Period, including units e.g. 1m or 5s or 200ms");
    
    // TODO
//    public static final ConfigKey<String> SENSOR_TYPE = ConfigKeys.newStringConfigKey("targetType");

    public AddSensor(T sensor) {
        this.sensor = Preconditions.checkNotNull(sensor, "sensor");
    }
    
    @Override
    public void apply(EntityLocal entity) {
        ((EntityInternal)entity).getMutableEntityType().addSensor(sensor);
    }
    
    public static <T> AttributeSensor<T> newSensor(Class<T> type, ConfigBag params) {
        String name = Preconditions.checkNotNull(params.get(SENSOR_NAME), "name must be supplied when defining a sensor");
        return Sensors.newSensor(type, name); 
    }

}
