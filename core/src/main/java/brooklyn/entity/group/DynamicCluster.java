package brooklyn.entity.group;

import static com.google.common.base.Preconditions.checkNotNull;
import groovy.lang.Closure;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
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

    public static final Effector<String> REPLACE_MEMBER = new MethodEffector<String>(DynamicCluster.class, "replaceMember");

    @SetFromFlag("quarantineFailedEntities")
    public static final ConfigKey<Boolean> QUARANTINE_FAILED_ENTITIES = new BasicConfigKey<Boolean>(
            Boolean.class, "dynamiccluster.quarantineFailedEntities", "Whether to guarantine entities that fail to start, or to try to clean them up", false);

    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;

    public static final BasicNotificationSensor<Entity> ENTITY_QUARANTINED = new BasicNotificationSensor<Entity>(Entity.class, "dynamiccluster.entityQuarantined", "Entity failed to start, and has been quarantined");

    public static final AttributeSensor<Group> QUARANTINE_GROUP = new BasicAttributeSensor<Group>(Group.class, "dynamiccluster.quarantineGroup", "Group of quarantined entities that failed to start");
    
    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];
    
    @SetFromFlag("factory")
    public static final ConfigKey<EntityFactory> FACTORY = new BasicConfigKey<EntityFactory>(
            EntityFactory.class, "dynamiccluster.factory", "factory for creating new cluster members", null);

    @SetFromFlag("removalStrategy")
    public static final ConfigKey<Function<Collection<Entity>, Entity>> REMOVAL_STRATEGY = new BasicConfigKey(
            Function.class, "dynamiccluster.removalstrategy", "strategy for deciding what to remove when down-sizing", null);

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
     * @param parent the entity that owns this cluster (optional)
     */
    public DynamicCluster(Map<?,?> properties, Entity parent) {
        super(properties, parent);
        setAttribute(SERVICE_UP, false);
    }
    public DynamicCluster(Entity parent) {
        this(Maps.newLinkedHashMap(), parent);
    }
    public DynamicCluster(Map<?,?> properties) {
        this(properties, null);
    }
    
    public void setRemovalStrategy(Function<Collection<Entity>, Entity> val) {
        setConfig(REMOVAL_STRATEGY, checkNotNull(val, "removalStrategy"));
    }
    
    public void setRemovalStrategy(Closure val) {
        setRemovalStrategy(GroovyJavaMethods.functionFromClosure(val));
    }

    public Function<Collection<Entity>, Entity> getRemovalStrategy() {
        Function<Collection<Entity>, Entity> result = getConfig(REMOVAL_STRATEGY);
        return (result != null) ? result : defaultRemovalStrategy;
    }
    
    public EntityFactory<?> getFactory() {
        return getConfig(FACTORY);
    }
    
    public void setFactory(EntityFactory<?> factory) {
        setConfigEvenIfOwned(FACTORY, factory);
    }
    
    private Location getLocation() {
        return Iterables.getOnlyElement(getLocations());
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
            Entities.manage(quarantineGroup);
            setAttribute(QUARANTINE_GROUP, quarantineGroup);
        }
        
        if (locs==null) throw new IllegalStateException("Null location supplied to start "+this);
        if (locs.size()!=1) throw new IllegalStateException("Wrong number of locations supplied to start "+this+": "+locs);
        getLocations().addAll(locs);
        setAttribute(SERVICE_STATE, Lifecycle.STARTING);
        Integer initialSize = getConfig(INITIAL_SIZE);
        resize(initialSize);
        if (getCurrentSize() != initialSize) {
            throw new IllegalStateException("On start of cluster "+this+", failed to get to initial size of "+initialSize+"; size is "+getCurrentSize());
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
            }
        }
        return getCurrentSize();
    }

    /**
     * 
     * @param memberId
     * @throws NoSuchElementException If entity cannot be resolved, or it is not a member 
     */
    @Description("Replaces the entity with the given ID, if it is a member; first adds a new member, then removes this one. "+
            "Returns id of the new entity; or throws exception if couldn't be replaced.")
    public String replaceMember(@NamedParameter("memberId") @Description("The entity id of a member to be replaced") String memberId) {
        Entity member = getManagementContext().getEntity(memberId);
        logger.info("In {}, replacing member {} ({})", new Object[] {this, memberId, member});

        if (member == null) {
            throw new NoSuchElementException("In "+this+", entity "+memberId+" cannot be resolved, so not replacing");
        }

        synchronized (mutex) {
            if (!getMembers().contains(member)) {
                throw new NoSuchElementException("In "+this+", entity "+member+" is not a member so not replacing");
            }
            
            Collection<Entity> addedEntities = grow(1);
            if (addedEntities.size() < 1) {
                String msg = String.format("In %s, failed to grow, to replace %s; not removing", this, member);
                throw new IllegalStateException(msg);
            }
            
            stopAndRemoveNode(member);
            
            return Iterables.get(addedEntities, 0).getId();
        }
    }

    /**
     * Increases the cluster size by the given number.
     */
    private Collection<Entity> grow(int delta) {
        Collection<Entity> addedEntities = Lists.newArrayList();
        for (int i = 0; i < delta; i++) {
            addedEntities.add(addNode());
        }
        Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();
        for (Entity entity: addedEntities) {
            Map<String,?> args = ImmutableMap.of("locations", ImmutableList.of(getLocation()));
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
        
        return MutableList.<Entity>builder().addAll(addedEntities).removeAll(errors.keySet()).build();
    }
    
    private void shrink(int delta) {
        Collection<Entity> removedEntities = Lists.newArrayList();
        
        for (int i = 0; i < (delta*-1); i++) { removedEntities.add(pickAndRemoveMember()); }

        // FIXME symmetry in order of added as child, managed, started, and added to group
        // FIXME assume stoppable; use logic of grow?
        Task<List<Void>> invoke = Entities.invokeEffectorList(this, removedEntities, Startable.STOP, Collections.<String,Object>emptyMap());
        try {
            invoke.get();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } finally {
            for (Entity removedEntity : removedEntities) {
                discardNode(removedEntity);
            }
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
            discardNode(entity);
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
                task.get();
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            } catch (Throwable t) {
                logger.error("Cluster "+this+" failed to start entity "+entity+" (removing): "+t, t);
                errors.put(entity, unwrapException(t));
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
    public boolean removeChild(Entity child) {
        boolean changed = super.removeChild(child);
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

        EntityFactory<?> factory = getFactory();
        if (factory == null) 
            throw new IllegalStateException("EntityFactory factory not supplied for "+this);
        Entity entity = (factory instanceof EntityFactoryForLocation ? ((EntityFactoryForLocation)factory).newFactoryForLocation(getLocation()) : factory).
            newEntity(creation, this);
        if (entity==null) 
            throw new IllegalStateException("EntityFactory factory routine did not return an entity, in "+this+" ("+entity+")");
        
        Entities.manage(entity);
        addMember(entity);
        return entity;
    }

    protected Entity pickAndRemoveMember() {
        
        // TODO use pluggable strategy; default is to remove newest
        // TODO inefficient impl
        Preconditions.checkState(getMembers().size() > 0, "Attempt to remove a node when members is empty, from cluster "+this);
        if (logger.isDebugEnabled()) logger.debug("Removing a node from {}", this);
        
        Entity entity = getRemovalStrategy().apply(getMembers());
        Preconditions.checkNotNull(entity, "No entity chosen for removal from "+getId());
        Preconditions.checkState(entity instanceof Startable, "Chosen entity for removal not stoppable: cluster="+this+"; choice="+entity);

        removeMember(entity);
        return entity;
    }
    
    protected void discardNode(Entity entity) {
        removeMember(entity);
        Entities.unmanage(entity);
    }
    
    protected void stopAndRemoveNode(Entity member) {
        removeMember(member);
        
        try {
            if (member instanceof Startable) {
                Task<?> task = member.invoke(Startable.STOP, Collections.<String,Object>emptyMap());
                try {
                    task.get();
                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }
        } finally {
            Entities.unmanage(member);
        }
    }
}
