package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * The persisted state of a location.
 * 
 * @author aled
 */
public class BasicPolicyMemento implements PolicyMemento, Serializable {

    private static final long serialVersionUID = -4025337943126838761L;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        protected String id;
        protected String parent;
        protected List<String> children = Lists.newArrayList();
        protected String displayName;
        protected String type;
        protected Map<String,Object> flags = Maps.newLinkedHashMap();
        protected Map<String,Object> customProperties = Maps.newLinkedHashMap();
        
        public Builder from(LocationMemento other) {
            id = other.getId();
            type = other.getType();
            displayName = other.getDisplayName();
            flags.putAll(other.getFlags());
            customProperties.putAll(other.getCustomProperties());
            return this;
        }
        public Builder customProperties(Map<String,?> vals) {
            customProperties.putAll(vals); return this;
        }
        public PolicyMemento build() {
            return new BasicPolicyMemento(this);
        }
    }
    
    private String id;
    private String type;
    private String displayName;
	private Map<String,Object> flags;
    private Map<String,Object> customProperties;

    // Trusts the builder to not mess around with mutability after calling build()
	protected BasicPolicyMemento(Builder builder) {
        id = builder.id;
        type = builder.type;
        displayName = builder.displayName;
	    flags = Collections.unmodifiableMap(builder.flags);
	    customProperties = Collections.unmodifiableMap(builder.customProperties );
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
    public String getId() {
        return id;
    }
    
	@Override
    public String getType() {
        return type;
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public Map<String, Object> getFlags() {
		return flags;
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
    	return Objects.toStringHelper(this).add("type", type).add("id", getId()).toString();
    }
}
