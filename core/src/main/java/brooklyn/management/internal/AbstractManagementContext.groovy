package brooklyn.management.internal;

import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import java.util.Map

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEffector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.management.ExecutionContext
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.Task
import brooklyn.util.task.BasicExecutionContext
import brooklyn.util.task.BasicExecutionManager

public abstract class AbstractManagementContext implements ManagementContext {
    
    public ExecutionContext getExecutionContext(Entity e) { 
        new BasicExecutionContext(tag:e, getExecutionManager());
    }
    
    public SubscriptionContext getSubscriptionContext(Entity e) {
        new BasicSubscriptionContext(getSubscriptionManager(), e);
    }
        
    public boolean isManaged(Entity e) {
        return (getEntity(e.id)!=null)
    }

    /** begins management for the given entity and its children, recursively
    * <p>
    * depending on the implementation of the management context,
    * this might push it out to one or more remote management nodes */
    public void manage(Entity e) {
        if (manageNonRecursive(e))
            ((AbstractEntity)e).onManagementBecomingMaster()
        for (Entity ei : e.getOwnedChildren())
            manage(ei);
    }
    /** should ensure that the entity is now managed somewhere, and known about in all the ists */
    protected abstract boolean manageNonRecursive(Entity e);
   /** causes the given entity and its children, recursively, to be removed from the management plane
    * (for instance because the entity is no longer relevant) */
    public void unmanage(Entity e) {
        for (Entity ei : e.getOwnedChildren())
            unmanage(ei);
        if (unmanageNonRecursive(e))
            ((AbstractEntity)e).onManagementNoLongerMaster()
    }
    /** should ensure that the entity is no longer managed anywhere, remove from all lists */
    protected abstract boolean unmanageNonRecursive(Entity e);
    
    public <T> Task<T> invokeEffector(Entity entity, Effector<T> eff, Map parameters) {
        runAtEntity(entity, { eff.call(entity, parameters); },
           description:"invoking ${eff.name} on ${entity}" )
    }
    protected <T> T invokeEffectorMethodLocal(Entity entity, Effector<T> eff, Object args) {
        assert isManagedLocally(entity) : "cannot invoke effector method at $this because it is not managed here"
        args = AbstractEffector.prepareArgsForEffector(eff, args);
        entity.metaClass.invokeMethod(entity, eff.name, args)
    }
    /** method for entity to make effector happen with correct semantics (right place, right task context),
     * when a method is called on that entity */
    protected <T> T invokeEffectorMethodSync(Entity entity, Effector<T> eff, Object args) {
        Task current = BasicExecutionManager.currentTask
        if (!current || !current.tags.contains(entity) || !isManagedLocally(entity)) {
            // Wrap in a task if we aren't already in a task that is tagged with this entity
            runAtEntity(entity, { invokeEffectorMethodLocal(entity, eff, args); },
                description:"invoking ${eff.name} on ${entity}" ).
            get()
        } else {
            return invokeEffectorMethodLocal(entity, eff, args)
        }
    }

    /** whether the master entity record is local, and sensors and effectors can be properly accessed locally */ 
    public abstract boolean isManagedLocally(Entity e);
    
    /** causes the indicated runnable to be run at the right location for the given entity,
     * returning the actual task (if it is local) or a proxy task (if it is remote);
     * if management for the entity has not yet started this may start it */
    public abstract <T> Task<T> runAtEntity(Map flags, Entity entity, Runnable c);

    public abstract void addEntitySetListener(CollectionChangeListener<Entity> listener);
    public abstract void removeEntitySetListener(CollectionChangeListener<Entity> listener);

}
