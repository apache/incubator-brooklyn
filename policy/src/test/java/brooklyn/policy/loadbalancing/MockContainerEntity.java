package brooklyn.policy.loadbalancing;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(MockContainerEntityImpl.class)
public interface MockContainerEntity extends AbstractGroup, BalanceableContainer<Movable>, Startable {

    @SetFromFlag("membership")
    public static final ConfigKey<String> MOCK_MEMBERSHIP = new BasicConfigKey<String>(
            String.class, "mock.container.membership", "For testing ItemsInContainersGroup");

    @SetFromFlag("delay")
    public static final ConfigKey<Long> DELAY = new BasicConfigKey<Long>(
            Long.class, "mock.container.delay", "", 0L);

    public static final Effector OFFLOAD_AND_STOP = new MethodEffector(MockContainerEntity.class, "offloadAndStop");

    public void lock();

    public void unlock();

    public int getWorkrate();

    public Map<Entity, Double> getItemUsage();

    public void addItem(Entity item);

    public void removeItem(Entity item);

    public void offloadAndStop(MockContainerEntity otherContainer);
}
