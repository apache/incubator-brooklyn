package brooklyn.entity.rebind;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.location.Location;
import brooklyn.mementos.EntityMemento;
import brooklyn.util.MutableMap;

import com.google.common.base.Objects;

/**
 * Represents the state of an entity, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see AbstractEntity.getMemento()
 * @see AbstractEntity.rebind
 * 
 * @author aled
 */
public class BasicEntityMemento implements EntityMemento, Serializable {

    // TODO Think about which sensors; how would sub-class supply that (given that super-constructor is doing work)
    
    private static final long serialVersionUID = 8642959541121050126L;
    
    private String type;
    private String id;
    private String displayName;
    private Map<ConfigKey, Object> config;
    private Map<AttributeSensor, Object> attributes;
    private List<String> locations;
    private String parent;
    private List<String> children;
    private List<String> members;
    private Map<String,Object> properties;
    
    public BasicEntityMemento(Entity entity) {
        this(entity, Collections.<String,Object>emptyMap());
    }
    
    public BasicEntityMemento(Entity entity, Map<String,?> properties) {
        id = entity.getId();
        displayName = entity.getDisplayName();
        type = entity.getClass().getName();
        
        this.properties = (properties != null) ? MutableMap.copyOf(properties) : Collections.<String,Object>emptyMap();
        
        config = new LinkedHashMap<ConfigKey,Object>(entity.getEntityType().getConfigKeys().size());
        for (ConfigKey<?> key : entity.getEntityType().getConfigKeys()) {
            config.put(key, entity.getConfig(key)); 
        }
        config = Collections.unmodifiableMap(config);
        
        attributes = new LinkedHashMap<AttributeSensor,Object>(entity.getEntityType().getSensors().size());
        for (Sensor<?> key : entity.getEntityType().getSensors()) {
            if (key instanceof AttributeSensor) {
                attributes.put((AttributeSensor<?>)key, entity.getAttribute((AttributeSensor<?>)key));
            }
        }
        attributes = Collections.unmodifiableMap(attributes);
        
        locations = new ArrayList<String>(entity.getLocations().size());
        for (Location location : entity.getLocations()) {
            locations.add(location.getId()); 
        }
        locations = Collections.unmodifiableList(locations);
        
        children = new ArrayList<String>(entity.getOwnedChildren().size());
        for (Entity child : entity.getOwnedChildren()) {
            children.add(child.getId()); 
        }
        children = Collections.unmodifiableList(children);
        
        Entity parentEntity = entity.getOwner();
        parent = (parentEntity != null) ? parentEntity.getId() : null;

        members = new ArrayList<String>();
        if (entity instanceof Group) {
            for (Entity member : ((Group)entity).getMembers()) {
                members.add(member.getId()); 
            }
        }
        members = Collections.unmodifiableList(members);
    }

    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
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
    public String getParent() {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public List<String> getChildren() {
        return children;
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
    public Object getProperty(String name) {
        return properties.get(name);
    }
    
	public Map<String, ? extends Object> getProperties() {
		return properties;
	}
	
    @Override
    public String toString() {
    	return Objects.toStringHelper(this).add("type", type).add("id", id).toString();
    }
}
