package brooklyn.entity.trait;

import brooklyn.entity.Entity;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.IntegerAttributeSensor;
import brooklyn.event.basic.BasicNotificationSensor;

/**
 * A collection of entities that can change.
 */
public interface Changeable {
    BasicAttributeSensor<Integer> GROUP_SIZE = new IntegerAttributeSensor("group.members.count", "Number of members");
    BasicNotificationSensor<Entity> MEMBER_ADDED = new BasicNotificationSensor<Entity>(Entity.class, "group.members.added", "Entity added to group members");
    BasicNotificationSensor<Entity> MEMBER_REMOVED = new BasicNotificationSensor<Entity>(Entity.class, "group.members.removed", "Entity removed from group members");
}
