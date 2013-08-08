package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;

/**
 * This is the most basic entity possible - does nothing beyond AbstractEntity.
 * Useful when structuring the entity management hierarchy; also consider using
 * {@link BasicGroup}.
 * 
 * Example usage is:
 * {@code Entity entity = getEntityManager().createEntity(EntitySpec.create(BasicEntity.class))}.
 * 
 * @author aled
 */
@ImplementedBy(BasicEntityImpl.class)
public interface BasicEntity extends Entity {
}
