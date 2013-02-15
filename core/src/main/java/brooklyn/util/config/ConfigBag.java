package brooklyn.util.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Sets;

/**
 * Stores config in such a way that usage can be tracked.
 * Either {@link ConfigKey} or {@link String} keys can be inserted;
 * they will be stored internally as strings.
 * It is recommended to use {@link ConfigKey} instances to access,
 * although in some cases (such as setting fields from flags, or copying a map)
 * it may be necessary to mark things as used, or put, when only a string key is available.
 * 
 * @author alex
 */
public class ConfigBag {

    private static final Logger log = LoggerFactory.getLogger(ConfigBag.class);
    
    protected String description;
    
    private Map<String,Object> config = new LinkedHashMap<String,Object>();
    private Map<String,Object> unusedConfig = new LinkedHashMap<String,Object>();

    public ConfigBag setDescription(String description) {
        this.description = description;
        return this;
    }
    
    /** optional description used to provide context for operations */
    public String getDescription() {
        return description;
    }
    
    /** current values for all entries 
     * @return non-modifiable map of strings to object */
    public Map<String,Object> getAllConfig() {
        return Collections.unmodifiableMap(config);
    }

    /** internal map containing the current values for all entries;
     * for use where the caller wants to modify this directly and knows it is safe to do so */ 
    public Map<String,Object> getAllConfigRaw() {
        return config;
    }

    /** current values for all entries which have not yet been used 
     * @return non-modifiable map of strings to object */
    public Map<String,Object> getUnusedConfig() {
        return Collections.unmodifiableMap(unusedConfig);
    }

    /** internal map containing the current values for all entries which have not yet been used;
     * for use where the caller wants to modify this directly and knows it is safe to do so */
    public Map<String,Object> getUnusedConfigRaw() {
        return unusedConfig;
    }

    public ConfigBag putAll(Map<?,?> addlConfig) {
        if (addlConfig==null) return this;
        for (Map.Entry<?,?> e: addlConfig.entrySet()) {
            putAsStringKey(e.getKey(), e.getValue());
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T put(ConfigKey<T> key, T value) {
        return (T) putStringKey(key.getName(), value);
    }

    /** as {@link #put(ConfigKey, Object)} but returning this ConfigBag for fluent-style coding */
    public <T> ConfigBag configure(ConfigKey<T> key, T value) {
        putStringKey(key.getName(), value);
        return this;
    }
    
    protected void putAsStringKey(Object key, Object value) {
        if (key instanceof HasConfigKey<?>) key = ((HasConfigKey<?>)key).getConfigKey();
        if (key instanceof ConfigKey<?>) key = ((ConfigKey<?>)key).getName();
        if (key instanceof String) {
            putStringKey((String)key, value);
        } else {
            String message = (key == null ? "Invalid key 'null'" : "Invalid key type "+key.getClass().getCanonicalName()+" ("+key+")") +
                    "being used for configuration, ignoring";
            log.debug(message, new Throwable("Source of "+message));
            log.warn(message);
        }
    }
    
    /** recommended to use {@link #put(ConfigKey, Object)} but there are times
     * (e.g. when copying a map) where we want to put a string key directly 
     * @return */
    public Object putStringKey(String key, Object value) {
        boolean isNew = !config.containsKey(key);
        boolean isUsed = !isNew && !unusedConfig.containsKey(key);
        Object old = config.put(key, value);
        if (!isUsed) 
            unusedConfig.put(key, value);
        //if (!isNew && !isUsed) log.debug("updating config value which has already been used");
        return old;
    }

    public boolean containsKey(HasConfigKey<?> key) {
        return config.containsKey(key.getConfigKey());
    }

    public boolean containsKey(ConfigKey<?> key) {
        return config.containsKey(key.getName());
    }

    public boolean containsKey(String key) {
        return config.containsKey(key);
    }

    /** returns the value of this config key, falling back to its default (use containsKey to see whether it was contained);
     * also marks it as having been used (use peek to prevent marking as used)
     */
    public <T> T get(ConfigKey<T> key) {
        return get(key, true);
    }

    /** gets a value from a string-valued key; ConfigKey is preferred, but this is useful in some contexts (e.g. setting from flags) */
    public Object getStringKey(String key) {
        return getStringKey(key, true);
    }

    /** like get, but without marking it as used */
    public <T> T peek(ConfigKey<T> key) {
        return get(key, false);
    }

    protected <T> T get(ConfigKey<T> key, boolean remove) {
        // TODO for now, no evaluation / closure content / smart (self-extracting) keys are supported
        Object value;
        if (config.containsKey(key.getName()))
            value = getStringKey(key.getName(), remove);
        else
            value = key.getDefaultValue();
        return TypeCoercions.coerce(value, key.getType());
    }

    protected Object getStringKey(String key, boolean remove) {
        if (config.containsKey(key)) {
            if (remove) markUsed(key);
            return config.get(key);
        }
        return null;
    }

    /** indicates that a string key in the config map has been accessed */
    public void markUsed(String key) {
        unusedConfig.remove(key);
    }

    /** @deprecated don't use, remove ASAP */
    // TODO remove
    public void markFlagUsed(String key) {
        markUsed(key);
    }

    public ConfigBag removeAll(ConfigKey<?> ...keys) {
        for (ConfigKey<?> key: keys) remove(key);
        return this;
    }

    public void remove(ConfigKey<?> key) {
        config.remove(key.getName());
        unusedConfig.remove(key.getName());
    }

    public ConfigBag copy(ConfigBag other) {
        putAll(other.getAllConfig());
        markAll(Sets.difference(other.getAllConfig().keySet(), other.getUnusedConfig().keySet()));
        setDescription(other.getDescription());
        return this;
    }

    public ConfigBag markAll(Iterable<String> usedFlags) {
        for (String flag: usedFlags)
            markUsed(flag);
        return this;
    }

    /** creates a new ConfigBag instance which includes all of the supplied ConfigBag's values,
     * but which tracks usage separately (already used values are marked as such,
     * but uses in the original set will not be marked here, and vice versa) */
    public static ConfigBag newInstanceCopying(final ConfigBag configBag) {
        return new ConfigBag().copy(configBag).setDescription(configBag.getDescription());
    }
    
    /** creates a new ConfigBag instance which includes all of the supplied ConfigBag's values,
     * plus an additional set of <ConfigKey,Object> or <String,Object> pairs
     * <p>
     * values from the original set which are used here will be marked as used in the original set
     * (note: this applies even for values which are overridden and the overridden value is used);
     * however subsequent uses in the original set will not be marked here
     */
    public static ConfigBag newInstanceExtending(final ConfigBag configBag, Map<?,?> flags) {
        return new ConfigBag() {
            @Override
            public void markUsed(String key) {
                super.markUsed(key);
                configBag.markUsed(key);
            }
        }.copy(configBag).putAll(flags);
    }

    public boolean isUnused(ConfigKey<?> key) {
        return unusedConfig.containsKey(key.getName());
    }
    
}
