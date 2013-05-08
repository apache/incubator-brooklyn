package brooklyn.policy.loadbalancing;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

@ImplementedBy(MockItemEntityImpl.class)
public interface MockItemEntity extends Entity, Movable {

    public static final AttributeSensor<Integer> TEST_METRIC = new BasicAttributeSensor<Integer>(
            Integer.class, "test.metric", "Dummy workrate for test entities");
    
    public boolean isStopped();

    public void moveNonEffector(Entity rawDestination);
    
    public void stop();
}
