package brooklyn.test.policy;

import java.util.Collections;
import java.util.Map;

import brooklyn.policy.basic.AbstractPolicy;

public class TestPolicy extends AbstractPolicy {
    // TODO: Add some config keys
    
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
