package brooklyn.web.console.entity;

import brooklyn.entity.Entity
import brooklyn.entity.EntityClass
import brooklyn.entity.Group

/** Summary of a Brookln Entity   */
public class EntitySummary {

    final String id;
    final EntityClass entityClass;
    final String displayName;
    final String applicationId;
    final String ownerId;
    final Collection<LocationSummary> locations;
    final Collection<String> children;
    final Collection<String> groupNames;

    public EntitySummary(Entity entity) {
        this.id = entity.getId();
        this.entityClass = entity.entityClass;
        this.displayName = entity.displayName;
        this.applicationId = entity.application?.getId();
        this.ownerId = entity.owner ? entity.owner.id : null;
        this.locations = entity.getLocations().collect { new LocationSummary(it) };
        this.groupNames = entity.getGroups().collect { it.getDisplayName() }
        if (entity instanceof Group) {
            this.children = ((Group) entity).members.collect { it.id };
        }
    }
}
