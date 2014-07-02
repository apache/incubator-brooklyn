package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Supplier;
import com.google.common.reflect.TypeToken;

/**
 * An entity that supplies data as {@link AttributeSensor} values.
 * <p>
 * Usage:
 * <pre>{@code
 * EntitySpec.create(DataEntity.class)
 *          .configure(SENSOR_DATA_MAP, MutableMap.builder()
 *                  .put(Sensors.newStringSensor("string.data"), new Supplier&lt;String&gt;() { ... })
 *                  .put(Sensors.newLongSensor("long.data"), new Supplier&lt;Long&gt;() { ... })
 *                  .build());
 * }</pre>
 */
@ImplementedBy(DataEntityImpl.class)
public interface DataEntity extends Entity, Startable {

    @SetFromFlag("pollPeriod")
    ConfigKey<Long> POLL_PERIOD = ConfigKeys.newLongConfigKey(
            "data.sensorpoll", "Poll period (in milliseconds)", 1000L);

    @SetFromFlag("sensorSuppliers")
    ConfigKey<Map<AttributeSensor<?>, Supplier<?>>> SENSOR_SUPPLIER_MAP = ConfigKeys.newConfigKey(
            new TypeToken<Map<AttributeSensor<?>, Supplier<?>>>() { },
            "data.sensorSupplierMap", "Map linking sensors and data suppliers");

}
