package brooklyn.util.internal;

import java.util.Map;

import brooklyn.entity.ConfigKey;
import brooklyn.management.ExecutionContext;

/** Interface for resolving key values; typically implemented by the config key,
 * but discouraged for external usage.
 */
public interface ConfigKeySelfExtracting<T> extends ConfigKey<T> {
    public T extractValue(Map<?,?> configMap, ExecutionContext exec);
}
