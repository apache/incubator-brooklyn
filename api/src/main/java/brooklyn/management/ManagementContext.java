package brooklyn.management;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.EntitySummary;

/**
 * This is the entry point for accessing and interacting with OverPaas. 
 * 
 * For example, policies and the web-app can use this to retrieve the desired entities.  
 * 
 * It may refer to several applications, and to all the entities within those applications.
 * 
 * @author aled
 */
public interface ManagementContext {

    /**
     * The applications known about in this OverPaas context.
     */
    Collection<EntitySummary> getApplicationSummaries();

    /**
     * All the entities associated with this application (i.e. the entire graph of entitities involved in this app). 
     */
    Collection<EntitySummary> getEntitySummariesInApplication(String id);

    /**
     * All entities known about in this OverPaas context.
     */
    Collection<EntitySummary> getAllEntitySummaries();

    /**
     * @return The entity with the given identifier (may be a full instance, or a proxy to one which is remote)
     */
    Entity getEntity(String id);

    
//    Execution
}
