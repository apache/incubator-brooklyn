package brooklyn.entity;

import java.util.Map;

import brooklyn.entity.ConfigKey.HasConfigKey;

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
     * or given default value if not set */ 
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
    
    public ConfigMap submapMatchingGlob(String glob);
    public ConfigMap submapMatchingRegex(String regex);
    public ConfigMap submapMatchingConfigKeys(Predicate<ConfigKey<?>> filter);
    public ConfigMap submapMatching(Predicate<String> filter);

    /** convenience extension where map is principally strings or converted to strings
     * (supporting BrooklynProperties) */
    public interface StringConfigMap extends ConfigMap {
        /** @see #getFirst(Map, String...) */
        public String getFirst(String ...keys);
        /** returns the value of the first key which is defined
         * <p>
         * takes the following flags:
         * 'warnIfNone' or 'failIfNone' (both taking a boolean (to use default message) or a string (which is the message)); 
         * and 'defaultIfNone' (a default value to return if there is no such property); 
         * defaults to no warning and null default value */   
        public String getFirst(@SuppressWarnings("rawtypes") Map flags, String ...keys);        
    }
    
}
