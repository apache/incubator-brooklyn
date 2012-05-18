package brooklyn.entity.group

import static com.google.common.base.Preconditions.checkNotNull

import java.util.Collection
import java.util.Map
import java.util.concurrent.ExecutionException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.ConfigurableEntityFactory
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityFactoryForLocation
import brooklyn.entity.trait.Changeable
import brooklyn.entity.trait.Startable
import brooklyn.event.EntityStartException
import brooklyn.location.Location
import brooklyn.management.Task
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Function
import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 */
public class DynamicCluster extends AbstractGroup implements Cluster {
    private static final Logger logger = LoggerFactory.getLogger(DynamicCluster)

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];
    
    @SetFromFlag
    ConfigurableEntityFactory factory

    @SetFromFlag
    Function<Collection<Entity>, Entity> removalStrategy

    Location location

    private static final Function<Collection<Entity>, Entity> defaultRemovalStrategy = new Function<Collection<Entity>, Entity>() {
        public Entity apply(Collection<Entity> contenders) {
            // choose last (i.e. newest) entity that is stoppable
            Entity result
            contenders.each {
                if (it instanceof Startable) result = it
            }
            return result
        }
    }
    
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
    public DynamicCluster(Map properties = [:], Entity owner = null) {
        super(properties, owner)
        removalStrategy = removalStrategy ?: defaultRemovalStrategy
        setAttribute(SERVICE_UP, false)
    }
    public DynamicCluster(Entity owner) { this([:], owner) }

    public void setRemovalStrategy(Function<Collection<Entity>, Entity> val) {
        removalStrategy = checkNotNull(val, "removalStrategy")
    }
    
    public void setRemovalStrategy(Closure val) {
        setRemovalStrategy(val as Function)
    }

    public void start(Collection<? extends Location> locs) {
        Preconditions.checkNotNull locs, "locations must be supplied"
        Preconditions.checkArgument locs.size() == 1, "Exactly one location must be supplied"
        location = Iterables.getOnlyElement(locs)
        this.locations.add(location)
        resize(getConfig(INITIAL_SIZE))
        policies.each { it.resume() }
        setAttribute(SERVICE_UP, true)
    }

    public void stop() {
        policies.each { it.suspend() }
        resize(0)
        setAttribute(SERVICE_UP, false)
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }

    public Integer resize(Integer desiredSize) {
        synchronized (mutex) {
            int delta = desiredSize - currentSize
            // Make a local copy of this - otherwise the closure later on tries to bind to a variable called logger in
            // the context of the class that is invoking the closure (maybe related to it being a static class member?)
            final Logger logger = DynamicCluster.logger
            if (delta != 0) {
                logger.info "Resize {} from {} to {}; delta = {}", this, currentSize, desiredSize, delta
            } else {
                if (logger.isDebugEnabled()) logger.debug "Resize no-op {} from {} to {}", this, currentSize, desiredSize
            }
    
            Collection<Entity> addedEntities = []
            Collection<Entity> removedEntities = []

            if (delta > 0) {
                delta.times { addedEntities += addNode() }
                Map<Entity, Task> tasks = [:]
                addedEntities.each { entity ->
                    tasks.put(entity, entity.invoke(Startable.START, [locations:[ location ]]))
                }
                waitForTasksOnEntityStart(tasks);                
            } else if (delta < 0) {
                (-delta).times { removedEntities += removeNode() }

                Task invoke = Entities.invokeEffectorList(this, removedEntities, Startable.STOP, [:])
                invoke.get()
            } else {
                setAttribute(Changeable.GROUP_SIZE, currentSize)
            }
        }
        return currentSize
    }

    protected void waitForTasksOnEntityStart(Map tasks) {
        // TODO Could have CompoundException, rather than propagating first
        Throwable toPropagate = null
        tasks.each { Entity entity, Task task ->
            try {
                try {
                    task.get()
                } catch (Throwable t) {
                    throw unwrapException(t)
                }
            } catch (EntityStartException e) {
                logger.error("Cluster $this failed to start entity $entity", e)
                removeNode(entity)
            } catch (InterruptedException e) {
                throw e
            } catch (Throwable t) {
                if (!toPropagate) toPropagate = t
            }
        }
        if (toPropagate) throw toPropagate
    }
    
    protected Throwable unwrapException(Throwable e) {
        if (e instanceof ExecutionException) {
            return unwrapException(e.cause)
        } else if (e instanceof org.codehaus.groovy.runtime.InvokerInvocationException) {
            return unwrapException(e.cause)
        } else {
            return e
        }
    }
    
    @Override
    public boolean removeOwnedChild(Entity child) {
        boolean changed = super.removeOwnedChild(child)
        if (changed) {
            removeMember(child)
        }
    }
    
    protected Map getCustomChildFlags() { [:] }
    
    protected Entity addNode() {
        Map creation = [:]
        creation << getCustomChildFlags()
        if (logger.isDebugEnabled()) logger.debug "Adding a node to {}({}) with properties {}", displayName, id, creation

        if (factory==null) 
            throw new IllegalStateException("EntityFactory factory not supplied for $this")
        Entity entity = (factory in EntityFactoryForLocation ? ((EntityFactoryForLocation)factory).newFactoryForLocation(location) : factory).
            newEntity(creation, this)
        if (entity==null || !(entity in Entity)) 
            throw new IllegalStateException("EntityFactory factory routine did not return an entity, in $this ($entity)")
        
        addMember(entity)
        entity
    }

    protected Entity removeNode() {
        
        // TODO use pluggable strategy; default is to remove newest
        // TODO inefficient impl
        Preconditions.checkState(members.size() > 0, "Attempt to remove a node when members is empty, from cluster $this")
        if (logger.isDebugEnabled()) logger.debug "Removing a node from {}", this
        
        Entity entity = removalStrategy.apply(members)
        Preconditions.checkNotNull entity, "No entity chosen for removal from $id"
        Preconditions.checkState(entity instanceof Startable, "Chosen entity for removal not stoppable: cluster=$this; choice=$entity")
        
        removeNode(entity)
    }
    
    protected Entity removeNode(Entity entity) {
        removeMember(entity)
        managementContext.unmanage(entity)
        
        entity
    }
}
