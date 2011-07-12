package brooklyn.management;

import java.util.Collection;
import java.util.concurrent.Executor;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;

/**
 * This is the entry point for accessing and interacting with a realm of applications and their entities in Brooklyn.
 *
 * For example, policies and the management console(s) (web-app, etc) can use this to interact with entities; 
 * policies, web-app, and entities share the realm for subscribing to events, executing tasks, and generally co-existing.      
 * <p>
 * It may refer to several applications, and it refers to all the entities owned by those applications.
 */
public interface ManagementContext {
    /**
     * The applications associated with this management realm
     */
    Collection<Application> getApplications();

    //try removing this, as it is tedious to implement always (e.g. local case, where it was returning null anyway);
    //and not sure it is desirable to treat an entity by its ID only (when we can just as easily keep a proxy Entity instance)
//    /**
//     * Returns the entity with the given identifier (may be a full instance, or a proxy to one which is remote)
//     */
//    Entity getEntity(String id);
    
    /**
     * Returns the {@link ExecutionManager} instance for entities and users in this management realm 
     * to submit tasks and to observe what tasks are occurring
     */
    ExecutionManager getExecutionManager();
    
    /**
     * Returns the {@link SubscriptionManager} instance for entities and users of this management realm
     * to subscribe to sensor events (and, in the case of entities, to emit sensor events) 
     */
    SubscriptionManager getSubscriptionManager();
 
    ExecutionContext getExecutionContext(Entity entity);
 
    SubscriptionContext getSubscriptionContext(Entity entity);
}
