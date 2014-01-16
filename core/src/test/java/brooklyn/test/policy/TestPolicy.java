package brooklyn.test.policy;

import java.util.Collections;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.flags.SetFromFlag;

public class TestPolicy extends AbstractPolicy {
    @SetFromFlag("confName")
    public static final ConfigKey<String> CONF_NAME = ConfigKeys.newStringConfigKey("test.confName", "Configuration key, my name", "defaultval");
    
    @SetFromFlag("confFromFunction")
    public static final ConfigKey<String> CONF_FROM_FUNCTION = ConfigKeys.newStringConfigKey("test.confFromFunction", "Configuration key, from function", "defaultval");
    
    public TestPolicy() {
        this(Collections.emptyMap());
    }
    
    public TestPolicy(Map<?, ?> properties) {
        super(properties);
    }

    public Map<?, ?> getLeftoverProperties() {
        return Collections.unmodifiableMap(leftoverProperties);
    }
}
