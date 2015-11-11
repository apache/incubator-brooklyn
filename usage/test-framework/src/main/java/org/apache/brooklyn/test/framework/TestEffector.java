package org.apache.brooklyn.test.framework;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Entity that invokes an effector on another entity
 *
 * @author m4rkmckenna
 */
@ImplementedBy(value = TestEffectorImpl.class)
public interface TestEffector extends BaseTest {

    @SetFromFlag(nullable = false)
    ConfigKey<String> EFFECTOR_NAME = ConfigKeys.newConfigKey(String.class, "effector", "The name of the effector to invoke");

    ConfigKey<Map<String, ?>> EFFECTOR_PARAMS = ConfigKeys.newConfigKey(new TypeToken<Map<String, ?>>() {
    }, "params", "The parameters to pass to the effector", ImmutableMap.<String, Object>of());

    AttributeSensorAndConfigKey<Object, Object> EFFECTOR_RESULT = ConfigKeys.newSensorAndConfigKey(Object.class, "result", "The result of invoking the effector");

}