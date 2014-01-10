package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.TreeNode;
import brooklyn.util.config.ConfigBag;

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
        protected Map<String,Object> locationConfig = Maps.newLinkedHashMap();
        protected Set<String> locationConfigUnused = Sets.newLinkedHashSet();
        protected String locationConfigDescription;
        protected Set<String> locationConfigReferenceKeys = Sets.newLinkedHashSet();
        protected Set<String> entityConfigReferenceKeys = Sets.newLinkedHashSet();
        
        public Builder from(LocationMemento other) {
            super.from((TreeNode)other);
            displayName = other.getDisplayName();
            locationConfig.putAll(other.getLocationConfig());
            locationConfigUnused.addAll(other.getLocationConfigUnused());
            locationConfigDescription = other.getLocationConfigDescription();
            locationConfigReferenceKeys.addAll(other.getLocationConfigReferenceKeys());
            entityConfigReferenceKeys.addAll(other.getEntityConfigReferenceKeys());
            fields.putAll(other.getCustomFields());
            return self();
        }
        public LocationMemento build() {
            return new BasicLocationMemento(this);
        }
        public void copyConfig(ConfigBag config) {
            locationConfig.putAll(config.getAllConfig());
            locationConfigUnused.addAll(config.getUnusedConfig().keySet());
            locationConfigDescription = config.getDescription();
        }
    }
    
    private Map<String,Object> locationConfig;
	private Set<String> locationConfigUnused;
	private String locationConfigDescription;
	private Set<String> locationConfigReferenceKeys;
    private Set<String> entityConfigReferenceKeys;

    // Trusts the builder to not mess around with mutability after calling build()
	protected BasicLocationMemento(Builder builder) {
	    super(builder);
	    locationConfig = toPersistedMap(builder.locationConfig);
	    locationConfigUnused = toPersistedSet(builder.locationConfigUnused);
	    locationConfigDescription = builder.locationConfigDescription;
	    locationConfigReferenceKeys = toPersistedSet(builder.locationConfigReferenceKeys);
        entityConfigReferenceKeys = toPersistedSet(builder.entityConfigReferenceKeys);
	}
	
    @Override
    public Map<String,Object> getLocationConfig() {
		return fromPersistedMap(locationConfig);
	}
	
    @Override
    public Set<String> getLocationConfigUnused() {
		return fromPersistedSet(locationConfigUnused);
	}
    
    @Override
    public String getLocationConfigDescription() {
        return locationConfigDescription;
    }
    
    @Override
    public Set<String> getLocationConfigReferenceKeys() {
    	return fromPersistedSet(locationConfigReferenceKeys);
    }
    
    @Override
    public Set<String> getEntityConfigReferenceKeys() {
        return fromPersistedSet(entityConfigReferenceKeys);
    }
}
