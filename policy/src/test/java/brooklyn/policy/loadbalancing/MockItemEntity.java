package brooklyn.policy.loadbalancing;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

import com.google.common.reflect.TypeToken;

@ImplementedBy(MockItemEntityImpl.class)
public interface MockItemEntity extends Entity, Movable {

    public static final AttributeSensor<Integer> TEST_METRIC = Sensors.newIntegerSensor(
            "test.metric", "Dummy workrate for test entities");

    public static final AttributeSensor<Map<Entity, Double>> ITEM_USAGE_METRIC = Sensors.newSensor(
            new TypeToken<Map<Entity, Double>>() {}, "test.itemUsage.metric", "Dummy item usage for test entities");

    public boolean isStopped();

    public void moveNonEffector(Entity rawDestination);
    
    public void stop();
}
