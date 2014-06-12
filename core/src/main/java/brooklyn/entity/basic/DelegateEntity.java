package brooklyn.entity.basic;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

/**
 * A delegate entity for use as a {@link Group} child proxy for members.
 */
@ImplementedBy(DelegateEntityImpl.class)
public interface DelegateEntity extends Entity {

    ConfigKey<Entity> DELEGATE_ENTITY = ConfigKeys.newConfigKey(Entity.class, "delegate.entity", "The delegate entity");

    AttributeSensor<Entity> DELEGATE_ENTITY_LINK = Sensors.newSensor(Entity.class, "webapp.url", "The delegate entity link");

}
