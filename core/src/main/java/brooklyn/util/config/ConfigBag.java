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

    /** an immutable, empty ConfigBag */
    public static final ConfigBag EMPTY = new ConfigBag().setDescription("immutable empty config bag").seal();
    
    protected String description;
    
    private Map<String,Object> config = new LinkedHashMap<String,Object>();
    private Map<String,Object> unusedConfig = new LinkedHashMap<String,Object>();
    private boolean sealed = false;

    /** creates a new ConfigBag instance, empty and ready for population */
    public static ConfigBag newInstance() {
        return new ConfigBag();
    }
    
    /** creates a new ConfigBag instance which includes all of the supplied ConfigBag's values,
     * but which tracks usage separately (already used values are marked as such,
     * but uses in the original set will not be marked here, and vice versa) */
    public static ConfigBag newInstanceCopying(final ConfigBag configBag) {
        return new ConfigBag().copy(configBag).setDescription(configBag.getDescription());
    }
    
    /** creates a new ConfigBag instance which includes all of the supplied ConfigBag's values,
     * plus an additional set of &lt;ConfigKey,Object&gt; or &lt;String,Object&gt; pairs
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

    public ConfigBag setDescription(String description) {
        if (sealed) 
            throw new IllegalStateException("Cannot set description to '"+description+"': this config bag has been sealed and is now immutable.");
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
    
    public <T> void putIfNotNull(ConfigKey<T> key, T value) {
        if (value!=null) put(key, value);
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
        if (sealed) 
            throw new IllegalStateException("Cannot insert "+key+"="+value+": this config bag has been sealed and is now immutable.");
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

    /** returns the first key in the list for which a value is explicitly set, then defaulting to defaulting value of preferred key */
    public <T> T getFirst(ConfigKey<T> preferredKey, ConfigKey<T> ...otherCurrentKeysInOrderOfPreference) {
        if (containsKey(preferredKey)) 
            return get(preferredKey);
        for (ConfigKey<T> key: otherCurrentKeysInOrderOfPreference) {
            if (containsKey(key)) 
                return get(key);
        }
        return get(preferredKey);
    }

    /** convenience for @see #getWithDeprecation(ConfigKey[], ConfigKey...) */
    public Object getWithDeprecation(ConfigKey<?> key, ConfigKey<?> ...deprecatedKeys) {
        return getWithDeprecation(new ConfigKey[] { key }, deprecatedKeys);
    }

    /** returns the value for the first key in the list for which a value is set,
     * warning if any of the deprecated keys have a value which is different to that set on the first set current key
     * (including warning if a deprecated key has a value but no current key does) */
    public Object getWithDeprecation(ConfigKey<?> currentKeysInOrderOfPreference[], ConfigKey<?> ...deprecatedKeys) {
        ConfigKey<?> deprecatedKeyProvidingValue = null;
        Object result = null;
        for (ConfigKey<?> deprecatedKey: deprecatedKeys) {
            Object x = get(deprecatedKey);
            if (x!=null) {
                if (result!=null) {
                    if (!result.equals(x)) {
                        log.warn("Conflicting values in deprecated keys, ignoring "+deprecatedKey+" value "+x+
                                " conflicting with "+deprecatedKeyProvidingValue+" value "+result);
                    } // if value the same, just ignore
                } else {
                    // new value, from deprecated key
                    result = x;
                    deprecatedKeyProvidingValue = deprecatedKey;
                }
            }
        }
        ConfigKey<?> preferredKeyProvidingValue = null;
        Object x = null;
        for (ConfigKey<?> key: currentKeysInOrderOfPreference) {
            if (containsKey(key)) {
                preferredKeyProvidingValue = key;
                x = get(preferredKeyProvidingValue);
                break;
            }
        }
        if (x!=null) {
            if (result!=null) {
                if (!result.equals(x)) {
                    log.warn("Conflicting value from deprecated key " +deprecatedKeyProvidingValue+" value "+result+
                            ", using preferred key "+preferredKeyProvidingValue+" value "+x);
                } else {
                    // preferred and deprecated keys give the same value, no need to log
                }
            } else {
                // new value from preferred key (no deprecated keys used), no need to log
            }
            return x;
        } else {
            // deprecated key only
            log.warn("Deprecated key " +deprecatedKeyProvidingValue+" detected (supplying value "+result+"), "+
                    ", use preferred key '"+preferredKeyProvidingValue+"' instead");
            return result;
        }
    }

    protected <T> T get(ConfigKey<T> key, boolean remove) {
        // TODO for now, no evaluation -- closure content / smart (self-extracting) keys are NOT supported
        // (need a clean way to inject that behaviour, as well as desired TypeCoercions)
        Object value;
        if (config.containsKey(key.getName()))
            value = getStringKey(key.getName(), remove);
        else
            value = key.getDefaultValue();
        return TypeCoercions.coerce(value, key.getTypeToken());
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

    public ConfigBag removeAll(ConfigKey<?> ...keys) {
        for (ConfigKey<?> key: keys) remove(key);
        return this;
    }

    public void remove(ConfigKey<?> key) {
        remove(key.getName());
    }

    public ConfigBag removeAll(Iterable<String> keys) {
        for (String key: keys) remove(key);
        return this;
    }

    public void remove(String key) {
        if (sealed) 
            throw new IllegalStateException("Cannot remove "+key+": this config bag has been sealed and is now immutable.");
        config.remove(key);
        unusedConfig.remove(key);
    }

    public ConfigBag copy(ConfigBag other) {
        if (sealed) 
            throw new IllegalStateException("Cannot copy "+other+" to "+this+": this config bag has been sealed and is now immutable.");
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

    public boolean isUnused(ConfigKey<?> key) {
        return unusedConfig.containsKey(key.getName());
    }
    
    /** makes this config bag immutable; any attempts to change subsequently 
     * (apart from marking fields as used) will throw an exception
     * <p>
     * copies will be unsealed however
     * <p>
     * returns this for convenience (fluent usage) */
    public ConfigBag seal() {
        sealed = true;
        config = getAllConfig();
        return this;
    }
}
