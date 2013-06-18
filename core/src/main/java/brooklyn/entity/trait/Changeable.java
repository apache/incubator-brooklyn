package brooklyn.entity.trait;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.Sensors;

/**
 * A collection of entities that can change.
 */
public interface Changeable {
    AttributeSensor<Integer> GROUP_SIZE = Sensors.newIntegerSensor("group.members.count", "Number of members");
    BasicNotificationSensor<Entity> MEMBER_ADDED = new BasicNotificationSensor<Entity>(Entity.class, "group.members.added", "Entity added to group members");
    BasicNotificationSensor<Entity> MEMBER_REMOVED = new BasicNotificationSensor<Entity>(Entity.class, "group.members.removed", "Entity removed from group members");
}
