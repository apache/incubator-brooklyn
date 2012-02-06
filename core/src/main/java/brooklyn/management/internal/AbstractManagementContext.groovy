package brooklyn.management.internal;

import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.management.Task;
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEffector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.management.ExecutionContext
import brooklyn.management.ExpirationPolicy
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.Task
import brooklyn.util.task.BasicExecutionContext
import brooklyn.util.task.BasicExecutionManager

public abstract class AbstractManagementContext implements ManagementContext  {
    private static final Logger log = LoggerFactory.getLogger(AbstractManagementContext.class)
    public static final EFFECTOR_TAG = "EFFECTOR"

    public ExecutionContext getExecutionContext(Entity e) { 
        new BasicExecutionContext(tag:e, getExecutionManager());
    }
    
    public SubscriptionContext getSubscriptionContext(Entity e) {
        new BasicSubscriptionContext(getSubscriptionManager(), e);
    }
        
    public boolean isManaged(Entity e) {
        return (getEntity(e.id)!=null)
    }

    /**
     * Begins management for the given entity and its children, recursively.
     *
     * depending on the implementation of the management context,
     * this might push it out to one or more remote management nodes.
     */
    public void manage(Entity e) {
        if (isManaged(e)) {
            log.warn("call to manage entity $e but it is already managed (known at $this); skipping, and all descendants")
            new Throwable("source of duplicate management").printStackTrace()
            return
        }
        if (manageNonRecursive(e)) {
            ((AbstractEntity)e).onManagementBecomingMaster()
            ((AbstractEntity)e).setBeingManaged()
        }
        for (Entity ei : e.getOwnedChildren()) {
            manage(ei);
        }
    }

    /**
     * Implementor-supplied internal method.
     * <p>
     * Should ensure that the entity is now managed somewhere, and known about in all the lists.
     * Returns true if the entity has now become managed; false if it was already managed (anything else throws exception)
     */
    protected abstract boolean manageNonRecursive(Entity e);

    /**
     * Causes the given entity and its children, recursively, to be removed from the management plane
     * (for instance because the entity is no longer relevant)
     */
    public void unmanage(Entity e) {
        if (!isManaged(e)) {
            log.warn("call to unmanage entity $e but it is not known at $this; skipping, and all descendants")
            return
        }
        for (Entity ei : e.getOwnedChildren()) {
            unmanage(ei);
        }
        if (unmanageNonRecursive(e))
            ((AbstractEntity)e).onManagementNoLongerMaster()
    }

    /**
     * Implementor-supplied internal method.
     * <p>
     * Should ensure that the entity is no longer managed anywhere, remove from all lists.
     * Returns true if the entity has been removed from management; if it was not previously managed (anything else throws exception) 
     */
    protected abstract boolean unmanageNonRecursive(Entity e);

    public <T> Task<T> invokeEffector(Entity entity, Effector<T> eff, Map parameters) {
        runAtEntity(expirationPolicy: ExpirationPolicy.NEVER, entity, { eff.call(entity, parameters); },
           description:"invoking ${eff.name} on ${entity.displayName}", displayName:eff.name, tags:[EFFECTOR_TAG])
    }

    protected <T> T invokeEffectorMethodLocal(Entity entity, Effector<T> eff, Object args) {
        assert isManagedLocally(entity) : "cannot invoke effector method at $this because it is not managed here"
        args = AbstractEffector.prepareArgsForEffector(eff, args);
        entity.metaClass.invokeMethod(entity, eff.name, args)
    }

	/** activates management when effector invoked, warning unless context is acceptable
	 * (currently only acceptable context is "start") */
	protected void manageIfNecessary(Entity entity, Object context) {
        if (((AbstractEntity)entity).hasEverBeenManaged()) {
            return
        } else if (!isManaged(entity)) {
			Entity rootUnmanaged = entity;
			while (true) {
				Entity candidateUnmanagedOwner = rootUnmanaged.getOwner();
				if (candidateUnmanagedOwner==null || getEntity(candidateUnmanagedOwner.id)!=null)
					break;
				rootUnmanaged = candidateUnmanagedOwner;
			}
			if (context==Startable.START.name)
				log.info("activating local management for $rootUnmanaged on start")
			else
				log.warn("activating local management for $rootUnmanaged due to running code on $entity: "+context)
			manage(rootUnmanaged)
		}
	}

    /**
     * Method for entity to make effector happen with correct semantics (right place, right task context),
     * when a method is called on that entity.
     */
    protected <T> T invokeEffectorMethodSync(Entity entity, Effector<T> eff, Object args) {
        try {
            Task current = BasicExecutionManager.currentTask
            if (!current || !current.tags.contains(entity) || !isManagedLocally(entity)) {
    			manageIfNecessary(entity, eff.name);
                // Wrap in a task if we aren't already in a task that is tagged with this entity
                runAtEntity(expirationPolicy: ExpirationPolicy.NEVER, entity, { invokeEffectorMethodLocal(entity, eff, args); },
                    description:"invoking ${eff.name} on ${entity.displayName}", displayName:eff.name, tags:[EFFECTOR_TAG]).get()
            } else {
                return invokeEffectorMethodLocal(entity, eff, args)
            }
        } catch (Exception e) {
            throw new RuntimeException("Error invoking $eff on entity $entity", e);
        }
    }

    /**
     * Whether the master entity record is local, and sensors and effectors can be properly accessed locally.
     */ 
    public abstract boolean isManagedLocally(Entity e);
    
    /**
     * Causes the indicated runnable to be run at the right location for the given entity.
     *
     * Returns the actual task (if it is local) or a proxy task (if it is remote);
     * if management for the entity has not yet started this may start it.
     */
    public abstract <T> Task<T> runAtEntity(Map flags, Entity entity, Runnable c);

    public abstract void addEntitySetListener(CollectionChangeListener<Entity> listener);

    public abstract void removeEntitySetListener(CollectionChangeListener<Entity> listener);
}
