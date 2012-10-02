package brooklyn.management.internal;

import static brooklyn.util.GroovyJavaMethods.elvis;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.drivers.BasicEntityDriverFactory;
import brooklyn.entity.drivers.EntityDriverFactory;
import groovy.util.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.ExecutionManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionManager;
import brooklyn.management.Task;
import brooklyn.util.task.BasicExecutionManager;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A local implementation of the {@link ManagementContext} API.
 */
public class LocalManagementContext extends AbstractManagementContext {
    private static final Logger log = LoggerFactory.getLogger(LocalManagementContext.class);

    private ExecutionManager execution;
    private SubscriptionManager subscriptions;

    protected Map<String,Entity> entitiesById = Maps.newLinkedHashMap();
    protected ObservableList entities = new ObservableList();
    protected Set<Application> applications = Sets.newLinkedHashSet();

    private static final Object MANAGED_LOCALLY = new Object();

    public LocalManagementContext(BrooklynProperties brooklynProperties){
       super(brooklynProperties);
    }

    protected synchronized boolean manageNonRecursive(Entity e) {
        ((AbstractEntity)e).managementData = MANAGED_LOCALLY;
        Object old = entitiesById.put(e.getId(), e);
        if (old!=null) {
            if (old == e) {
                log.warn("{} redundant call to start management of entity {}", this, e);
            } else {
                throw new IllegalStateException("call to manage entity "+e+" but different entity "+old+" already known under that id at "+this);
            }
            return false;
        } else {
            log.debug("{} starting management of entity {}", this, e);
            if (e instanceof Application) {
                applications.add((Application)e);
                ((AbstractApplication)e).setManagementContext(this);
            }
            entities.add(e);
            return true;
        }
    }

    protected synchronized boolean unmanageNonRecursive(Entity e) {
        ((AbstractEntity)e).managementData = null;
        e.clearOwner();
        if (e instanceof Application) applications.remove(e);
        entities.remove(e);
        Object old = entitiesById.remove(e.getId());
        if (old==null) {
            log.warn("{} call to stop management of unknown entity (already unmanaged?) {}", this, e);
            return false;
        } else if (!old.equals(e)) {
            // shouldn't happen...
            log.error("{} call to stop management of entity {} removed different entity {}", new Object[] { this, e, old });
            return true;
        } else {
            log.debug("{} stopped management of entity {}", this, e);
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
        return entitiesById.get(id);
	}
    
    public synchronized  SubscriptionManager getSubscriptionManager() {
        if (subscriptions == null) {
            subscriptions = new LocalSubscriptionManager(getExecutionManager());
        }
        return subscriptions;
    }

    public synchronized ExecutionManager getExecutionManager() {
        if (execution == null) {
            execution = new BasicExecutionManager();
        }
        return execution;
    }
    	
    public <T> Task<T> runAtEntity(Map flags, Entity entity, Callable<T> c) {
		manageIfNecessary(entity, (elvis(flags.get("displayName"), flags.get("description"), flags, c)));
        return ((EntityLocal)entity).getExecutionContext().submit(flags, c);
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
