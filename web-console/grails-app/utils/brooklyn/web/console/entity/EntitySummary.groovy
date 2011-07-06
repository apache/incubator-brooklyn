package brooklyn.web.console.entity;

import brooklyn.entity.Entity
import brooklyn.entity.EntityClass
import brooklyn.entity.Group
import brooklyn.entity.Effector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor

/** Summary of a Brookln Entity   */
public class EntitySummary {

    final String id;
    final EntityClass entityClass;
    final String displayName;
    final String applicationId;
    final String ownerId;
    final Collection<String> children;
    final Map<String, SensorSummary> sensors = [:];
    final Map<String, Effector> effectors = [:];

    public EntitySummary(Entity entity) {
        this.id = entity.getId();
        this.entityClass = entity.entityClass;
        this.displayName = entity.displayName;
        this.applicationId = entity.application?.getId();
        this.ownerId = entity.owner ? entity.owner.id : null;
        if (entity instanceof Group) {
            this.children = ((Group) entity).members.collect { it.id };
        }

        if (entity.entityClass) {
            for (Sensor sensor: entity.entityClass.sensors) {
                if (sensor instanceof AttributeSensor) {
                    this.sensors[sensor.name] = new SensorSummary(sensor, entity.getAttribute(sensor))
                }
            }
            for (Effector effector: entity.entityClass.effectors) {
                this.effectors[effector.name] = effector
            }
        }
    }
}
