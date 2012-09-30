package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.TreeNode;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Represents the state of an entity, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see AbstractEntity.getMemento()
 * @see AbstractEntity.rebind
 * 
 * @author aled
 */
@JsonAutoDetect(fieldVisibility=Visibility.ANY, getterVisibility=Visibility.NONE)
public class BasicEntityMemento extends AbstractMemento implements EntityMemento, Serializable {

    // TODO Think about which sensors - is all of them the right thing?
    
    private static final long serialVersionUID = 8642959541121050126L;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractMemento.Builder<Builder> {
        protected String type;
        protected Map<ConfigKey, Object> config = Maps.newLinkedHashMap();
        protected Map<AttributeSensor, Object> attributes = Maps.newLinkedHashMap();
        protected Set<ConfigKey> entityReferenceConfigs = Sets.newLinkedHashSet();
        protected Set<AttributeSensor> entityReferenceAttributes = Sets.newLinkedHashSet();
        protected List<String> locations = Lists.newArrayList();
        protected List<String> members = Lists.newArrayList();
        
        public Builder type(String val) {
            type = val; return this;
        }
        public Builder config(Map<ConfigKey, Object> val) {
            config = val; return this;
        }
        public Builder attributes(Map<AttributeSensor, Object> val) {
            attributes = val; return this;
        }
        public Builder entityReferenceConfigs(Set<ConfigKey> val) {
            entityReferenceConfigs = val; return this;
        }
        public Builder entityReferenceAttributes(Set<AttributeSensor> val) {
            entityReferenceAttributes = val; return this;
        }
        public Builder locations(List<String> val) {
            locations = val; return this;
        }
        public Builder  members(List<String> val) {
            members = val; return this;
        }
        public Builder from(EntityMemento other) {
            super.from((TreeNode)other);
            type = other.getType();
            displayName = other.getDisplayName();
            config.putAll(other.getConfig());
            attributes.putAll(other.getAttributes());
            entityReferenceConfigs.addAll(other.getEntityReferenceConfigs());
            entityReferenceAttributes.addAll(other.getEntityReferenceAttributes());
            locations.addAll(other.getLocations());
            members.addAll(other.getMembers());
            customProperties.putAll(other.getCustomProperties());
            return this;
        }
        public EntityMemento build() {
            return new BasicEntityMemento(this);
        }
    }
    
    private String type;
    private Map<String, Object> config;
    private Map<String, Object> attributes;
    private Set<String> entityReferenceConfigs;
    private Set<String> entityReferenceAttributes;
    private List<String> locations;
    private List<String> members;
    private Map<String, ConfigKey> configKeys;
    private Map<String, AttributeSensor> attributeKeys;
    
    private transient Map<ConfigKey, Object> configByKey;
    private transient Map<AttributeSensor, Object> attributesByKey;
    private transient Set<ConfigKey> entityReferenceConfigsByKey;
    private transient Set<AttributeSensor> entityReferenceAttributesByKey;

    // for de-serialization
    @SuppressWarnings("unused")
    private BasicEntityMemento() {
    }

    // Trusts the builder to not mess around with mutability after calling build()
    protected BasicEntityMemento(Builder builder) {
        super(builder);
        type = builder.type;
        locations = Collections.unmodifiableList(builder.locations);
        members = Collections.unmodifiableList(builder.members);
        configByKey = Collections.unmodifiableMap(builder.config);
        attributesByKey = Collections.unmodifiableMap(builder.attributes);
        entityReferenceConfigsByKey = Collections.unmodifiableSet(builder.entityReferenceConfigs);
        entityReferenceAttributesByKey = Collections.unmodifiableSet(builder.entityReferenceAttributes);
        
        configKeys = Maps.newLinkedHashMap();
        config = Maps.newLinkedHashMap();
        for (Map.Entry<ConfigKey, Object> entry : configByKey.entrySet()) {
            ConfigKey key = entry.getKey();
            configKeys.put(key.getName(), key);
            config.put(key.getName(), entry.getValue());
        }
        
        attributeKeys = Maps.newLinkedHashMap();
        attributes = Maps.newLinkedHashMap();
        for (Map.Entry<AttributeSensor, Object> entry : attributesByKey.entrySet()) {
            AttributeSensor key = entry.getKey();
            attributeKeys.put(key.getName(), key);
            attributes.put(key.getName(), entry.getValue());
        }
        
        entityReferenceConfigs = Sets.newLinkedHashSet();
        for (ConfigKey key : entityReferenceConfigsByKey) {
            entityReferenceConfigs.add(key.getName());
        }
        
        entityReferenceAttributes = Sets.newLinkedHashSet();
        for (AttributeSensor key : entityReferenceAttributesByKey) {
            entityReferenceAttributes.add(key.getName());
        }
    }

    private void postDeserialize() {
        configByKey = Maps.newLinkedHashMap();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            configByKey.put(configKeys.get(entry.getKey()), entry.getValue());
        }
        configByKey = Collections.unmodifiableMap(configByKey);
        
        attributesByKey = Maps.newLinkedHashMap();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            attributesByKey.put(attributeKeys.get(entry.getKey()), entry.getValue());
        }
        attributesByKey = Collections.unmodifiableMap(attributesByKey);
        
        entityReferenceConfigsByKey = Sets.newLinkedHashSet();
        for (String key : entityReferenceConfigs) {
            entityReferenceConfigsByKey.add(configKeys.get(key));
        }
        entityReferenceConfigsByKey = Collections.unmodifiableSet(entityReferenceConfigsByKey);

        entityReferenceAttributesByKey = Sets.newLinkedHashSet();
        for (String key : entityReferenceAttributes) {
            entityReferenceAttributesByKey.add(attributeKeys.get(key));
        }
        entityReferenceAttributesByKey = Collections.unmodifiableSet(entityReferenceAttributesByKey);
    }
    
    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public Map<ConfigKey, Object> getConfig() {
        if (configByKey == null) postDeserialize();
        return configByKey;
    }
    
    @Override
    public Map<AttributeSensor, Object> getAttributes() {
        if (attributesByKey == null) postDeserialize();
        return attributesByKey;
    }
    
    @Override
    public Set<AttributeSensor> getEntityReferenceAttributes() {
        if (entityReferenceAttributesByKey == null) postDeserialize();
        return entityReferenceAttributesByKey;
    }
    
    @Override
    public Set<ConfigKey> getEntityReferenceConfigs() {
        if (entityReferenceConfigsByKey == null) postDeserialize();
        return entityReferenceConfigsByKey;
    }
    
    @Override
    public List<String> getMembers() {
        return members;
    }
    
    @Override
    public List<String> getLocations() {
        return locations;
    }

    @Override
    public String toString() {
    	return Objects.toStringHelper(this).add("type", type).add("id", getId()).toString();
    }
}
