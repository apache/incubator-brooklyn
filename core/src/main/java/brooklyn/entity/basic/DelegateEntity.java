package brooklyn.entity.basic;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;

/**
 * A delegate entity for use as a {@link Group} child proxy for members.
 */
@ImplementedBy(DelegateEntityImpl.class)
public interface DelegateEntity extends Entity {

    ConfigKey<Entity> DELEGATE_ENTITY = ConfigKeys.newConfigKey(Entity.class, "delegate.entity", "The delegate entity");

}
