/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.util.core.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;

/**
 * Stores config in such a way that usage can be tracked.
 * Either {@link ConfigKey} or {@link String} keys can be inserted;
 * they will be stored internally as strings.
 * It is recommended to use {@link ConfigKey} instances to access,
 * although in some cases (such as setting fields from flags, or copying a map)
 * it may be necessary to mark things as used, or put, when only a string key is available.
 * <p>
 * This bag is order-preserving and thread-safe except where otherwise indicated,
 * currently by synching on this instance (but that behaviour may change).
 * <p>
 * @author alex
 */
public class ConfigBag {

    private static final Logger log = LoggerFactory.getLogger(ConfigBag.class);

    /** an immutable, empty ConfigBag */
    public static final ConfigBag EMPTY = new ConfigBag().setDescription("immutable empty config bag").seal();
    
    protected String description;
    
    private Map<String,Object> config;
    private final Map<String,Object> unusedConfig;
    private final boolean live;
    private boolean sealed = false;

    /** creates a new ConfigBag instance, empty and ready for population */
    public static ConfigBag newInstance() {
        return new ConfigBag();
    }

    /**
     * Creates an instance that is backed by a "live map" (e.g. storage in a datagrid).
     * The order-preserving nature of this class is only guaranteed if the
     * provided storage has those properties. External modifications to the store can cause
     * {@link ConcurrentModificationException} to be thrown, here or elsewhere. 
     */
    public static ConfigBag newLiveInstance(Map<String,Object> storage) {
        return new ConfigBag(checkNotNull(storage, "storage map must be specified"));
    }

    public static ConfigBag newInstance(Map<?, ?> config) {
        ConfigBag result = new ConfigBag();
        result.putAll(config);
        return result;
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
    @Beta
    public static ConfigBag newInstanceExtending(final ConfigBag parentBag) {
        return new ConfigBagExtendingParent(parentBag);
    }

    /** @see #newInstanceExtending(ConfigBag) */
    private static class ConfigBagExtendingParent extends ConfigBag {
        ConfigBag parentBag;
        private ConfigBagExtendingParent(ConfigBag parentBag) {
            this.parentBag = parentBag;
            copy(parentBag);
        }
        @Override
        public void markUsed(String key) {
            super.markUsed(key);
            if (parentBag!=null)
                parentBag.markUsed(key);
        }
    }
    
    /** As {@link #newInstanceExtending(ConfigBag)} but also putting the supplied values. */
    @Beta
    public static ConfigBag newInstanceExtending(final ConfigBag configBag, Map<?,?> optionalAdditionalValues) {
        return newInstanceExtending(configBag).putAll(optionalAdditionalValues);
    }

    /** @deprecated since 0.7.0, not used; kept only for rebind compatibility where the inner class is used 
     * (now replaced by a static class above) */
    @Beta @Deprecated
    public static ConfigBag newInstanceWithInnerClass(final ConfigBag configBag, Map<?,?> optionalAdditionalValues) {
        return new ConfigBag() {
            @Override
            public void markUsed(String key) {
                super.markUsed(key);
                configBag.markUsed(key);
            }
        }.copy(configBag).putAll(optionalAdditionalValues);
    }

    public ConfigBag() {
        config = new LinkedHashMap<String,Object>();
        unusedConfig = new LinkedHashMap<String,Object>();
        live = false;
    }
    
    private ConfigBag(Map<String,Object> storage) {
        this.config = storage;
        unusedConfig = new LinkedHashMap<String,Object>();
        live = true;
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
    public synchronized Map<String,Object> getAllConfig() {
        return MutableMap.copyOf(config).asUnmodifiable();
    }

    /** current values for all entries in a map where the keys are converted to {@link ConfigKey} instances */
    public synchronized Map<ConfigKey<?>, ?> getAllConfigAsConfigKeyMap() {
        Map<ConfigKey<?>,Object> result = MutableMap.of();
        for (Map.Entry<String,Object> entry: config.entrySet()) {
            result.put(ConfigKeys.newConfigKey(Object.class, entry.getKey()), entry.getValue());
        }
        return result;
    }

    /** Returns the internal map containing the current values for all entries;
     * for use where the caller wants to modify this directly and knows it is safe to do so 
     * <p>
     * Accesses to the returned map must be synchronized on this bag if the 
     * thread-safe behaviour is required. */ 
    public Map<String,Object> getAllConfigMutable() {
        if (live) {
            // TODO sealed no longer works as before, because `config` is the backing storage map.
            // Therefore returning it is dangerous! Even if we were to replace our field with an immutable copy,
            // the underlying datagrid's map would still be modifiable. We need a way to switch the returned
            // value's behaviour to sealable (i.e. wrapping the returned map).
            return (sealed) ? MutableMap.copyOf(config).asUnmodifiable() : config;
        } else {
            return config;
        }
    }

    /** current values for all entries which have not yet been used 
     * @return non-modifiable map of strings to object */
    public synchronized Map<String,Object> getUnusedConfig() {
        return MutableMap.copyOf(unusedConfig).asUnmodifiable();
    }

    /** Returns the internal map containing the current values for all entries which have not yet been used;
     * for use where the caller wants to modify this directly and knows it is safe to do so 
     * <p>
     * Accesses to the returned map must be synchronized on this bag if the 
     * thread-safe behaviour is required. */ 
    public Map<String,Object> getUnusedConfigMutable() {
        return unusedConfig;
    }

    public ConfigBag putAll(Map<?,?> addlConfig) {
        if (addlConfig==null) return this;
        for (Map.Entry<?,?> e: addlConfig.entrySet()) {
            putAsStringKey(e.getKey(), e.getValue());
        }
        return this;
    }
    
    public ConfigBag putAll(ConfigBag addlConfig) {
        return putAll(addlConfig.getAllConfig());
    }
    
    public <T> ConfigBag putIfAbsent(ConfigKey<T> key, T value) {
        return putIfAbsent(MutableMap.of(key, value));
    }

    public ConfigBag putAsStringKeyIfAbsent(Object key, Object value) {
        return putIfAbsent(MutableMap.of(key, value));
    }

    public synchronized ConfigBag putIfAbsent(Map<?, ?> propertiesToSet) {
        if (propertiesToSet==null)
            return this;
        for (Map.Entry<?, ?> entry: propertiesToSet.entrySet()) {
            Object key = entry.getKey();
            if (key instanceof HasConfigKey<?>)
                key = ((HasConfigKey<?>)key).getConfigKey();
            if (key instanceof ConfigKey<?>) {
                if (!containsKey((ConfigKey<?>)key))
                    putAsStringKey(key, entry.getValue());
            } else if (key instanceof String) {
                if (!containsKey((String)key))
                    putAsStringKey(key, entry.getValue());
            } else {
                logInvalidKey(key);
            }
        }
        return this;
    }

    public ConfigBag putIfAbsent(ConfigBag addlConfig) {
        return putIfAbsent(addlConfig.getAllConfig());
    }


    @SuppressWarnings("unchecked")
    public <T> T put(ConfigKey<T> key, T value) {
        return (T) putStringKey(key.getName(), value);
    }
    
    public <T> ConfigBag putIfNotNull(ConfigKey<T> key, T value) {
        if (value!=null) put(key, value);
        return this;
    }

    public <T> ConfigBag putIfAbsentAndNotNull(ConfigKey<T> key, T value) {
        if (value!=null) putIfAbsent(key, value);
        return this;
    }

    /** as {@link #put(ConfigKey, Object)} but returning this ConfigBag for fluent-style coding */
    public <T> ConfigBag configure(ConfigKey<T> key, T value) {
        putStringKey(key.getName(), value);
        return this;
    }
    
    public <T> ConfigBag configureStringKey(String key, T value) {
        putStringKey(key, value);
        return this;
    }
    
    protected synchronized void putAsStringKey(Object key, Object value) {
        if (key instanceof HasConfigKey<?>) key = ((HasConfigKey<?>)key).getConfigKey();
        if (key instanceof ConfigKey<?>) key = ((ConfigKey<?>)key).getName();
        if (key instanceof String) {
            putStringKey((String)key, value);
        } else {
            logInvalidKey(key);
        }
    }

    protected void logInvalidKey(Object key) {
        String message = (key == null ? "Invalid key 'null'" : "Invalid key type "+key.getClass().getCanonicalName()+" ("+key+")") +
                " being used for configuration, ignoring";
        log.debug(message, new Throwable("Source of "+message));
        log.warn(message);
    }
    
    /** recommended to use {@link #put(ConfigKey, Object)} but there are times
     * (e.g. when copying a map) where we want to put a string key directly 
     */
    public synchronized Object putStringKey(String key, Object value) {
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
    public Object putStringKeyIfHasValue(String key, Maybe<?> value) {
        if (value.isPresent())
            return putStringKey(key, value.get());
        return null;
    }
    public Object putStringKeyIfNotNull(String key, Object value) {
        if (value!=null)
            return putStringKey(key, value);
        return null;
    }

    public boolean containsKey(HasConfigKey<?> key) {
        return containsKey(key.getConfigKey());
    }

    public boolean containsKey(ConfigKey<?> key) {
        return containsKey(key.getName());
    }

    public synchronized boolean containsKey(String key) {
        return config.containsKey(key);
    }

    /** returns the value of this config key, falling back to its default (use containsKey to see whether it was contained);
     * also marks it as having been used (use peek to prevent marking as used)
     */
    public <T> T get(ConfigKey<T> key) {
        return get(key, true);
    }

    /** gets a value from a string-valued key or null; ConfigKey is preferred, but this is useful in some contexts (e.g. setting from flags) */
    public Object getStringKey(String key) {
        return getStringKeyMaybe(key).orNull();
    }
    /** gets a {@link Maybe}-wrapped value from a string-valued key; ConfigKey is preferred, but this is useful in some contexts (e.g. setting from flags) */
    public @Nonnull Maybe<Object> getStringKeyMaybe(String key) {
        return getStringKeyMaybe(key, true);
    }

    /** gets a {@link Maybe}-wrapped value from a key, inferring the type of that key (e.g. {@link ConfigKey} or {@link String}) */
    @Beta
    public Maybe<Object> getObjKeyMaybe(Object key) {
        if (key instanceof HasConfigKey<?>) key = ((HasConfigKey<?>)key).getConfigKey();
        if (key instanceof ConfigKey<?>) key = ((ConfigKey<?>)key).getName();
        if (key instanceof String) {
            return getStringKeyMaybe((String)key, true);
        } else {
            logInvalidKey(key);
            return Maybe.absent();
        }
    }

    /** like get, but without marking it as used */
    public <T> T peek(ConfigKey<T> key) {
        return get(key, false);
    }

    /** returns the first key in the list for which a value is explicitly set, then defaulting to defaulting value of preferred key */
    public synchronized <T> T getFirst(ConfigKey<T> preferredKey, ConfigKey<T> ...otherCurrentKeysInOrderOfPreference) {
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
    public synchronized Object getWithDeprecation(ConfigKey<?>[] currentKeysInOrderOfPreference, ConfigKey<?> ...deprecatedKeys) {
        // Get preferred key (or null)
        ConfigKey<?> preferredKeyProvidingValue = null;
        Object result = null;
        boolean found = false;
        for (ConfigKey<?> key: currentKeysInOrderOfPreference) {
            if (containsKey(key)) {
                preferredKeyProvidingValue = key;
                result = get(preferredKeyProvidingValue);
                found = true;
                break;
            }
        }
        
        // Check if any deprecated keys are set
        ConfigKey<?> deprecatedKeyProvidingValue = null;
        Object deprecatedResult = null;
        boolean foundDeprecated = false;
        for (ConfigKey<?> deprecatedKey: deprecatedKeys) {
            Object x = null;
            boolean foundX = false;
            if (containsKey(deprecatedKey)) {
                x = get(deprecatedKey);
                foundX = true;
            }
            if (foundX) {
                if (found) {
                    if (!Objects.equal(result, x)) {
                        log.warn("Conflicting value from deprecated key " +deprecatedKey+", value "+x+
                                "; using preferred key "+preferredKeyProvidingValue+" value "+result);
                    } else {
                        log.info("Deprecated key " +deprecatedKey+" ignored; has same value as preferred key "+preferredKeyProvidingValue+" ("+result+")");
                    }
                } else if (foundDeprecated) {
                    if (!Objects.equal(result, x)) {
                        log.warn("Conflicting values from deprecated keys: using " +deprecatedKeyProvidingValue+" instead of "+deprecatedKey+
                                " (value "+deprecatedResult+" instead of "+x+")");
                    } else {
                        log.info("Deprecated key " +deprecatedKey+" ignored; has same value as other deprecated key "+preferredKeyProvidingValue+" ("+deprecatedResult+")");
                    }
                } else {
                    // new value, from deprecated key
                    log.warn("Deprecated key " +deprecatedKey+" detected (supplying value "+x+"), "+
                            "; recommend changing to preferred key '"+currentKeysInOrderOfPreference[0]+"'; this will not be supported in future versions");
                    deprecatedResult = x;
                    deprecatedKeyProvidingValue = deprecatedKey;
                    foundDeprecated = true;
                }
            }
        }
        
        if (found) {
            return result;
        } else if (foundDeprecated) {
            return deprecatedResult;
        } else {
            return currentKeysInOrderOfPreference[0].getDefaultValue();
        }
    }

    protected <T> T get(ConfigKey<T> key, boolean markUsed) {
        // TODO for now, no evaluation -- maps / closure content / other smart (self-extracting) keys are NOT supported
        // (need a clean way to inject that behaviour, as well as desired TypeCoercions)
        // this method, and the coercion, is not synchronized, nor does it need to be, because the "get" is synchronized. 
        return coerceFirstNonNullKeyValue(key, getStringKey(key.getName(), markUsed));
    }

    /** returns the first non-null value to be the type indicated by the key, or the keys default value if no non-null values are supplied */
    public static <T> T coerceFirstNonNullKeyValue(ConfigKey<T> key, Object ...values) {
        for (Object o: values)
            if (o!=null) return TypeCoercions.coerce(o, key.getTypeToken());
        return TypeCoercions.coerce(key.getDefaultValue(), key.getTypeToken());
    }

    protected Object getStringKey(String key, boolean markUsed) {
        return getStringKeyMaybe(key, markUsed).orNull();
    }
    protected synchronized Maybe<Object> getStringKeyMaybe(String key, boolean markUsed) {
        if (config.containsKey(key)) {
            if (markUsed) markUsed(key);
            return Maybe.of(config.get(key));
        }
        return Maybe.absent();
    }

    /** indicates that a string key in the config map has been accessed */
    public synchronized void markUsed(String key) {
        unusedConfig.remove(key);
    }

    public synchronized void clear() {
        if (sealed) 
            throw new IllegalStateException("Cannot clear this config bag has been sealed and is now immutable.");
        config.clear();
        unusedConfig.clear();
    }
    
    public ConfigBag removeAll(ConfigKey<?> ...keys) {
        for (ConfigKey<?> key: keys) remove(key);
        return this;
    }

    public synchronized void remove(ConfigKey<?> key) {
        remove(key.getName());
    }

    public ConfigBag removeAll(Iterable<String> keys) {
        for (String key: keys) remove(key);
        return this;
    }

    public synchronized void remove(String key) {
        if (sealed) 
            throw new IllegalStateException("Cannot remove "+key+": this config bag has been sealed and is now immutable.");
        config.remove(key);
        unusedConfig.remove(key);
    }

    public ConfigBag copy(ConfigBag other) {
        // ensure locks are taken in a canonical order to prevent deadlock
        if (other==null) {
            synchronized (this) {
                return copyWhileSynched(other);
            }
        }
        if (System.identityHashCode(other) < System.identityHashCode(this)) {
            synchronized (other) {
                synchronized (this) {
                    return copyWhileSynched(other);
                }
            }
        } else {
            synchronized (this) {
                synchronized (other) {
                    return copyWhileSynched(other);
                }
            }
        }
    }
    
    protected ConfigBag copyWhileSynched(ConfigBag other) {
        if (sealed) 
            throw new IllegalStateException("Cannot copy "+other+" to "+this+": this config bag has been sealed and is now immutable.");
        putAll(other.getAllConfig());
        markAll(Sets.difference(other.getAllConfig().keySet(), other.getUnusedConfig().keySet()));
        setDescription(other.getDescription());
        return this;
    }

    public synchronized int size() {
        return config.size();
    }
    
    public synchronized boolean isEmpty() {
        return config.isEmpty();
    }
    
    public ConfigBag markAll(Iterable<String> usedFlags) {
        for (String flag: usedFlags)
            markUsed(flag);
        return this;
    }

    public synchronized boolean isUnused(ConfigKey<?> key) {
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
        if (live) {
            // TODO How to ensure sealed?!
        } else {
            config = getAllConfig();
        }
        return this;
    }

    // TODO why have both this and mutable
    /** @see #getAllConfigMutable() */
    public Map<String, Object> getAllConfigRaw() {
        return getAllConfigMutable();
    }
    
    @Override
    public String toString() {
        return JavaClassNames.simpleClassName(this)+"["+getAllConfigRaw()+"]";
    }

}
