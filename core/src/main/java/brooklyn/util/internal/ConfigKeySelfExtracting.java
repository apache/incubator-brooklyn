package brooklyn.util.internal;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;

/** Interface for resolving key values; typically implemented by the config key,
 * but discouraged for external usage.
 */
public interface ConfigKeySelfExtracting<T> extends ConfigKey<T> {
    /**
     * Extracts the value for this config key from the given map.
     */
    public T extractValue(Map<?,?> configMap, ExecutionContext exec);
    
    /**
     * @return True if there is an entry in the configMap that could be extracted
     */
    public boolean isSet(Map<?,?> configMap);
}
