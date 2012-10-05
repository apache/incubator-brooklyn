package brooklyn.management.internal;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEffector;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EffectorUtils;
import brooklyn.entity.basic.EntityReferences.EntityCollectionReference;
import brooklyn.entity.drivers.BasicEntityDriverFactory;
import brooklyn.entity.drivers.EntityDriverFactory;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ExpirationPolicy;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementTransitionInfo.ManagementTransitionMode;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.Tasks;

import com.google.common.base.Predicate;

public abstract class AbstractManagementContext implements ManagementContext  {
    private static final Logger log = LoggerFactory.getLogger(AbstractManagementContext.class);
    public static final String EFFECTOR_TAG = "EFFECTOR";

    private final AtomicLong totalEffectorInvocationCount = new AtomicLong();

<<<<<<< HEAD
    protected BrooklynProperties configMap;

    // TODO leaking "this" reference; yuck
    private final RebindManager rebindManager = new RebindManagerImpl(this);

    public AbstractManagementContext(BrooklynProperties brooklynProperties){
       this.configMap = brooklynProperties;
    }
    
    public void terminate() {
        rebindManager.stop();
        for (Application app : getApplications()) {
            unmanage(app);
        }
    }
    
    @Override
    public RebindManager getRebindManager() {
        return rebindManager;
    }

    public long getTotalEffectorInvocations() {
        return totalEffectorInvocationCount.get();
    }
    
    public ExecutionContext getExecutionContext(Entity e) { 
        return new BasicExecutionContext(MutableMap.of("tag", e), getExecutionManager());
    }
    
    public SubscriptionContext getSubscriptionContext(Entity e) {
        return new BasicSubscriptionContext(getSubscriptionManager(), e);
    }

    private final EntityDriverFactory entityDriverFactory = new BasicEntityDriverFactory();

    @Override
    public EntityDriverFactory getEntityDriverFactory() {
        return entityDriverFactory;
    }
    
    public boolean isManaged(Entity e) {
        return (getEntity(e.getId())!=null);
    }
    
    /**
     * Begins management for the given entity and its children, recursively.
     *
     * depending on the implementation of the management context,
     * this might push it out to one or more remote management nodes.
     */
    public void manage(Entity e) {
        if (isManaged(e)) {
//            if (log.isDebugEnabled()) {
                log.warn(""+this+" redundant call to start management of entity (and descendants of) "+e+"; skipping", 
                    new Throwable("source of duplicate management of "+e));
//            }
            return;
        }
        
        final ManagementTransitionInfo info = new ManagementTransitionInfo(this, ManagementTransitionMode.NORMAL);
        recursively(e, new Predicate<AbstractEntity>() { public boolean apply(AbstractEntity it) {
            it.getManagementSupport().onManagementStarting(info); 
            return manageNonRecursive(it);
        } });
        
        recursively(e, new Predicate<AbstractEntity>() { public boolean apply(AbstractEntity it) {
            it.getManagementSupport().onManagementStarted(info);
            it.setBeingManaged();
            rebindManager.getChangeListener().onManaged(it);
            return true; 
        } });
    }
    
    protected void recursively(Entity e, Predicate<AbstractEntity> action) {
        action.apply( (AbstractEntity)e );
        EntityCollectionReference<?> ref = ((AbstractEntity)e).getOwnedChildrenReference();
        for (String ei: ref.getIds()) {
            Entity entity = ref.peek(ei);
            if (entity==null) entity = getEntity(ei);
            if (entity==null) {
                log.warn("Unable to resolve entity "+ei+" when recursing for management");
            } else {
                recursively( entity, action );
            }
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
        if (shouldSkipUnmanagement(e)) return;
        
        final ManagementTransitionInfo info = new ManagementTransitionInfo(this, ManagementTransitionMode.NORMAL);
        recursively(e, new Predicate<AbstractEntity>() { public boolean apply(AbstractEntity it) {
            if (shouldSkipUnmanagement(it)) return false;
            it.getManagementSupport().onManagementStopping(info); 
            return true;
        } });
        
        recursively(e, new Predicate<AbstractEntity>() { public boolean apply(AbstractEntity it) {
            if (shouldSkipUnmanagement(it)) return false;
            boolean result = unmanageNonRecursive(it);            
            it.getManagementSupport().onManagementStopped(info);
            rebindManager.getChangeListener().onUnmanaged(it);
            return result; 
        } });
    }
    
    protected boolean shouldSkipUnmanagement(Entity e) {
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

    /**
     * Implementor-supplied internal method.
     * <p>
     * Should ensure that the entity is no longer managed anywhere, remove from all lists.
     * Returns true if the entity has been removed from management; if it was not previously managed (anything else throws exception) 
     */
    protected abstract boolean unmanageNonRecursive(Entity e);

    public <T> Task<T> invokeEffector(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters) {
        return runAtEntity(
                MutableMap.builder()
                        .put("expirationPolicy", ExpirationPolicy.NEVER)
                        .put("description", "invoking "+eff.getName()+" on "+entity.getDisplayName())
                        .put("displayName", eff.getName())
                        .put("tags", MutableList.of(EFFECTOR_TAG))
                        .build(), 
                entity, 
                new Callable<T>() {
                    public T call() {
                        // TODO unpleasant cast
                        return ((AbstractEffector<T>)eff).call(entity, parameters);
                    }});
    }

    protected <T> T invokeEffectorMethodLocal(Entity entity, Effector<T> eff, Object args) {
        assert isManagedLocally(entity) : "cannot invoke effector method at "+this+" because it is not managed here";
        totalEffectorInvocationCount.incrementAndGet();
        Object[] transformedArgs = EffectorUtils.prepareArgsForEffector(eff, args);
        return GroovyJavaMethods.invokeMethodOnMetaClass(entity, eff.getName(), transformedArgs);
    }

    /**
     * activates management when effector invoked, warning unless context is acceptable
     * (currently only acceptable context is "start")
     */
    protected void manageIfNecessary(Entity entity, Object context) {
        if (((AbstractEntity) entity).hasEverBeenManaged()) {
            return;
        } else if (!isManaged(entity)) {
            Entity rootUnmanaged = entity;
            while (true) {
                Entity candidateUnmanagedOwner = rootUnmanaged.getOwner();
                if (candidateUnmanagedOwner == null || getEntity(candidateUnmanagedOwner.getId()) != null)
                    break;
                rootUnmanaged = candidateUnmanagedOwner;
            }
            if (context == Startable.START.getName())
                log.info("Activating local management for {} on start", rootUnmanaged);
            else
                log.warn("Activating local management for {} due to effector invocation on {}: {}", new Object[]{rootUnmanaged, entity, context});
            manage(rootUnmanaged);
        }
    }

    /**
     * Method for entity to make effector happen with correct semantics (right place, right task context),
     * when a method is called on that entity.
     * @throws ExecutionException 
     */
    protected <T> T invokeEffectorMethodSync(final Entity entity, final Effector<T> eff, final Object args) throws ExecutionException {
        try {
            Task<?> current = Tasks.current();
            if (current == null || !current.getTags().contains(entity) || !isManagedLocally(entity)) {
                manageIfNecessary(entity, eff.getName());
                // Wrap in a task if we aren't already in a task that is tagged with this entity
                Task<T> task = runAtEntity(
                        MutableMap.builder()
                                .put("expirationPolicy", ExpirationPolicy.NEVER)
                                .put("description", "invoking "+eff.getName()+" on "+entity.getDisplayName())
                                .put("displayName", eff.getName())
                                .put("tags", MutableList.of(EFFECTOR_TAG))
                                .build(), 
                        entity, 
                        new Callable<T>() {
                            public T call() {
                                return invokeEffectorMethodLocal(entity, eff, args);
                            }});
                return task.get();
            } else {
                return invokeEffectorMethodLocal(entity, eff, args);
            }
        } catch (Exception e) {
            throw new ExecutionException("Error invoking "+eff+" on entity "+entity, e);
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
    public abstract <T> Task<T> runAtEntity(@SuppressWarnings("rawtypes") Map flags, Entity entity, Callable<T> c);

    public abstract void addEntitySetListener(CollectionChangeListener<Entity> listener);

    public abstract void removeEntitySetListener(CollectionChangeListener<Entity> listener);
    
    @Override
    public StringConfigMap getConfig() {
        return configMap;
    }
}
