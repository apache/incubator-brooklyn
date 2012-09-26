package brooklyn.mementos;

import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.event.AttributeSensor;

/**
 * Represents the state of an entity, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see Entity.getMemento()
 * @see Entity.rebind(BrooklynMemento, String)
 * 
 * @author aled
 */
public interface EntityMemento extends Memento {

    // TODO Think about which sensors; how would sub-class supply that (given that super-constructor is doing work)
    
    public String getType();
    
    public String getId();
    
    public String getDisplayName();
    
    public Map<ConfigKey, Object> getConfig();

    public Map<AttributeSensor, Object> getAttributes();

    public Set<AttributeSensor> getEntityReferenceAttributes();
    
    public Set<ConfigKey> getEntityReferenceConfigs();

    /**
     * The id of the parent entity, or null if none (e.g. if top-level app).
     */
    public String getParent();
    
    /**
     * The ids of the child entities.
     * 
     * @see Entity.getOwnedChildren()
     */
    public List<String> getChildren();

    /**
     * The ids of the member entities, if this is a Group; otherwise empty.
     * 
     * @see Group.getMembers()
     */
    public List<String> getMembers();
    
    /**
     * The ids of the locations for this entity.
     */
    public List<String> getLocations();

    /**
     * A (weakly-typed) property set for this memento.
     * These can be used to avoid sub-classing the entity memento, but developers can sub-class to get strong typing if desired.
     */
    public Object getCustomProperty(String name);

	public Map<String, ? extends Object> getCustomProperties();
}
