package brooklyn.management.internal;

import java.util.Collection
import java.util.Set

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.management.ExecutionManager
import brooklyn.management.SubscriptionManager
import brooklyn.management.Task
import brooklyn.util.task.BasicExecutionManager

/**
 * A local implementation of the {@link ManagementContext} API.
 */
public class LocalManagementContext extends AbstractManagementContext {
    private static final Logger log = LoggerFactory.getLogger(LocalManagementContext.class)

    private ExecutionManager execution
    private SubscriptionManager subscriptions

    protected Map<String,Entity> entitiesById = new LinkedHashMap<String,Entity>()
    protected ObservableList entities = new ObservableList()
    protected Set<Application> applications = []

    private static final Object MANAGED_LOCALLY = new Object();
    
    protected synchronized boolean manageNonRecursive(Entity e) {
        ((AbstractEntity)e).managementData = MANAGED_LOCALLY;
        Object old = entitiesById.put(e.getId(), e);
        if (old!=null) {
            if (old.is(e))
                log.warn("call to manage entity $e but it is already managed (known at $this)")
            else
                throw new IllegalStateException("call to manage entity $e but different entity $old already known under that id at $this")
            return false
        } else {
            entities.add(e)
            if (e instanceof Application) applications << e
            return true
        }
    }

    protected synchronized boolean unmanageNonRecursive(Entity e) {
        ((AbstractEntity)e).managementData = null;
        if (e in Application) applications.remove(e)
        entities.remove(e)
        Object old = entitiesById.remove(e.getId());
        if (old!=e) {
            log.warn("call to unmanage entity $e but it is not known at $this")
            return false
        } else {
            return true
        }
    }

    @Override
    public Collection<Application> getApplications() {
        return applications
    }
    public Collection<Entity> getEntities() {
        return entitiesById.values();
    }
    
    public Entity getEntity(String id) {
        entitiesById.get(id)
	}
    
    public synchronized  SubscriptionManager getSubscriptionManager() {
        if (subscriptions) return subscriptions
        subscriptions = new LocalSubscriptionManager(executionManager)
    }

    public synchronized ExecutionManager getExecutionManager() {
        if (execution) return execution
        execution = new BasicExecutionManager()
    }
    
    public <T> Task<T> runAtEntity(Map flags, Entity entity, Runnable c) {
        if (!isManaged(entity)) {
            Entity rootUnmanaged = entity;
            while (true) {
                Entity candidateUnmanagedOwner = rootUnmanaged.getOwner();
                if (candidateUnmanagedOwner==null || getEntity(candidateUnmanagedOwner.id)!=null)
                    break;
                rootUnmanaged = candidateUnmanagedOwner;
            }
            log.warn("Activating management for $rootUnmanaged due to running code on $entity: "+flags+" - "+c.toString())
            manage(rootUnmanaged)
        }
        entity.executionContext.submit(flags, c);
    }

    public boolean isManagedLocally(Entity e) {
        return true;
    }

    public void addEntitySetListener(CollectionChangeListener<Entity> listener) {
        entities.addPropertyChangeListener(new GroovyObservablesPropertyChangeToCollectionChangeAdapter(listener))
    }

    public void removeEntitySetListener(CollectionChangeListener<Entity> listener) {
        entities.removePropertyChangeListener(new GroovyObservablesPropertyChangeToCollectionChangeAdapter(listener))
    }
}
