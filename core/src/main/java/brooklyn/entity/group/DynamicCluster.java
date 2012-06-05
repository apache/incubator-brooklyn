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

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.EntityStartException;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.util.GroovyJavaMethods;
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

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];
    
    @SetFromFlag
    ConfigurableEntityFactory factory;

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

    public ConfigurableEntityFactory getFactory() {
        return factory;
    }
    
    public void start(Collection<? extends Location> locs) {
        Preconditions.checkNotNull(locs, "locations must be supplied");
        Preconditions.checkArgument(locs.size() == 1, "Exactly one location must be supplied");
        location = Iterables.getOnlyElement(locs);
        getLocations().add(location);
        resize(getConfig(INITIAL_SIZE));
        for (Policy it : getPolicies()) { it.resume(); }
        setAttribute(SERVICE_UP, true);
    }

    public void stop() {
        for (Policy it : getPolicies()) { it.suspend(); }
        resize(0);
        setAttribute(SERVICE_UP, false);
    }

    public void restart() {
        throw new UnsupportedOperationException();
    }

    public Integer resize(Integer desiredSize) {
        synchronized (mutex) {
            int currentSize = getCurrentSize();
            int delta = desiredSize - currentSize;
            if (delta != 0) {
                logger.info("Resize {} from {} to {}; delta = {}", new Object[] {this, currentSize, desiredSize, delta});
            } else {
                if (logger.isDebugEnabled()) logger.debug("Resize no-op {} from {} to {}", new Object[] {this, currentSize, desiredSize});
            }
    
            Collection<Entity> addedEntities = Lists.newArrayList();
            Collection<Entity> removedEntities = Lists.newArrayList();

            if (delta > 0) {
                for (int i = 0; i < delta; i++) { addedEntities.add(addNode()); }
                Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();
                for (Entity entity: addedEntities) {
                    Map<String,?> args = ImmutableMap.of("locations", ImmutableList.of(location));
                    tasks.put(entity, entity.invoke(Startable.START, args));
                }
                waitForTasksOnEntityStart(tasks);                
            } else if (delta < 0) {
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
            } else {
                setAttribute(Changeable.GROUP_SIZE, currentSize);
            }
        }
        return getCurrentSize();
    }

    protected void waitForTasksOnEntityStart(Map<Entity,Task<?>> tasks) {
        // TODO Could have CompoundException, rather than propagating first
        Throwable toPropagate = null;
        for (Map.Entry<Entity,Task<?>> entry : tasks.entrySet()) {
            Entity entity = entry.getKey();
            Task<?> task = entry.getValue();
            try {
                try {
                    task.get();
                } catch (Throwable t) {
                    throw unwrapException(t);
                }
            } catch (EntityStartException e) {
                logger.error("Cluster "+this+" failed to start entity "+entity, e);
                removeNode(entity);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw Throwables.propagate(e);
            } catch (Throwable t) {
                if (toPropagate == null) toPropagate = t;
            }
        }
        if (toPropagate != null) throw Throwables.propagate(toPropagate);
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
