package brooklyn.entity.rebind;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.location.Location;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.FlagUtils;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * The persisted state of a location.
 * 
 * @author aled
 */
public class BasicLocationMemento implements LocationMemento, Serializable {

    private static final long serialVersionUID = -4025337943126838761L;
    
    private String type;
    private String id;
    private String displayName;
    private String parent;
    private List<String> children;
    private Map<String,Object> locationProperties;
	private Map<String,Object> flags;
	private Set<String> locationReferenceFlags;
	private Map<String,Object> customProperties;

    public BasicLocationMemento(Location location) {
        this(location, Collections.<String,Object>emptyMap());
    }
    
    /**
     * Given a location (and some additional custom properties), extracts its state for serialization.
     * 
     * For bits of state that are references to other locations, these are treated in a special way:
     * the location reference is replaced by the location id.
     * TODO When we have a cleaner separation of constructor/config for entities and locations, then
     * we will remove this code!
     * 
     * @param location
     * @param customProperties
     */
    public BasicLocationMemento(Location location, Map<String,?> customProperties) {
        type = location.getClass().getName();
        id = location.getId();
        displayName = location.getName();
        locationProperties = Collections.unmodifiableMap(MutableMap.copyOf(location.getLocationProperties()));

        locationReferenceFlags = Sets.newLinkedHashSet();
        
    	flags = FlagUtils.getFieldsWithFlagsExcludingModifiers(location, Modifier.STATIC ^ Modifier.TRANSIENT);
    	for (Map.Entry<String, Object> entry : flags.entrySet()) {
    		String key = entry.getKey();
    		Object value = entry.getValue();
    		Object transformedValue = MementoTransformer.transformLocationsToIds(value);
    		if (transformedValue != value) {
    			entry.setValue(transformedValue);
    			locationReferenceFlags.add(key);
    		}
    	}
    	flags = Collections.unmodifiableMap(flags);
    	locationReferenceFlags = Collections.unmodifiableSet(locationReferenceFlags);
    	
        Location parentLocation = location.getParentLocation();
        parent = (parentLocation != null) ? parentLocation.getId() : null;
        
        children = new ArrayList<String>(location.getChildLocations().size());
        for (Location child : location.getChildLocations()) {
            children.add(child.getId()); 
        }
        children = Collections.unmodifiableList(children);
        
        this.customProperties = (customProperties != null) ? MutableMap.copyOf(customProperties) : Collections.<String,Object>emptyMap();
    }

    
    private <K,V> Map<K,V> createOfSameType(Map<K,V> orig) {
    	return Maps.newLinkedHashMap();
    }
    
    private Collection<Object> createOfSameType(Iterable<?> orig) {
    	if (orig instanceof List) {
    		return Lists.newArrayList();
    	} else if (orig instanceof Set) {
    		return Sets.newLinkedHashSet();
    	} else {
    		return Lists.newArrayList();
    	}
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
    };
    
    @Override
    public String getParent() {
        return parent;
    }
    
    @Override
    public List<String> getChildren() {
        return children;
    }
    
    @Override
    public Map<String,Object> getLocationProperties() {
		return locationProperties;
	}
	
    @Override
    public Map<String, Object> getFlags() {
		return flags;
	}
    
    @Override
    public Set<String> getLocationReferenceFlags() {
    	return locationReferenceFlags;
    }
    
    @Override
    public Object getCustomProperty(String name) {
        return customProperties.get(name);
    }
    
    @Override
    public Map<String, ? extends Object> getCustomProperties() {
        return customProperties;
    }
    
    @Override
    public String toString() {
    	return Objects.toStringHelper(this).add("type", type).add("id", id).toString();
    }
}
