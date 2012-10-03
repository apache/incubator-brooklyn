package brooklyn.entity.group;

import static com.google.common.base.Preconditions.checkNotNull;
import groovy.lang.Closure;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 */
public class DynamicCluster extends AbstractGroup implements Cluster {
    private static final Logger logger = LoggerFactory.getLogger(DynamicCluster.class);

    @SetFromFlag("quarantineFailedEntities")
    public static final ConfigKey<Boolean> QUARANTINE_FAILED_ENTITIES = new BasicConfigKey<Boolean>(
            Boolean.class, "dynamiccluster.quarantineFailedEntities", "Whether to guarantine entities that fail to start, or to try to clean them up", false);

    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;

    public static final BasicNotificationSensor<Entity> ENTITY_QUARANTINED = new BasicNotificationSensor<Entity>(Entity.class, "dynamiccluster.entityQuarantined", "Entity failed to start, and has been quarantined");

    public static final AttributeSensor<Group> QUARANTINE_GROUP = new BasicAttributeSensor<Group>(Group.class, "dynamiccluster.quarantineGroup", "Group of quarantined entities that failed to start");
    
    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];
    
    @SetFromFlag
    EntityFactory<?> factory;

    @SetFromFlag
    Function<Collection<Entity>, Entity> removalStrategy;

    Location location;

    private static final Function<Collection<Entity>, Entity> defaultRemovalStrategy = new Function<Collection<Entity>, Entity>() {
        public Entity apply(Collection<Entity> contenders) {
            // choose last (i.e. newest) entity that is stoppable
            Entity result = null;
            for (Entity it : contenders) {
                if (it instanceof Startable) result = it;
            }
            return result;
        }
    };
    
    /**
     * Instantiate a new DynamicCluster.
     * 
     * Valid properties are:
     * <ul>
     * <li>factory - an {@EntityFactory) (or {@link Closure}) that creates an {@link Entity},
     * typically implementing {@link Startable}, taking the {@link Map}
     * of properties from this cluster as an argument. This property is mandatory.
     * <li>initialSize - an {@link Integer} that is the number of nodes to start when the cluster's {@link #start(List)} method is
     * called. This property is optional, with a default of 1.
     * </ul>
     *
     * @param properties the properties of the cluster (these may be visible to created children by inheritance,
     *  but to set properties on children explicitly, use the factory)
     * @param owner the entity that owns this cluster (optional)
     */
    public DynamicCluster(Map<?,?> properties, Entity owner) {
        super(properties, owner);
        if (removalStrategy == null) removalStrategy = defaultRemovalStrategy;
        setAttribute(SERVICE_UP, false);
    }
    public DynamicCluster(Entity owner) {
        this(Maps.newLinkedHashMap(), owner);
    }
    public DynamicCluster(Map<?,?> properties) {
        this(properties, null);
    }

    public void setRemovalStrategy(Function<Collection<Entity>, Entity> val) {
        removalStrategy = checkNotNull(val, "removalStrategy");
    }
    
    public void setRemovalStrategy(Closure val) {
        setRemovalStrategy(GroovyJavaMethods.functionFromClosure(val));
    }

    public EntityFactory<?> getFactory() {
        return factory;
    }
    
    public void setFactory(EntityFactory<?> factory) {
        this.factory = factory;
    }
    
    private boolean isQuarantineEnabled() {
        return getConfig(QUARANTINE_FAILED_ENTITIES);
    }
    
    private Group getQuarantineGroup() {
        return getAttribute(QUARANTINE_GROUP);
    }
    
    public void start(Collection<? extends Location> locs) {
        if (isQuarantineEnabled()) {
            Group quarantineGroup = new BasicGroup(MutableMap.of("displayName", "quarantine"), this);
            getManagementContext().manage(quarantineGroup);
            setAttribute(QUARANTINE_GROUP, quarantineGroup);
        }
        
        Preconditions.checkNotNull(locs, "locations must be supplied");
        Preconditions.checkArgument(locs.size() == 1, "Exactly one location must be supplied");
        location = Iterables.getOnlyElement(locs);
        getLocations().add(location);
        setAttribute(SERVICE_STATE, Lifecycle.STARTING);
        resize(getConfig(INITIAL_SIZE));
        if (getCurrentSize() != getConfig(INITIAL_SIZE)) {
            throw new IllegalStateException("On start of cluster "+this+", failed to get to initial size of "+getConfig(INITIAL_SIZE)+"; size is "+getCurrentSize());
        }
        for (Policy it : getPolicies()) { it.resume(); }
        setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
        setAttribute(SERVICE_UP, calculateServiceUp());
    }

    public void stop() {
        setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
        setAttribute(SERVICE_UP, calculateServiceUp());
        for (Policy it : getPolicies()) { it.suspend(); }
        resize(0);
        setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
        setAttribute(SERVICE_UP, calculateServiceUp());
    }

    public void restart() {
        throw new UnsupportedOperationException();
    }
    
//    public Integer resize(Integer desiredSize) {
//    }
    
    public Integer resize(Integer desiredSize) {
        synchronized (mutex) {
            int currentSize = getCurrentSize();
            int delta = desiredSize - currentSize;
            if (delta != 0) {
                logger.info("Resize {} from {} to {}", new Object[] {this, currentSize, desiredSize});
            } else {
                if (logger.isDebugEnabled()) logger.debug("Resize no-op {} from {} to {}", new Object[] {this, currentSize, desiredSize});
            }
    
            if (delta > 0) {
                grow(delta);
            } else if (delta < 0) {
                shrink(delta);
            } else {
                setAttribute(Changeable.GROUP_SIZE, currentSize);
            }
        }
        return getCurrentSize();
    }

    /**
     * Increases the cluster size by the given number.
     */
    private void grow(int delta) {
        Collection<Entity> addedEntities = Lists.newArrayList();
        for (int i = 0; i < delta; i++) {
            addedEntities.add(addNode());
        }
        Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();
        for (Entity entity: addedEntities) {
            Map<String,?> args = ImmutableMap.of("locations", ImmutableList.of(location));
            tasks.put(entity, entity.invoke(Startable.START, args));
        }
        Map<Entity, Throwable> errors = waitForTasksOnEntityStart(tasks);
        
        if (!errors.isEmpty()) {
            if (isQuarantineEnabled()) {
                quarantineFailedNodes(errors.keySet());
            } else {
                cleanupFailedNodes(errors.keySet());
            }
        }
    }
    
    private void shrink(int delta) {
        Collection<Entity> removedEntities = Lists.newArrayList();
        
        for (int i = 0; i < (delta*-1); i++) { removedEntities.add(removeNode()); }

        Task<List<Void>> invoke = Entities.invokeEffectorList(this, removedEntities, Startable.STOP, Collections.<String,Object>emptyMap());
        try {
            invoke.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        } catch (ExecutionException e) {
            throw Throwables.propagate(e);
        }
    }
    
    private void quarantineFailedNodes(Collection<Entity> failedEntities) {
        for (Entity entity : failedEntities) {
            emit(ENTITY_QUARANTINED, entity);
            getQuarantineGroup().addMember(entity);
            removeMember(entity);
        }
    }
    
    private void cleanupFailedNodes(Collection<Entity> failedEntities) {
        // TODO Could also call stop on them?
        for (Entity entity : failedEntities) {
            removeNode(entity);
        }
    }
    
    /**
     * Default impl is to be up when running, and !up otherwise.
     */
    protected boolean calculateServiceUp() {
        return getAttribute(SERVICE_STATE) == Lifecycle.RUNNING;
    }
    
    protected Map<Entity, Throwable> waitForTasksOnEntityStart(Map<Entity,Task<?>> tasks) {
        // TODO Could have CompoundException, rather than propagating first
        Map<Entity, Throwable> errors = Maps.newLinkedHashMap();
        
        for (Map.Entry<Entity,Task<?>> entry : tasks.entrySet()) {
            Entity entity = entry.getKey();
            Task<?> task = entry.getValue();
            try {
                try {
                    task.get();
                } catch (Throwable t) {
                    throw unwrapException(t);
                }
            } catch (InterruptedException e) {
                // TODO This interrupt could have come from the task's thread, so don't want to interrupt this thread!
                Thread.currentThread().interrupt();
                throw Throwables.propagate(e);
            } catch (Throwable t) {
                logger.error("Cluster "+this+" failed to start entity "+entity, t);
                errors.put(entity, t);
            }
        }
        return errors;
    }
    
    protected Throwable unwrapException(Throwable e) {
        if (e instanceof ExecutionException) {
            return unwrapException(e.getCause());
        } else if (e instanceof org.codehaus.groovy.runtime.InvokerInvocationException) {
            return unwrapException(e.getCause());
        } else {
            return e;
        }
    }
    
    @Override
    public boolean removeOwnedChild(Entity child) {
        boolean changed = super.removeOwnedChild(child);
        if (changed) {
            removeMember(child);
        }
        return changed;
    }
    
    protected Map getCustomChildFlags() { return Maps.newLinkedHashMap(); }
    
    protected Entity addNode() {
        Map creation = Maps.newLinkedHashMap();
        creation.putAll(getCustomChildFlags());
        if (logger.isDebugEnabled()) logger.debug("Adding a node to {}({}) with properties {}", new Object[] {getDisplayName(), getId(), creation});

        if (factory==null) 
            throw new IllegalStateException("EntityFactory factory not supplied for "+this);
        Entity entity = (factory instanceof EntityFactoryForLocation ? ((EntityFactoryForLocation)factory).newFactoryForLocation(location) : factory).
            newEntity(creation, this);
        if (entity==null || !(entity instanceof Entity)) 
            throw new IllegalStateException("EntityFactory factory routine did not return an entity, in "+this+" ("+entity+")");
        
        getManagementContext().manage(entity);
        addMember(entity);
        return entity;
    }

    protected Entity removeNode() {
        
        // TODO use pluggable strategy; default is to remove newest
        // TODO inefficient impl
        Preconditions.checkState(getMembers().size() > 0, "Attempt to remove a node when members is empty, from cluster "+this);
        if (logger.isDebugEnabled()) logger.debug("Removing a node from {}", this);
        
        Entity entity = removalStrategy.apply(getMembers());
        Preconditions.checkNotNull(entity, "No entity chosen for removal from "+getId());
        Preconditions.checkState(entity instanceof Startable, "Chosen entity for removal not stoppable: cluster="+this+"; choice="+entity);
        
        return removeNode(entity);
    }
    
    protected Entity removeNode(Entity entity) {
        removeMember(entity);
        managementContext.unmanage(entity);
        
        return entity;
    }
}
