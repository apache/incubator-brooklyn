package brooklyn.entity.rebind;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.location.Location;
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
public class BasicEntityMemento extends AbstractMemento implements EntityMemento, Serializable {

    // TODO Think about which sensors - is all of them the right thing?
    
    private static final long serialVersionUID = 8642959541121050126L;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractMemento.Builder<Builder> {
        private String type;
        private Map<ConfigKey, Object> config = Maps.newLinkedHashMap();
        private Map<AttributeSensor, Object> attributes = Maps.newLinkedHashMap();
        private Set<ConfigKey> entityReferenceConfigs = Sets.newLinkedHashSet();
        private Set<AttributeSensor> entityReferenceAttributes = Sets.newLinkedHashSet();
        private List<String> locations = Lists.newArrayList();
        private List<String> members = Lists.newArrayList();
        
        public Builder from(Entity entity) {
            id = entity.getId();
            displayName = entity.getDisplayName();
            type = entity.getClass().getName();
            
            for (ConfigKey<?> key : entity.getEntityType().getConfigKeys()) {
                Object value = entity.getConfig(key);
                Object transformedValue = MementoTransformer.transformEntitiesToIds(value);
                if (transformedValue != value) {
                    entityReferenceConfigs.add(key);
                }
                config.put(key, transformedValue); 
            }
            
            for (Sensor<?> key : entity.getEntityType().getSensors()) {
                if (key instanceof AttributeSensor) {
                    Object value = entity.getAttribute((AttributeSensor<?>)key);
                    Object transformedValue = MementoTransformer.transformEntitiesToIds(value);
                    if (transformedValue != value) {
                        entityReferenceAttributes.add((AttributeSensor<?>)key);
                    }
                    attributes.put((AttributeSensor<?>)key, transformedValue);

                }
            }
            
            for (Location location : entity.getLocations()) {
                locations.add(location.getId()); 
            }
            
            for (Entity child : entity.getOwnedChildren()) {
                children.add(child.getId()); 
            }
            
            Entity parentEntity = entity.getOwner();
            parent = (parentEntity != null) ? parentEntity.getId() : null;

            if (entity instanceof Group) {
                for (Entity member : ((Group)entity).getMembers()) {
                    members.add(member.getId()); 
                }
            }
            
            return this;
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
    private Map<ConfigKey, Object> config;
    private Map<AttributeSensor, Object> attributes;
    private Set<ConfigKey> entityReferenceConfigs;
    private Set<AttributeSensor> entityReferenceAttributes;
    private List<String> locations;
    private List<String> members;
    
    // Trusts the builder to not mess around with mutability after calling build()
    protected BasicEntityMemento(Builder builder) {
        super(builder);
        type = builder.type;
        config = Collections.unmodifiableMap(builder.config);
        attributes = Collections.unmodifiableMap(builder.attributes);
        entityReferenceConfigs = Collections.unmodifiableSet(builder.entityReferenceConfigs);
        entityReferenceAttributes = Collections.unmodifiableSet(builder.entityReferenceAttributes);
        locations = Collections.unmodifiableList(builder.locations);
        members = Collections.unmodifiableList(builder.members);
    }

    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public Map<ConfigKey, Object> getConfig() {
        return config;
    }
    
    @Override
    public Map<AttributeSensor, Object> getAttributes() {
        return attributes;
    }
    
    @Override
    public Set<AttributeSensor> getEntityReferenceAttributes() {
        return entityReferenceAttributes;
    }
    
    @Override
    public Set<ConfigKey> getEntityReferenceConfigs() {
        return entityReferenceConfigs;
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
