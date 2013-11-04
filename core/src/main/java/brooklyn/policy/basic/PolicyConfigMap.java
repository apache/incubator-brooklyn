package brooklyn.policy.basic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.config.ConfigKey;

import com.google.common.base.Predicate;

/**
 * @deprecated since 0.6; use {@link ConfigMapImpl} instead.
 */
@Deprecated
public class PolicyConfigMap extends ConfigMapImpl {

    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "children" of this
     * entity.
     */
    private final Map<ConfigKey<?>,Object> ownConfig = Collections.synchronizedMap(new LinkedHashMap<ConfigKey<?>, Object>());

    public PolicyConfigMap(AbstractEntityAdjunct policy) {
        super(policy);
    }

    @Override
    public PolicyConfigMap submap(Predicate<ConfigKey<?>> filter) {
        PolicyConfigMap m = new PolicyConfigMap(getAdjunct());
        for (Map.Entry<ConfigKey<?>,Object> entry: ownConfig.entrySet())
            if (filter.apply(entry.getKey()))
                m.ownConfig.put(entry.getKey(), entry.getValue());
        return m;
    }
}
