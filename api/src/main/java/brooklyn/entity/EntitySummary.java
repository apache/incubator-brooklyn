package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;

public interface EntitySummary extends Serializable {

    /**
     * @return The unique identifier for this entity.
     */
    String getId();
    
    EntityClass getEntityClass();
    
    /**
     * A display name; recommended to be a concise single-line description.
     */
    String getDisplayName();
    
    String getApplicationId();
    
    String getParentId();
    
    /**
     * The id of the group-entities that this entity is a member of.
     * 
     * TODO Entity.getParents() makes me think of containment relationships too much. I'd prefer groups?
     */
    Collection<String> getGroupIds();

    // TODO Add getLocation when needed
}
