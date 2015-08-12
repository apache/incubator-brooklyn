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

import org.apache.brooklyn.management.ExecutionContext;
import org.apache.brooklyn.management.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigInheritance;
import brooklyn.config.ConfigKey;
import brooklyn.config.internal.AbstractConfigMapImpl;
import brooklyn.event.basic.StructuredConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.internal.ConfigKeySelfExtracting;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class EntityConfigMap extends AbstractConfigMapImpl {

    private static final Logger LOG = LoggerFactory.getLogger(EntityConfigMap.class);

    /** entity against which config resolution / task execution will occur */
    private final AbstractEntity entity;

    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "children" of this
     * entity.
     */
    private final Map<ConfigKey<?>,Object> inheritedConfig = Collections.synchronizedMap(new LinkedHashMap<ConfigKey<?>, Object>());
    // TODO do we really want to have *both* bags and maps for these?  danger that they get out of synch.
    // have added some logic (Oct 2014) so that the same changes are applied to both, in most places at least;
    // i (alex) think we should prefer ConfigBag (the input keys don't matter, it is more a question of retrieval keys),
    // but first we need ConfigBag to support StructuredConfigKeys 
    private final ConfigBag localConfigBag;
    private final ConfigBag inheritedConfigBag;

    public EntityConfigMap(AbstractEntity entity, Map<ConfigKey<?>, Object> storage) {
        this.entity = checkNotNull(entity, "entity must be specified");
        this.ownConfig = checkNotNull(storage, "storage map must be specified");
        
        // TODO store ownUnused in backing-storage
        this.localConfigBag = ConfigBag.newInstance();
        this.inheritedConfigBag = ConfigBag.newInstance();
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
        
        ConfigInheritance inheritance = key.getInheritance();
        if (inheritance==null) inheritance = ownKey.getInheritance(); 
        if (inheritance==null) {
            // TODO we could warn by introducing a temporary "ALWAYS_BUT_WARNING" instance
            inheritance = getDefaultInheritance(); 
        }
        
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
            } else if (isInherited(ownKey, inheritance) && 
                    ((ConfigKeySelfExtracting<T>)ownKey).isSet(inheritedConfig)) {
                ExecutionContext exec = entity.getExecutionContext();
                result = ((ConfigKeySelfExtracting<T>)ownKey).extractValue(inheritedConfig, exec);
                complete = true;
            } else if (localConfigBag.containsKey(ownKey)) {
                // TODO configBag.get doesn't handle tasks/attributeWhenReady - it only uses TypeCoercions
                result = localConfigBag.get(ownKey);
                complete = true;
            } else if (isInherited(ownKey, inheritance) && 
                    inheritedConfigBag.containsKey(ownKey)) {
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

    private <T> boolean isInherited(ConfigKey<T> key) {
        return isInherited(key, key.getInheritance());
    }
    private <T> boolean isInherited(ConfigKey<T> key, ConfigInheritance inheritance) {
        if (inheritance==null) inheritance = getDefaultInheritance(); 
        return inheritance.isInherited(key, entity.getParent(), entity);
    }
    private ConfigInheritance getDefaultInheritance() {
        return ConfigInheritance.ALWAYS; 
    }

    @Override
    public Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited) {
        if (ownConfig.containsKey(key)) return Maybe.of(ownConfig.get(key));
        if (includeInherited && inheritedConfig.containsKey(key)) return Maybe.of(inheritedConfig.get(key));
        return Maybe.absent();
    }
    
    /** an immutable copy of the config visible at this entity, local and inherited (preferring local) */
    public Map<ConfigKey<?>,Object> getAllConfig() {
        Map<ConfigKey<?>,Object> result = new LinkedHashMap<ConfigKey<?>,Object>(inheritedConfig.size()+ownConfig.size());
        result.putAll(inheritedConfig);
        result.putAll(ownConfig);
        return Collections.unmodifiableMap(result);
    }

    /** an immutable copy of the config defined at this entity, ie not inherited */
    public Map<ConfigKey<?>,Object> getLocalConfig() {
        Map<ConfigKey<?>,Object> result = new LinkedHashMap<ConfigKey<?>,Object>(ownConfig.size());
        result.putAll(ownConfig);
        return Collections.unmodifiableMap(result);
    }
    
    /** Creates an immutable copy of the config visible at this entity, local and inherited (preferring local), including those that did not match config keys */
    public ConfigBag getAllConfigBag() {
        return ConfigBag.newInstanceCopying(localConfigBag)
                .putAll(ownConfig)
                .putIfAbsent(inheritedConfig)
                .putIfAbsent(inheritedConfigBag)
                .seal();
    }

    /** Creates an immutable copy of the config defined at this entity, ie not inherited, including those that did not match config keys */
    public ConfigBag getLocalConfigBag() {
        return ConfigBag.newInstanceCopying(localConfigBag)
                .putAll(ownConfig)
                .seal();
    }

    @SuppressWarnings("unchecked")
    public Object setConfig(ConfigKey<?> key, Object v) {
        Object val = coerceConfigVal(key, v);
        Object oldVal;
        if (key instanceof StructuredConfigKey) {
            oldVal = ((StructuredConfigKey)key).applyValueToMap(val, ownConfig);
            // TODO ConfigBag does not handle structured config keys; quick fix is to remove (and should also remove any subkeys;
            // as it stands if someone set string a.b.c in the config bag then removed structured key a.b, then got a.b.c they'd get a vale);
            // long term fix is to support structured config keys in ConfigBag, at which point i think we could remove ownConfig altogether
            localConfigBag.remove(key);
        } else {
            oldVal = ownConfig.put(key, val);
            localConfigBag.put((ConfigKey<Object>)key, v);
        }
        entity.config().refreshInheritedConfigOfChildren();
        return oldVal;
    }
    
    public void setLocalConfig(Map<ConfigKey<?>, ?> vals) {
        ownConfig.clear();
        localConfigBag.clear();
        ownConfig.putAll(vals);
        localConfigBag.putAll(vals);
    }
    
    public void setInheritedConfig(Map<ConfigKey<?>, ?> valsO, ConfigBag configBagVals) {
        Map<ConfigKey<?>, ?> vals = filterUninheritable(valsO);
        
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
                if (!isInherited(key)) {
                    // no-op
                } else if (inheritedConfig.containsKey(key)) {
                    LOG.warn("Entity "+entity+" inherited duplicate config for key "+key+", via explicit config and string name "+name+"; using value of key");
                } else {
                    inheritedConfig.put(key, value);
                }
            } else {
                // a config bag has discarded the keys, so we must assume default inheritance for things given that way
                // unless we can infer a key; not a big deal, as we should have the key in inheritedConfig for everything
                // which originated with a key ... but still, it would be nice to clean up the use of config bag!
                inheritedConfigBag.putStringKey(name, value);
            }
        }
    }
    
    private Map<ConfigKey<?>, ?> filterUninheritable(Map<ConfigKey<?>, ?> vals) {
        Map<ConfigKey<?>, Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<ConfigKey<?>, ?> entry : vals.entrySet()) {
            if (isInherited(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    public void addToLocalBag(Map<String,?> vals) {
        localConfigBag.putAll(vals);
        // quick fix for problem that ownConfig can get out of synch
        ownConfig.putAll(localConfigBag.getAllConfigAsConfigKeyMap());
    }

    public void removeFromLocalBag(String key) {
        localConfigBag.remove(key);
        ownConfig.remove(key);
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
        return super.toString()+"[own="+Sanitizer.sanitize(ownConfig)+"; inherited="+Sanitizer.sanitize(inheritedConfig)+"]";
    }
    
}
