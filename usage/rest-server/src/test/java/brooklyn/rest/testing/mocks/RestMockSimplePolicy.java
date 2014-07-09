package brooklyn.rest.testing.mocks;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.flags.SetFromFlag;

public class RestMockSimplePolicy extends AbstractPolicy {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(RestMockSimplePolicy.class);

    public RestMockSimplePolicy() {
        super();
    }

    @SuppressWarnings("rawtypes")
    public RestMockSimplePolicy(Map flags) {
        super(flags);
    }

    @SetFromFlag("sampleConfig")
    public static final ConfigKey<String> SAMPLE_CONFIG = BasicConfigKey.builder(String.class)
            .name("brooklyn.rest.mock.sample.config")
            .description("Mock sample config")
            .defaultValue("DEFAULT_VALUE")
            .reconfigurable(true)
            .build();

    @SetFromFlag
    public static final ConfigKey<Integer> INTEGER_CONFIG = BasicConfigKey.builder(Integer.class)
            .name("brooklyn.rest.mock.sample.integer")
            .description("Mock integer config")
            .defaultValue(1)
            .reconfigurable(true)
            .build();

    @Override
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        // no-op
    }
}
