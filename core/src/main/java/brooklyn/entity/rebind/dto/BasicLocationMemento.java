package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.TreeNode;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * The persisted state of a location.
 * 
 * @author aled
 */
public class BasicLocationMemento extends AbstractTreeNodeMemento implements LocationMemento, Serializable {

    private static final long serialVersionUID = -4025337943126838761L;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractTreeNodeMemento.Builder<Builder> {
        protected Map<String,Object> locationProperties = Maps.newLinkedHashMap();
        protected Map<String,Object> flags = Maps.newLinkedHashMap();
        protected Set<String> locationReferenceFlags = Sets.newLinkedHashSet();
        
        public Builder from(LocationMemento other) {
            super.from((TreeNode)other);
            displayName = other.getDisplayName();
            locationProperties.putAll(other.getLocationProperties());
            flags.putAll(other.getFlags());
            locationReferenceFlags.addAll(other.getLocationReferenceFlags());
            fields.putAll(other.getCustomFields());
            return self();
        }
        public LocationMemento build() {
            return new BasicLocationMemento(this);
        }
    }
    
    private Map<String,Object> locationProperties;
	private Map<String,Object> flags;
	private Set<String> locationReferenceFlags;

    // Trusts the builder to not mess around with mutability after calling build()
	protected BasicLocationMemento(Builder builder) {
	    super(builder);
	    locationProperties = toPersistedMap(builder.locationProperties);
	    flags = toPersistedMap(builder.flags);
	    locationReferenceFlags = toPersistedSet(builder.locationReferenceFlags);
	}
	
    @Override
    public Map<String,Object> getLocationProperties() {
		return fromPersistedMap(locationProperties);
	}
	
    @Override
    public Map<String, Object> getFlags() {
		return fromPersistedMap(flags);
	}
    
    @Override
    public Set<String> getLocationReferenceFlags() {
    	return fromPersistedSet(locationReferenceFlags);
    }
}
