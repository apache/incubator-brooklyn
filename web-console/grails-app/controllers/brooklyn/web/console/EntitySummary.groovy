package brooklyn.web.console;

import java.util.Collection

import brooklyn.entity.Entity
import brooklyn.entity.EntityClass


public class EntitySummary {

    final String id;
    final EntityClass entityClass;
    final String displayName;
    
    final String applicationId;
    final String ownerId;
    final Collection<String> groupIds;

    public EntitySummary(Entity entity) {
        this.id = entity.getId();
        this.entityClass = entity.entityClass;
        this.displayName = entity.displayName;
        this.applicationId = entity.application?.getId();
        this.ownerId = entity.owner ? entity.owner.id : null;
        this.groupIds = entity.groups.collect { it.id };
    }
    
    public EntitySummary(String id, EntityClass entityClass, String displayName, String applicationId, String ownerId, Collection<String> groupIds) {
        this.id = id;
        this.entityClass = entityClass;
        this.displayName = displayName;
        this.applicationId = applicationId;
        this.ownerId = ownerId;
        this.groupIds = groupIds;
    }
}
