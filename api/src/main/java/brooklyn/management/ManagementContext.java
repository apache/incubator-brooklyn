package brooklyn.management;

import java.util.Collection;

import brooklyn.config.ConfigMap.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.drivers.EntityDriverFactory;

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
     * All applications under control of this management plane
     */
    Collection<Application> getApplications();
    /**
     * All entities under control of this management plane
     */
    Collection<Entity> getEntities();

    /**
     * Returns the entity with the given identifier (may be a full instance, or a proxy to one which is remote)
     */
    Entity getEntity(String id);
    
    /** whether the entity is under management by this management context */
    boolean isManaged(Entity entity);

    /**
     * Returns the {@link ExecutionManager} instance for entities and users in this management realm 
     * to submit tasks and to observe what tasks are occurring
     */
    ExecutionManager getExecutionManager();

    /**
     * Returns the {@link EntityDriverFactory} entities can use to create drivers.
     *
      * @return the EntityDriverFactory to use.
     */
    EntityDriverFactory getEntityDriverFactory();

    /**
     * Returns the {@link SubscriptionManager} instance for entities and users of this management realm
     * to subscribe to sensor events (and, in the case of entities, to emit sensor events) 
     */
    SubscriptionManager getSubscriptionManager();

    //TODO (Alex) I'm not sure the following two getXxxContext methods are needed on the interface;
    //I expect they will only be called once, in AbstractEntity, and fully capable
    //there of generating the respective contexts from the managers
    //(Litmus test will be whether there is anything in FederatedManagementContext
    //which requires a custom FederatedExecutionContext -- or whether BasicEC 
    //works with FederatedExecutionManager)
    /**
     * Returns an {@link ExecutionContext} instance representing tasks 
     * (from the {@link ExecutionManager}) associated with this entity, and capable 
     * of conveniently running such tasks which will be associated with that entity  
     */
    ExecutionContext getExecutionContext(Entity entity);
    
    /**
     * Returns a {@link SubscriptionContext} instance representing subscriptions
     * (from the {@link SubscriptionManager}) associated with this entity, and capable 
     * of conveniently subscribing on behalf of that entity  
     */
    SubscriptionContext getSubscriptionContext(Entity entity);

    /**
     * Manage an entity.
     */
    void manage(Entity e);
    
    /**
     * Unmanage an entity.
     */
    void unmanage(Entity e);
    
    /**
     * Returns the ConfigMap (e.g. BrooklynProperties) applicable to this management context.
     * Defaults to reading ~/.brooklyn/brooklyn.properties but configurable in the management context.
     */
    StringConfigMap getConfig();
}
