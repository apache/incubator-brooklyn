package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.TreeNode;

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
        protected String type;
        protected Map<String,Object> locationProperties = Maps.newLinkedHashMap();
        protected Map<String,Object> flags = Maps.newLinkedHashMap();
        protected Set<String> locationReferenceFlags = Sets.newLinkedHashSet();
        
        public Builder from(LocationMemento other) {
            super.from((TreeNode)other);
            type = other.getType();
            displayName = other.getDisplayName();
            locationProperties.putAll(other.getLocationProperties());
            flags.putAll(other.getFlags());
            locationReferenceFlags.addAll(other.getLocationReferenceFlags());
            customProperties.putAll(other.getCustomProperties());
            return self();
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
