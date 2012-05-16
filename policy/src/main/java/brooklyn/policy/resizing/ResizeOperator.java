package brooklyn.policy.resizing;

import brooklyn.entity.Entity;

public interface ResizeOperator {

    /**
     * Resizes the given entity to the desired size, if possible.
     * 
     * @return the new size of the entity
     */
    public Integer resize(Entity entity, Integer desiredSize);
}
