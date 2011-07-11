package brooklyn.entity.trait;

import brooklyn.entity.Entity;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Defines an entity group that can be re-sized dynamically. By invoking the @{link #resize} effector, the number of child nodes
 * can be reduced (by shutting down some of them) or increased (by provisioning new entities.)
 */
public interface Resizable {
    
    /**
     * Grow or shrink this entity to the desired size.
     * @param desiredSize the new size of the entity group.
     * @return a ResizeResult object that describes the outcome of the action.
     */
    ResizeResult resize(int desiredSize);
}

