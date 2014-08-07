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
package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

import brooklyn.basic.BrooklynTypes;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.TreeNode;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Represents the state of an entity, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see AbstractEntity#getRebindSupport()
 * @see RebindSupport#getMemento()
 * @see RebindSupport#reconstruct(brooklyn.entity.rebind.RebindContext, brooklyn.mementos.Memento)
 * 
 * @author aled
 */
@JsonAutoDetect(fieldVisibility=Visibility.ANY, getterVisibility=Visibility.NONE)
public class BasicEntityMemento extends AbstractTreeNodeMemento implements EntityMemento, Serializable {

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
            members.addAll(other.getMembers());
            effectors.addAll(other.getEffectors());
            tags.addAll(other.getTags());
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
    
    // TODO can we move some of these to entity type, or remove/re-insert those which are final statics?
    private Map<String, ConfigKey<?>> configKeys;
    private transient Map<String, ConfigKey<?>> staticConfigKeys;
    private Map<String, AttributeSensor<?>> attributeKeys;
    private transient Map<String, Sensor<?>> staticSensorKeys;
    private List<Effector<?>> effectors;
    
    private transient Map<ConfigKey<?>, Object> configByKey;
    private transient Map<String, Object> configUnmatched;
    private transient Map<AttributeSensor<?>, Object> attributesByKey;

    // for de-serialization
    @SuppressWarnings("unused")
    private BasicEntityMemento() {
    }

    // Trusts the builder to not mess around with mutability after calling build() -- with invalidate pattern
    // Does not make any attempt to make unmodifiable, or immutable copy, to have cleaner (and faster) output
    protected BasicEntityMemento(Builder builder) {
        super(builder);
        
        isTopLevelApp = builder.isTopLevelApp==null || builder.isTopLevelApp==(getParent()==null) ? null : builder.isTopLevelApp;
        
        locations = toPersistedList(builder.locations);
        policies = toPersistedList(builder.policies);
        enrichers = toPersistedList(builder.enrichers);
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

    protected ConfigKey<?> getConfigKey(String key) {
        if (configKeys!=null) {
            ConfigKey<?> ck = configKeys.get(key);
            if (ck!=null) return ck;
        }
        return getStaticConfigKeys().get(key);
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
        if (attributeKeys!=null) {
            AttributeSensor<?> ak = attributeKeys.get(key);
            if (ak!=null) return ak;
        }
        return (AttributeSensor<?>) getStaticSensorKeys().get(key);
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
    protected ToStringHelper newVerboseStringHelper() {
        return super.newVerboseStringHelper()
                .add("members", getMembers())
                .add("config", Entities.sanitize(getConfig()))
                .add("configUnmatched", Entities.sanitize(getConfigUnmatched()))
                .add("attributes", Entities.sanitize(getAttributes()))
                .add("policies", getPolicies())
                .add("enrichers", getEnrichers())
                .add("tags", getTags())
                .add("locations", getLocations());
    }

}
