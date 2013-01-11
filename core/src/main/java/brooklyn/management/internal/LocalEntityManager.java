package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import groovy.util.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Startable;
import brooklyn.management.EntityManager;
import brooklyn.management.internal.ManagementTransitionInfo.ManagementTransitionMode;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class LocalEntityManager implements EntityManager {

    private static final Logger log = LoggerFactory.getLogger(LocalEntityManager.class);

    private final LocalManagementContext managementContext;
    
    protected final Map<String,Entity> preManagedEntitiesById = new WeakHashMap<String, Entity>();
    protected final Map<String,Entity> entitiesById = Maps.newLinkedHashMap();
    protected final ObservableList entities = new ObservableList();
    protected final Set<Application> applications = Sets.newLinkedHashSet();

    public LocalEntityManager(LocalManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public synchronized Collection<Entity> getEntities() {
        return new ArrayList<Entity>(entitiesById.values());
    }
    
    @Override
    public Entity getEntity(String id) {
        return entitiesById.get(id);
    }
    
    synchronized Collection<Application> getApplications() {
        return ImmutableList.copyOf(applications);
    }
    
    @Override
    public boolean isManaged(Entity e) {
        return (isRunning() && getEntity(e.getId()) != null);
    }
    
    @Override
    public void manage(Entity e) {
        if (isManaged(e)) {
//            if (log.isDebugEnabled()) {
                log.warn(""+this+" redundant call to start management of entity (and descendants of) "+e+"; skipping", 
                    new Throwable("source of duplicate management of "+e));
//            }
            return;
        }
        
        final ManagementTransitionInfo info = new ManagementTransitionInfo(managementContext, ManagementTransitionMode.NORMAL);
        recursively(e, new Predicate<EntityLocal>() { public boolean apply(EntityLocal it) {
            preManageNonRecursive(it);
            it.getManagementSupport().onManagementStarting(info); 
            return manageNonRecursive(it);
        } });
        
        recursively(e, new Predicate<EntityLocal>() { public boolean apply(EntityLocal it) {
            it.getManagementSupport().onManagementStarted(info);
            managementContext.getRebindManager().getChangeListener().onManaged(it);
            return true; 
        } });
    }
    
    @Override
    public void unmanage(Entity e) {
        if (shouldSkipUnmanagement(e)) return;
        
        final ManagementTransitionInfo info = new ManagementTransitionInfo(managementContext, ManagementTransitionMode.NORMAL);
        recursively(e, new Predicate<EntityLocal>() { public boolean apply(EntityLocal it) {
            if (shouldSkipUnmanagement(it)) return false;
            it.getManagementSupport().onManagementStopping(info); 
            return true;
        } });
        
        recursively(e, new Predicate<EntityLocal>() { public boolean apply(EntityLocal it) {
            if (shouldSkipUnmanagement(it)) return false;
            boolean result = unmanageNonRecursive(it);            
            it.getManagementSupport().onManagementStopped(info);
            managementContext.getRebindManager().getChangeListener().onUnmanaged(it);
            if (managementContext.gc != null) managementContext.gc.onUnmanaged(it);
            return result; 
        } });
    }
    
    /**
     * activates management when effector invoked, warning unless context is acceptable
     * (currently only acceptable context is "start")
     */
    void manageIfNecessary(Entity entity, Object context) {
        if (!isRunning()) {
            return; // TODO Still a race for terminate being called, and then isManaged below returning false
        } else if (((EntityLocal)entity).getManagementSupport().wasDeployed()) {
            return;
        } else if (isManaged(entity)) {
            return;
        } else if (isPreManaged(entity)) {
            return;
        } else {
            Entity rootUnmanaged = entity;
            while (true) {
                Entity candidateUnmanagedParent = rootUnmanaged.getParent();
                if (candidateUnmanagedParent == null || isManaged(candidateUnmanagedParent) || isPreManaged(candidateUnmanagedParent))
                    break;
                rootUnmanaged = candidateUnmanagedParent;
            }
            if (context == Startable.START.getName())
                log.info("Activating local management for {} on start", rootUnmanaged);
            else
                log.warn("Activating local management for {} due to effector invocation on {}: {}", new Object[]{rootUnmanaged, entity, context});
            manage(rootUnmanaged);
        }
    }

    private void recursively(Entity e, Predicate<EntityLocal> action) {
        action.apply( (EntityLocal)e );
        for (Entity child : e.getChildren()) {
            recursively(child, action);
        }
    }

    /**
     * Whether the entity is in the process of being managed.
     */
    private synchronized boolean isPreManaged(Entity e) {
        return preManagedEntitiesById.containsKey(e.getId());
    }

    /**
     * Should ensure that the entity is now known about, but should not be accessible from other entities yet.
     * 
     * Records that the given entity is about to be managed (used for answering {@link isPreManaged(Entity)}.
     * Note that refs to the given entity are stored in a a weak hashmap so if the subsequent management
     * attempt fails then this reference to the entity will eventually be discarded (if no-one else holds 
     * a reference).
     */
    private synchronized boolean preManageNonRecursive(Entity e) {
        Object old = preManagedEntitiesById.put(e.getId(), e);
        if (old!=null) {
            if (old == e) {
                log.warn("{} redundant call to pre-start management of entity {}", this, e);
            } else {
                throw new IllegalStateException("call to pre-manage entity "+e+" but different entity "+old+" already known under that id at "+this);
            }
            return false;
        } else {
            if (log.isTraceEnabled()) log.trace("{} pre-start management of entity {}", this, e);
            return true;
        }
    }

    /**
     * Should ensure that the entity is now managed somewhere, and known about in all the lists.
     * Returns true if the entity has now become managed; false if it was already managed (anything else throws exception)
     */
    private synchronized boolean manageNonRecursive(Entity e) {
        Object old = entitiesById.put(e.getId(), e);
        if (old!=null) {
            if (old == e) {
                log.warn("{} redundant call to start management of entity {}", this, e);
            } else {
                throw new IllegalStateException("call to manage entity "+e+" but different entity "+old+" already known under that id at "+this);
            }
            return false;
        } else {
            if (log.isDebugEnabled()) log.debug("{} starting management of entity {}", this, e);
            preManagedEntitiesById.remove(e.getId());
            if (e instanceof Application) {
                applications.add((Application)e);
            }
            entities.add(e);
            return true;
        }
    }

    /**
     * Should ensure that the entity is no longer managed anywhere, remove from all lists.
     * Returns true if the entity has been removed from management; if it was not previously managed (anything else throws exception) 
     */
    private synchronized boolean unmanageNonRecursive(Entity e) {
        e.clearParent();
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
            if (log.isDebugEnabled()) log.debug("{} stopped management of entity {}", this, e);
            return true;
        }
    }

    void addEntitySetListener(CollectionChangeListener<Entity> listener) {
        //must notify listener in a different thread to avoid deadlock (issue #378)
        AsyncCollectionChangeAdapter<Entity> wrappedListener = new AsyncCollectionChangeAdapter<Entity>(managementContext.getExecutionManager(), listener);
        entities.addPropertyChangeListener(new GroovyObservablesPropertyChangeToCollectionChangeAdapter(wrappedListener));
    }

    void removeEntitySetListener(CollectionChangeListener<Entity> listener) {
        AsyncCollectionChangeAdapter<Entity> wrappedListener = new AsyncCollectionChangeAdapter<Entity>(managementContext.getExecutionManager(), listener);
        entities.removePropertyChangeListener(new GroovyObservablesPropertyChangeToCollectionChangeAdapter(wrappedListener));
    }
    
    private boolean shouldSkipUnmanagement(Entity e) {
        if (e==null) {
            log.warn(""+this+" call to unmanage null entity; skipping",  
                new IllegalStateException("source of null unmanagement call to "+this));
            return true;
        }
        if (!isManaged(e)) {
            log.warn("{} call to stop management of unknown entity (already unmanaged?) {}; skipping, and all descendants", this, e);
            return true;
        }
        return false;
    }
    
    private boolean isRunning() {
        return managementContext.isRunning();
    }
}
