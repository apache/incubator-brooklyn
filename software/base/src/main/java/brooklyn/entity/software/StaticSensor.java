package brooklyn.entity.software;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.AddSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.config.ConfigBag;

public class StaticSensor<T> extends AddSensor<Integer> {

    public static final ConfigKey<Integer> STATIC_VALUE = ConfigKeys.newConfigKey(Integer.class, "static.value");

    private final Integer value;

    public StaticSensor(ConfigBag params) {
        super(params);
        value = params.get(STATIC_VALUE);
    }

    @Override
    public void apply(EntityLocal entity) {
        super.apply(entity);
        entity.setAttribute(Sensors.newIntegerSensor(name), value);
    }
}
