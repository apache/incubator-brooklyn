package brooklyn.policy.basic;

import java.util.Collections;
import java.util.Map;

public class GeneralPurposePolicy extends AbstractPolicy {
    public GeneralPurposePolicy() {
        this(Collections.emptyMap());
    }
    public GeneralPurposePolicy(Map properties) {
        super(properties);
    }
}

