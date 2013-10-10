package brooklyn.entity.proxy;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(AbstractNonProvisionedControllerImpl.class)
public interface AbstractNonProvisionedController extends LoadBalancer, Entity {

    public boolean isActive();
}
