package brooklyn.management;

import java.util.Collection;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;

/**
 * This is the entry point for accessing and interacting with Brooklyn.
 * 
 * For example, policies and the web-app can use this to retrieve the desired entities.  
 * <p>
 * It may refer to several applications, and to all the entities within those applications.
 * 
 * @author aled
 */
public interface ManagementContext {
    /**
     * The applications known about in this Brooklyn context.
     */
    Collection<Application> getApplications();

    /**
     * Returns the entity with the given identifier (may be a full instance, or a proxy to one which is remote)
     */
    Entity getEntity(String id);
    
    /**
     * Returns the current {@link ExecutionManager} instance.
     */
    ExecutionManager getExecutionManager();
}
