package brooklyn.entity.rebind;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.location.Location;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.TreeNode;
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
public class BasicLocationMemento extends AbstractMemento implements LocationMemento, Serializable {

    private static final long serialVersionUID = -4025337943126838761L;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractMemento.Builder<Builder> {
        private String type;
        private String displayName;
        private Map<String,Object> locationProperties = Maps.newLinkedHashMap();
        private Map<String,Object> flags = Maps.newLinkedHashMap();
        private Set<String> locationReferenceFlags = Sets.newLinkedHashSet();
        
        /**
         * Given a location, extracts its state for serialization.
         * 
         * For bits of state that are references to other locations, these are treated in a special way:
         * the location reference is replaced by the location id.
         * TODO When we have a cleaner separation of constructor/config for entities and locations, then
         * we will remove this code!
         */
        public Builder from(Location location) {
            type = location.getClass().getName();
            id = location.getId();
            displayName = location.getName();
            locationProperties.putAll(location.getLocationProperties());

            flags.putAll(FlagUtils.getFieldsWithFlagsExcludingModifiers(location, Modifier.STATIC ^ Modifier.TRANSIENT));
            for (Map.Entry<String, Object> entry : flags.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                Object transformedValue = MementoTransformer.transformLocationsToIds(value);
                if (transformedValue != value) {
                    entry.setValue(transformedValue);
                    locationReferenceFlags.add(key);
                }
            }
            
            Location parentLocation = location.getParentLocation();
            parent = (parentLocation != null) ? parentLocation.getId() : null;
            
            for (Location child : location.getChildLocations()) {
                children.add(child.getId()); 
            }
            
            return this;
        }
        public Builder from(LocationMemento other) {
            super.from((TreeNode)other);
            type = other.getType();
            displayName = other.getDisplayName();
            locationProperties.putAll(other.getLocationProperties());
            flags.putAll(other.getFlags());
            locationReferenceFlags.addAll(other.getLocationReferenceFlags());
            customProperties.putAll(other.getCustomProperties());
            return this;
        }
        public LocationMemento build() {
            return new BasicLocationMemento(this);
        }
    }
    
    private String type;
    private Map<String,Object> locationProperties;
	private Map<String,Object> flags;
	private Set<String> locationReferenceFlags;

    // Trusts the builder to not mess around with mutability after calling build()
	protected BasicLocationMemento(Builder builder) {
	    super(builder);
	    type = builder.type;
	    locationProperties = Collections.unmodifiableMap(builder.locationProperties);
	    flags = Collections.unmodifiableMap(builder.flags);
	    locationReferenceFlags = Collections.unmodifiableSet(builder.locationReferenceFlags);
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
    public String toString() {
    	return Objects.toStringHelper(this).add("type", type).add("id", getId()).toString();
    }
}
