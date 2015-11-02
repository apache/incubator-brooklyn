package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;

/**
 * @author m4rkmckenna on 27/10/2015.
 */

@ImplementedBy(TestEntityImpl.class)
public interface TestEntity extends Entity, Startable {

    AttributeSensorAndConfigKey<Boolean, Boolean> SIMPLE_EFFECTOR_INVOKED = ConfigKeys.newSensorAndConfigKey(Boolean.class, "simple-effector-invoked", "");
    AttributeSensorAndConfigKey<Boolean, Boolean> COMPLEX_EFFECTOR_INVOKED = ConfigKeys.newSensorAndConfigKey(Boolean.class, "complex-effector-invoked", "");
    AttributeSensorAndConfigKey<String, String> COMPLEX_EFFECTOR_STRING = ConfigKeys.newSensorAndConfigKey(String.class, "complex-effector-string", "");
    AttributeSensorAndConfigKey<Boolean, Boolean> COMPLEX_EFFECTOR_BOOLEAN = ConfigKeys.newSensorAndConfigKey(Boolean.class, "complex-effector-boolean", "");
    AttributeSensorAndConfigKey<Long, Long> COMPLEX_EFFECTOR_LONG = ConfigKeys.newSensorAndConfigKey(Long.class, "complex-effector-long", "");

    @Effector
    void simpleEffector();

    @Effector
    TestPojo complexEffector(@EffectorParam(name = "stringValue") final String stringValue,
                             @EffectorParam(name = "booleanValue") final Boolean booleanValue,
                             @EffectorParam(name = "longValue") final Long longValue);

    class TestPojo {
        private final String stringValue;
        private final Boolean booleanValue;
        private final Long longValue;

        public TestPojo(final String stringValue, final Boolean booleanValue, final Long longValue) {
            this.stringValue = stringValue;
            this.booleanValue = booleanValue;
            this.longValue = longValue;
        }

        public String getStringValue() {
            return stringValue;
        }

        public Boolean getBooleanValue() {
            return booleanValue;
        }

        public Long getLongValue() {
            return longValue;
        }
    }
}
