package brooklyn.mementos;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.event.AttributeSensor;

/**
 * Represents the state of an entity, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see RebindSupport
 * 
 * @author aled
 */
public interface EntityMemento extends Memento, TreeNode {

    public boolean isTopLevelApp();
    
    public Map<ConfigKey, Object> getConfig();

    public Map<AttributeSensor, Object> getAttributes();

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
     * The ids of the policies of this entity.
     */
    public Collection<String> getPolicies();
}
