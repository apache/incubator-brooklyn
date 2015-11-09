package org.apache.brooklyn.test.framework;

import com.google.common.collect.Maps;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Entity that tests a sensor value on another entity
 *
 * @author m4rkmckenna
 */
@ImplementedBy(value = TestSensorImpl.class)
public interface TestSensor extends BaseTest {

    @SetFromFlag(nullable = false)
    ConfigKey<String> SENSOR_NAME = ConfigKeys.newConfigKey(String.class, "sensor", "Sensor to evaluate");

    ConfigKey<Map> ASSERTIONS = ConfigKeys.newConfigKey(Map.class, "assert", "Assertions to be evaluated", Maps.newLinkedHashMap());

    ConfigKey<Duration> TIMEOUT = ConfigKeys.newConfigKey(Duration.class, "timeout", "Time to wait on sensor result", new Duration(1L, TimeUnit.SECONDS));
}
