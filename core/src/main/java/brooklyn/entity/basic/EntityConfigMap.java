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
package brooklyn.entity.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.ConfigMap;
import brooklyn.event.basic.StructuredConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.internal.ConfigKeySelfExtracting;
import brooklyn.util.task.DeferredSupplier;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class EntityConfigMap implements ConfigMap {

    private static final Logger LOG = LoggerFactory.getLogger(EntityConfigMap.class);

    /** entity against which config resolution / task execution will occur */
    private final AbstractEntity entity;

    private final ConfigMapViewWithStringKeys mapViewWithStringKeys = new ConfigMapViewWithStringKeys(this);

    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "children" of this
     * entity.
     */
    private final Map<ConfigKey<?>,Object> ownConfig;
    private final Map<ConfigKey<?>,Object> inheritedConfig = Collections.synchronizedMap(new LinkedHashMap<ConfigKey<?>, Object>());
    private final ConfigBag localConfigBag;
    private final ConfigBag inheritedConfigBag;

    public EntityConfigMap(AbstractEntity entity, Map<ConfigKey<?>, Object> storage) {
        this.entity = checkNotNull(entity, "entity must be specified");
        this.ownConfig = checkNotNull(storage, "storage map must be specified");
        
        // TODO store ownUnused in backing-storage
        this.localConfigBag = ConfigBag.newInstance();
        this.inheritedConfigBag = ConfigBag.newInstance();
    }

    public <T> T getConfig(ConfigKey<T> key) {
        return getConfig(key, null);
    }
    
    public <T> T getConfig(HasConfigKey<T> key) {
        return getConfig(key.getConfigKey(), null);
    }
    
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue) {
        return getConfig(key.getConfigKey(), defaultValue);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getConfig(ConfigKey<T> key, T defaultValue) {
        // FIXME What about inherited task in config?!
        //              alex says: think that should work, no?
        // FIXME What if someone calls getConfig on a task, before setting parent app?
        //              alex says: not supported (throw exception, or return the task)
        
        // In case this entity class has overridden the given key (e.g. to set default), then retrieve this entity's key
        // TODO If ask for a config value that's not in our configKeys, should we really continue with rest of method and return key.getDefaultValue?
        //      e.g. SshBasedJavaAppSetup calls setAttribute(JMX_USER), which calls getConfig(JMX_USER)
        //           but that example doesn't have a default...
        ConfigKey<T> ownKey = entity!=null ? (ConfigKey<T>)elvis(entity.getEntityType().getConfigKey(key.getName()), key) : key;
        
        // TODO We're notifying of config-changed because currently persistence needs to know when the
        // attributeWhenReady is complete (so it can persist the result).
        // Long term, we'll just persist tasks properly so the call to onConfigChanged will go!

        // Don't use groovy truth: if the set value is e.g. 0, then would ignore set value and return default!
        if (ownKey instanceof ConfigKeySelfExtracting) {
            Object rawval = ownConfig.get(key);
            T result = null;
            boolean complete = false;
            if (((ConfigKeySelfExtracting<T>)ownKey).isSet(ownConfig)) {
                ExecutionContext exec = entity.getExecutionContext();
                result = ((ConfigKeySelfExtracting<T>)ownKey).extractValue(ownConfig, exec);
                complete = true;
            } else if (((ConfigKeySelfExtracting<T>)ownKey).isSet(inheritedConfig)) {
                ExecutionContext exec = entity.getExecutionContext();
                result = ((ConfigKeySelfExtracting<T>)ownKey).extractValue(inheritedConfig, exec);
                complete = true;
            } else if (localConfigBag.containsKey(ownKey)) {
                // TODO configBag.get doesn't handle tasks/attributeWhenReady - it only uses TypeCoercions
                result = localConfigBag.get(ownKey);
                complete = true;
            } else if (inheritedConfigBag.containsKey(ownKey)) {
                result = inheritedConfigBag.get(ownKey);
                complete = true;
            }

            if (rawval instanceof Task) {
                entity.getManagementSupport().getEntityChangeListener().onConfigChanged(key);
            }
            if (complete) {
                return result;
            }
        } else {
            LOG.warn("Config key {} of {} is not a ConfigKeySelfExtracting; cannot retrieve value; returning default", ownKey, this);
        }
        return TypeCoercions.coerce((defaultValue != null) ? defaultValue : ownKey.getDefaultValue(), key.getTypeToken());
    }
    
    @Override
    @Deprecated
    public Object getRawConfig(ConfigKey<?> key) {
        return getConfigRaw(key, true).orNull();
    }
    
    @Override
    public Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited) {
        if (ownConfig.containsKey(key)) return Maybe.of(ownConfig.get(key));
        if (includeInherited && inheritedConfig.containsKey(key)) return Maybe.of(inheritedConfig.get(key));
        return Maybe.absent();
    }
    
    /** returns the config visible at this entity, local and inherited (preferring local) */
    public Map<ConfigKey<?>,Object> getAllConfig() {
        Map<ConfigKey<?>,Object> result = new LinkedHashMap<ConfigKey<?>,Object>(inheritedConfig.size()+ownConfig.size());
        result.putAll(inheritedConfig);
        result.putAll(ownConfig);
        return Collections.unmodifiableMap(result);
    }

    /** returns the config defined at this entity, ie not inherited */
    public Map<ConfigKey<?>,Object> getLocalConfig() {
        Map<ConfigKey<?>,Object> result = new LinkedHashMap<ConfigKey<?>,Object>(ownConfig.size());
        result.putAll(ownConfig);
        return Collections.unmodifiableMap(result);
    }
    
    /** returns the config visible at this entity, local and inherited (preferring local), including those that did not match config keys */
    public ConfigBag getAllConfigBag() {
        return ConfigBag.newInstanceCopying(localConfigBag)
                .putAll(ownConfig)
                .putIfAbsent(inheritedConfig)
                .putIfAbsent(inheritedConfigBag)
                .seal();
    }

    /** returns the config defined at this entity, ie not inherited, including those that did not match config keys */
    public ConfigBag getLocalConfigBag() {
        return ConfigBag.newInstanceCopying(localConfigBag)
                .putAll(ownConfig)
                .seal();
    }

    public Object setConfig(ConfigKey<?> key, Object v) {
        Object val;
        if ((v instanceof Future) || (v instanceof DeferredSupplier)) {
            // no coercion for these (coerce on exit)
            val = v;
        } else if (key instanceof StructuredConfigKey) {
            // no coercion for these structures (they decide what to do)
            val = v;
        } else {
            try {
                val = TypeCoercions.coerce(v, key.getType());
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot coerce or set "+v+" to "+key, e);
            }
        }
        Object oldVal;
        if (key instanceof StructuredConfigKey) {
            oldVal = ((StructuredConfigKey)key).applyValueToMap(val, ownConfig);
        } else {
            oldVal = ownConfig.put(key, val);
        }
        entity.refreshInheritedConfigOfChildren();
        return oldVal;
    }
    
    public void setLocalConfig(Map<ConfigKey<?>, ? extends Object> vals) {
        ownConfig.clear();
        ownConfig.putAll(vals);
    }
    
    public void setInheritedConfig(Map<ConfigKey<?>, ? extends Object> vals, ConfigBag configBagVals) {
        inheritedConfig.clear();
        inheritedConfig.putAll(vals);

        // The configBagVals contains all inherited, including strings that did not match a config key on the parent.
        // They might match a config-key on this entity though, so need to check that:
        //   - if it matches one of our keys, set it in inheritedConfig
        //   - otherwise add it to our inheritedConfigBag
        Set<String> valKeyNames = Sets.newLinkedHashSet();
        for (ConfigKey<?> key : vals.keySet()) {
            valKeyNames.add(key.getName());
        }
        Map<String,Object> valsUnmatched = MutableMap.<String,Object>builder()
                .putAll(configBagVals.getAllConfig())
                .removeAll(valKeyNames)
                .build();
        inheritedConfigBag.clear();
        Map<ConfigKey<?>, SetFromFlag> annotatedConfigKeys = FlagUtils.getAnnotatedConfigKeys(entity.getClass());
        Map<String, ConfigKey<?>> renamedConfigKeys = Maps.newLinkedHashMap();
        for (Map.Entry<ConfigKey<?>, SetFromFlag> entry: annotatedConfigKeys.entrySet()) {
            String rename = entry.getValue().value();
            if (rename != null) {
                renamedConfigKeys.put(rename, entry.getKey());
            }
        }
        for (Map.Entry<String,Object> entry : valsUnmatched.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            ConfigKey<?> key = renamedConfigKeys.get(name);
            if (key == null) key = entity.getEntityType().getConfigKey(name);
            if (key != null) {
                if (inheritedConfig.containsKey(key)) {
                    LOG.warn("Entity "+entity+" inherited duplicate config for key "+key+", via explicit config and string name "+name+"; using value of key");
                } else {
                    inheritedConfig.put(key, value);
                }
            } else {
                inheritedConfigBag.putStringKey(name, value);
            }
        }
    }
    
    public void addToLocalBag(Map<String,?> vals) {
        localConfigBag.putAll(vals);
    }

    public void clearInheritedConfig() {
        inheritedConfig.clear();
        inheritedConfigBag.clear();
    }

    @Override
    public EntityConfigMap submap(Predicate<ConfigKey<?>> filter) {
        EntityConfigMap m = new EntityConfigMap(entity, Maps.<ConfigKey<?>, Object>newLinkedHashMap());
        for (Map.Entry<ConfigKey<?>,Object> entry: inheritedConfig.entrySet())
            if (filter.apply(entry.getKey()))
                m.inheritedConfig.put(entry.getKey(), entry.getValue());
        for (Map.Entry<ConfigKey<?>,Object> entry: ownConfig.entrySet())
            if (filter.apply(entry.getKey()))
                m.ownConfig.put(entry.getKey(), entry.getValue());
        return m;
    }

    @Override
    public String toString() {
        return super.toString()+"[own="+Entities.sanitize(ownConfig)+"; inherited="+Entities.sanitize(inheritedConfig)+"]";
    }
    
    public Map<String,Object> asMapWithStringKeys() {
        return mapViewWithStringKeys;
    }

    @Override
    public int size() {
        return ownConfig.size() + inheritedConfig.size();
    }

    @Override
    public boolean isEmpty() {
        return ownConfig.isEmpty() && inheritedConfig.isEmpty();
    }
    
}
