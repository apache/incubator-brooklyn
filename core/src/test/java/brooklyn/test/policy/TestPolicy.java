package brooklyn.test.policy;

import java.util.Collections;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

public class TestPolicy extends AbstractPolicy {
    @SetFromFlag("confName")
    public static final ConfigKey<String> CONF_NAME = ConfigKeys.newStringConfigKey("test.confName", "Configuration key, my name", "defaultval");
    
    @SetFromFlag("confFromFunction")
    public static final ConfigKey<String> CONF_FROM_FUNCTION = ConfigKeys.newStringConfigKey("test.confFromFunction", "Configuration key, from function", "defaultval");
    
    @SetFromFlag("attributeSensor")
    public static final ConfigKey<AttributeSensor<String>> TEST_ATTRIBUTE_SENSOR = BasicConfigKey.builder(new TypeToken<AttributeSensor<String>>(){})
        .name("test.attributeSensor")
        .build();
    
    public TestPolicy() {
        this(Collections.emptyMap());
    }
    
    public TestPolicy(Map<?, ?> properties) {
        super(properties);
    }

    public Map<?, ?> getLeftoverProperties() {
        return Collections.unmodifiableMap(leftoverProperties);
    }

    @Override
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        // no-op
    }
}
