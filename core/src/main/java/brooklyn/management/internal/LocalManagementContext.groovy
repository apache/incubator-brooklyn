package brooklyn.management.internal;

import java.util.Collection
import java.util.Set

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.collect.Sets;

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
    private static final Logger log = LoggerFactory.getLogger(LocalManagementContext.class);

    private ExecutionManager execution;
    private SubscriptionManager subscriptions;

    protected Map<String,Entity> entitiesById = new LinkedHashMap<String,Entity>();
    protected ObservableList entities = new ObservableList();
    protected Set<Application> applications = Sets.newLinkedHashSet();

    private static final Object MANAGED_LOCALLY = new Object();
    
    protected synchronized boolean manageNonRecursive(Entity e) {
        ((AbstractEntity)e).managementData = MANAGED_LOCALLY;
        Object old = entitiesById.put(e.getId(), e);
        if (old!=null) {
            if (old.is(e))
                log.warn("call to manage entity {} but it is already managed (known at {})", e, this);
            else
                throw new IllegalStateException("call to manage entity {} but different entity {} already known under that id at {}", e, old, this);
            return false;
        } else {
            entities.add(e);
            if (e instanceof Application) applications.add(e);
            return true;
        }
    }

    protected synchronized boolean unmanageNonRecursive(Entity e) {
        ((AbstractEntity)e).managementData = null;
        e.clearOwner();
        if (e in Application) applications.remove(e);
        entities.remove(e);
        Object old = entitiesById.remove(e.getId());
        if (old!=e) {
            log.warn("call to unmanage entity {} but it is not known at {}", e, this);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public synchronized Collection<Application> getApplications() {
        return new ArrayList<Application>(applications);
    }
    public synchronized Collection<Entity> getEntities() {
        return new ArrayList<Entity>(entitiesById.values());
    }
    
    public Entity getEntity(String id) {
        entitiesById.get(id);
	}
    
    public synchronized  SubscriptionManager getSubscriptionManager() {
        if (subscriptions) return subscriptions;
        subscriptions = new LocalSubscriptionManager(executionManager);
    }

    public synchronized ExecutionManager getExecutionManager() {
        if (execution) return execution;
        execution = new BasicExecutionManager();
    }
    	
    public <T> Task<T> runAtEntity(Map flags, Entity entity, Runnable c) {
		manageIfNecessary(entity, (flags.displayName?:flags.description?:flags?:c));
        entity.executionContext.submit(flags, c);
    }

    public boolean isManagedLocally(Entity e) {
        return true;
    }

    public void addEntitySetListener(CollectionChangeListener<Entity> listener) {
        entities.addPropertyChangeListener(new GroovyObservablesPropertyChangeToCollectionChangeAdapter(listener));
    }

    public void removeEntitySetListener(CollectionChangeListener<Entity> listener) {
        entities.removePropertyChangeListener(new GroovyObservablesPropertyChangeToCollectionChangeAdapter(listener));
    }
}
