package brooklyn.entity.trait;

import brooklyn.entity.Entity;

import java.util.Collection;

/**
 * Describes the result of a call to @{link Resizable#resize}.
 */
public interface ResizeResult {

    /**
     * The change in the number of entities. A positive number is the number of entities added to the group; a negative number
     * indicates the number of nodes removed from the group.
     * @return the change in the number of entities.
     */
    int getDelta();

    /**
     * Gets the entities that were added in this resize operation. May return null if @{link #getDelta} is equal to or less than
     * zero.
     */
    Collection<Entity> getAddedEntities();

    /**
     * Gets the entities that were removed in this resize operation. May return null if @{link #getDelta} is equal to or greater
     * than zero.
     */
    Collection<Entity> getRemovedEntities();

}
