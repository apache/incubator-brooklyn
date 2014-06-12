package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.AttributeSensorAndConfigKey;

/**
 * A delegate entity for use as a {@link Group} child proxy for members.
 */
@ImplementedBy(DelegateEntityImpl.class)
public interface DelegateEntity extends Entity {

    AttributeSensorAndConfigKey<Entity, Entity> DELEGATE_ENTITY = ConfigKeys.newSensorAndConfigKey(Entity.class, "delegate.entity", "The delegate entity");

}
