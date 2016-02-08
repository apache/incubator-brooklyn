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
package org.apache.brooklyn.core.mgmt.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.EntityMemento;
import org.apache.brooklyn.api.mgmt.rebind.mementos.TreeNode;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.Sanitizer;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.objs.BrooklynTypes;
import org.apache.brooklyn.core.sensor.Sensors;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Represents the state of an entity, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see AbstractEntity#getRebindSupport()
 * @see RebindSupport#getMemento()
 * @see RebindSupport#reconstruct(org.apache.brooklyn.api.mgmt.rebind.RebindContext, org.apache.brooklyn.api.mgmt.rebind.mementos.Memento)
 * 
 * @author aled
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility= JsonAutoDetect.Visibility.NONE)
public class BasicEntityMemento extends AbstractTreeNodeMemento implements EntityMemento, Serializable {

    private static final Logger log = LoggerFactory.getLogger(BasicEntityMemento.class);
    
    private static final long serialVersionUID = 8642959541121050126L;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractTreeNodeMemento.Builder<Builder> {
        protected Boolean isTopLevelApp;
        protected Map<ConfigKey<?>, Object> config = Maps.newLinkedHashMap();
        protected Map<String, Object> configUnmatched = Maps.newLinkedHashMap();
        protected Map<AttributeSensor<?>, Object> attributes = Maps.newLinkedHashMap();
        protected List<String> locations = Lists.newArrayList();
        protected List<String> policies = Lists.newArrayList();
        protected List<String> enrichers = Lists.newArrayList();
        protected List<String> feeds = Lists.newArrayList();
        protected List<String> members = Lists.newArrayList();
        protected List<Effector<?>> effectors = Lists.newArrayList();
        
        public Builder from(EntityMemento other) {
            super.from((TreeNode)other);
            isTopLevelApp = other.isTopLevelApp();
            displayName = other.getDisplayName();
            config.putAll(other.getConfig());
            configUnmatched.putAll(other.getConfigUnmatched());
            attributes.putAll(other.getAttributes());
            locations.addAll(other.getLocations());
            policies.addAll(other.getPolicies());
            enrichers.addAll(other.getEnrichers());
            feeds.addAll(other.getFeeds());
            members.addAll(other.getMembers());
            effectors.addAll(other.getEffectors());
            return this;
        }
        public EntityMemento build() {
            invalidate();
            return new BasicEntityMemento(this);
        }
    }
    
    /** this is usually inferred based on parent==null (so left out of persistent);
     * only needs to be set if it is an app with a parent (nested application) 
     * or an entity without a parent (orphaned entity) */
    private Boolean isTopLevelApp;
    
    private Map<String, Object> config;
    private List<String> locations;
    private List<String> members;
    private Map<String, Object> attributes;
    private List<String> policies;
    private List<String> enrichers;
    private List<String> feeds;
    
    // TODO can we move some of these to entity type, or remove/re-insert those which are final statics?
    private Map<String, ConfigKey<?>> configKeys;
    private transient Map<String, ConfigKey<?>> staticConfigKeys;
    private Map<String, AttributeSensor<?>> attributeKeys;
    private transient Map<String, Sensor<?>> staticSensorKeys;
    private List<Effector<?>> effectors;
    
    private transient Map<ConfigKey<?>, Object> configByKey;
    private transient Map<String, Object> configUnmatched;
    private transient Map<AttributeSensor<?>, Object> attributesByKey;

    @SuppressWarnings("unused") // For deserialisation
    private BasicEntityMemento() {}

    // Trusts the builder to not mess around with mutability after calling build() -- with invalidate pattern
    // Does not make any attempt to make unmodifiable, or immutable copy, to have cleaner (and faster) output
    protected BasicEntityMemento(Builder builder) {
        super(builder);
        
        isTopLevelApp = builder.isTopLevelApp==null || builder.isTopLevelApp==(getParent()==null) ? null : builder.isTopLevelApp;
        
        locations = toPersistedList(builder.locations);
        policies = toPersistedList(builder.policies);
        enrichers = toPersistedList(builder.enrichers);
        feeds = toPersistedList(builder.feeds);
        members = toPersistedList(builder.members);
        
        effectors = toPersistedList(builder.effectors);
        
        configByKey = builder.config;
        configUnmatched = builder.configUnmatched;
        attributesByKey = builder.attributes;
        
        if (configByKey!=null) {
            configKeys = Maps.newLinkedHashMap();
            config = Maps.newLinkedHashMap();
            for (Map.Entry<ConfigKey<?>, Object> entry : configByKey.entrySet()) {
                ConfigKey<?> key = entry.getKey();
                if (!key.equals(getStaticConfigKeys().get(key.getName())))
                    configKeys.put(key.getName(), key);
                config.put(key.getName(), entry.getValue());
            }
            configKeys = toPersistedMap(configKeys);
            config = toPersistedMap(config);
        }
        if (configUnmatched!=null) {
            if (config == null) config = Maps.newLinkedHashMap();
            config.putAll(configUnmatched);
            config = toPersistedMap(config);
        }
        if (attributesByKey!=null) {
            attributeKeys = Maps.newLinkedHashMap();
            attributes = Maps.newLinkedHashMap();
            for (Map.Entry<AttributeSensor<?>, Object> entry : attributesByKey.entrySet()) {
                AttributeSensor<?> key = entry.getKey();
                if (!key.equals(getStaticSensorKeys().get(key.getName())))
                    attributeKeys.put(key.getName(), key);
                attributes.put(key.getName(), entry.getValue());
            }
            attributeKeys = toPersistedMap(attributeKeys);
            attributes = toPersistedMap(attributes);
        }
    }

    protected synchronized Map<String, ConfigKey<?>> getStaticConfigKeys() {
        if (staticConfigKeys==null) {
            @SuppressWarnings("unchecked")
            Class<? extends Entity> clazz = (Class<? extends Entity>) getTypeClass();
            staticConfigKeys = (clazz == null) ? BrooklynTypes.getDefinedConfigKeys(getType()) : BrooklynTypes.getDefinedConfigKeys(clazz);
        }
        return staticConfigKeys;
    }

    final static String LEGACY_KEY_DESCRIPTION = "This item was defined in a different version of this blueprint; metadata unavailable here.";
    
    protected ConfigKey<?> getConfigKey(String key) {
        ConfigKey<?> result = null;
        if (configKeys!=null) {
            result = configKeys.get(key);
            if (result!=null && !LEGACY_KEY_DESCRIPTION.equals(result.getDescription()))
                    return result;
        }
        ConfigKey<?> resultStatic = getStaticConfigKeys().get(key);
        if (resultStatic!=null) return resultStatic;
        if (result!=null) return result;
        // can come here on rebind if a key has gone away in the class, so create a generic one; 
        // but if it was previously found to a legacy key (below) which is added back after a regind, 
        // gnore the legacy description (several lines above) and add the key back from static (code just above)
        log.warn("Config key "+key+": "+LEGACY_KEY_DESCRIPTION);
        return ConfigKeys.newConfigKey(Object.class, key, LEGACY_KEY_DESCRIPTION);
    }

    protected synchronized Map<String, Sensor<?>> getStaticSensorKeys() {
        if (staticSensorKeys==null) {
            @SuppressWarnings("unchecked")
            Class<? extends Entity> clazz = (Class<? extends Entity>) getTypeClass();
            staticSensorKeys = (clazz == null) ? BrooklynTypes.getDefinedSensors(getType()) : BrooklynTypes.getDefinedSensors(clazz);
        }
        return staticSensorKeys;
    }

    protected AttributeSensor<?> getAttributeKey(String key) {
        AttributeSensor<?> result=null;
        if (attributeKeys!=null) {
            result = attributeKeys.get(key);
            if (result!=null && !LEGACY_KEY_DESCRIPTION.equals(result.getDescription()))
                return result;
        }
        AttributeSensor<?> resultStatic = (AttributeSensor<?>) getStaticSensorKeys().get(key);
        if (resultStatic!=null) return resultStatic;
        if (result!=null) return result;
        // see notes on legacy config key
        log.warn("Sensor "+key+": "+LEGACY_KEY_DESCRIPTION);
        return Sensors.newSensor(Object.class, key, LEGACY_KEY_DESCRIPTION);
    }

    /**
     * Creates the appropriate data-structures for the getters, from the serialized forms.
     * The serialized form is string->object (e.g. using attribute sensor name), whereas 
     * the getters return Map<AttributeSensor,Object> for example.
     * 
     * TODO Really don't like this pattern. Should we clean it up? But deferring until 
     * everything else is working.
     */
    private void postDeserialize() {
        configByKey = Maps.newLinkedHashMap();
        configUnmatched = Maps.newLinkedHashMap();
        if (config!=null) {
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                ConfigKey<?> configKey = getConfigKey(entry.getKey());
                if (configKey != null) {
                    configByKey.put(configKey, entry.getValue());
                } else {
                    configUnmatched.put(entry.getKey(), entry.getValue());
                }
            }
        }

        attributesByKey = Maps.newLinkedHashMap();
        if (attributes!=null) {
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                attributesByKey.put(getAttributeKey(entry.getKey()), entry.getValue());
            }
        }
    }
    
    @Override
    public boolean isTopLevelApp() {
        return isTopLevelApp!=null ? isTopLevelApp : getParent()==null;
    }
    
    @Override
    public Map<ConfigKey<?>, Object> getConfig() {
        if (configByKey == null) postDeserialize();
        return Collections.unmodifiableMap(configByKey);
    }
    
    @Override
    public Map<String, Object> getConfigUnmatched() {
        if (configUnmatched == null) postDeserialize();
        return Collections.unmodifiableMap(configUnmatched);
    }
    
    @Override
    public Map<AttributeSensor<?>, Object> getAttributes() {
        if (attributesByKey == null) postDeserialize();
        return Collections.unmodifiableMap(attributesByKey);
    }

    public List<Effector<?>> getEffectors() {
        return fromPersistedList(effectors);
    }
    
    @Override
    public List<String> getPolicies() {
        return fromPersistedList(policies);
    }
    
    @Override
    public List<String> getEnrichers() {
        return fromPersistedList(enrichers);
    }
    
    @Override
    public List<String> getMembers() {
        return fromPersistedList(members);
    }
    
    @Override
    public List<String> getLocations() {
        return fromPersistedList(locations);
    }

    @Override
    public List<String> getFeeds() {
        return fromPersistedList(feeds);
    }
    
    @Override
    protected ToStringHelper newVerboseStringHelper() {
        return super.newVerboseStringHelper()
                .add("members", getMembers())
                .add("config", Sanitizer.sanitize(getConfig()))
                .add("configUnmatched", Sanitizer.sanitize(getConfigUnmatched()))
                .add("attributes", Sanitizer.sanitize(getAttributes()))
                .add("policies", getPolicies())
                .add("enrichers", getEnrichers())
                .add("locations", getLocations());
    }

}
