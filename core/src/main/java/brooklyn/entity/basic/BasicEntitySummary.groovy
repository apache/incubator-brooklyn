package brooklyn.entity.basic

import java.util.Collection

import brooklyn.entity.EntityClass
import brooklyn.entity.EntitySummary

public class BasicEntitySummary implements EntitySummary {
    
    final String id;
    final EntityClass entityClass;
    final String displayName;
	
    final String applicationId;
    final String parentId;
    final Collection<String> groupIds;

	public BasicEntitySummary(EntitySummary summary) {
        this.id = summary.id;
		this.entityClass = summary.entityClass;
        this.displayName = summary.displayName;
        this.applicationId = summary.applicationId;
        this.parentId = summary.parentId;
        this.groupIds = summary.groupIds;
	}
    public BasicEntitySummary(String id, EntityClass entityClass, String displayName, String applicationId, String parentId, Collection<String> groupIds) {
        this.id = id;
		this.entityClass = entityClass;
        this.displayName = displayName;
        this.applicationId = applicationId;
        this.parentId = parentId;
        this.groupIds = groupIds;
    }
}
