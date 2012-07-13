package brooklyn.web.console.entity;

import brooklyn.entity.Entity
import brooklyn.entity.EntityType
import brooklyn.entity.Group
import brooklyn.entity.basic.Attributes
import brooklyn.entity.trait.Startable

/** Summary of a Brookln Entity   */
public class EntitySummary {

    final String id;
    final EntityType entityType;
    final String displayName;
    final String applicationId;
    final String ownerId;
    final String status;
    final Collection<LocationSummary> locations;
    final Collection<String> children;
    final Collection<String> groupNames;

    public EntitySummary(Entity entity) {
        this.id = entity.getId();
        this.entityType = entity.entityType;
        this.displayName = entity.displayName;
        this.applicationId = entity.application?.getId();
        this.ownerId = entity.owner ? entity.owner.id : null;
        this.locations = entity.getLocations().collect { new LocationSummary(it) };
        this.groupNames = entity.getGroups().collect { it.getDisplayName() }
        if (entity instanceof Group) {
            this.children = ((Group) entity).members.collect { it.id };
        } else {
            this.children = null;
        }
        this.status = deriveStatus(entity);
    }
    
    private String deriveStatus(Entity entity) {
        // a simple status check. more sophisticated would be nice.
        
        Object result = entity.getAttribute(Attributes.SERVICE_STATE);
        if (result!=null) 
            return result.toString().toUpperCase();
            
        result = entity.getAttribute(Startable.SERVICE_UP);
        if (result!=null) {
            if (result) return "UP";
            else return "DOWN";
        }
        
        return "(no status)";
    }
}
