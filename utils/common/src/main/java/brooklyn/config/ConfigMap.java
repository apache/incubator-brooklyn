package brooklyn.config;

import java.util.Map;

import brooklyn.config.ConfigKey.HasConfigKey;

import com.google.common.base.Predicate;

public interface ConfigMap {
    
    /** @see #getConfig(ConfigKey, Object), with default value as per the key, or null */
    public <T> T getConfig(ConfigKey<T> key);
    /** @see #getConfig(ConfigKey, Object), with default value as per the key, or null  */
    public <T> T getConfig(HasConfigKey<T> key);
    /** @see #getConfig(ConfigKey, Object), with provided default value if not set */
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue);
    /** returns value stored against the given key,
     * resolved (if it is a Task, possibly blocking), and coerced to the appropriate type, 
     * or given default value if not set, 
     * unless the default value is null in which case it returns the default*/ 
    public <T> T getConfig(ConfigKey<T> key, T defaultValue);

    /** returns the value stored against the given key, 
     * <b>not</b> any default,
     * <b>not</b> resolved (and guaranteed non-blocking)
     * and <b>not</b> type-coerced
     * @return raw, unresolved, uncoerced value of key in map, locally or inherited, but <b>not</b> any default on the key
     */
    public Object getRawConfig(ConfigKey<?> key);

    /** returns a map of all config keys to their raw (unresolved+uncoerced) contents */
    public Map<ConfigKey<?>,Object> getAllConfig();

    /** returns submap matching the given filter predicate; see ConfigPredicates for common predicates */
    public ConfigMap submap(Predicate<ConfigKey<?>> filter);

    /** returns a read-only map view which has string keys (corresponding to the config key names);
     * callers encouraged to use the typed keys (and so not use this method),
     * but in some compatibility areas having a Properties-like view is useful */
    public Map<String,Object> asMapWithStringKeys();
    
}
