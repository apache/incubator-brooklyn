package brooklyn.entity.group

import brooklyn.entity.basic.AbstractGroup

import java.util.Collection
import java.util.Map
import java.util.concurrent.ExecutionException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.Entities;
import brooklyn.entity.trait.Changeable
import brooklyn.entity.trait.Startable
import brooklyn.event.EntityStartException
import brooklyn.location.Location
import brooklyn.management.Task
import brooklyn.util.flags.SetFromFlag;

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
    Closure<Entity> newEntity
    
    @SetFromFlag
    Closure postStartEntity

    Location location
    private Map createFlags

    /**
     * Instantiate a new DynamicCluster.
     * 
     * Valid properties are:
     * <ul>
     * <li>newEntity - a {@link Closure} that creates an {@link Entity} that implements {@link Startable}, taking the {@link Map}
     * of properties from this cluster as an argument. This property is mandatory.
     * <li>postStartEntity - a {@link Closure} that is called after newEntity, taking the {@link Entity} as an argument. This property is optional, with a default of no-op.
     * <li>initialSize - an {@link Integer} that is the number of nodes to start when the cluster's {@link #start(List)} method is
     * called. This property is optional, with a default of 0.
     * </ul>
     *
     * @param properties the properties of the cluster and any new entity.
     * @param owner the entity that owns this cluster (optional)
     */
    public DynamicCluster(Map properties = [:], Entity owner = null) {
        super(properties, owner)

        Preconditions.checkNotNull newEntity, "'newEntity' property is mandatory"

        // Save flags for use when creating members
        // TODO But we aren't calling remove anymore; passing them to the child isn't good because the 
        // string in the properties isn't as unique as the ConfigKey constant!
        // Alex agrees: use a config key instead. remove createFlags.
        // (you'll be getting a warning anyway from configure(Map) that some flags haven't been applied!
        createFlags = properties

        setAttribute(SERVICE_UP, false)
    }

    public void start(Collection<? extends Location> locations) {
        Preconditions.checkNotNull locations, "locations must be supplied"
        Preconditions.checkArgument locations.size() == 1, "Exactly one location must be supplied"
        location = Iterables.getOnlyElement(locations)
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
                logger.info "Resize from {} to {}; delta = {}", currentSize, desiredSize, delta
            } else {
                if (logger.isDebugEnabled()) logger.debug "Resize no-op from {} to {}", currentSize, desiredSize
            }
    
            Collection<Entity> addedEntities = []
            Collection<Entity> removedEntities = []

            if (delta > 0) {
                delta.times { addedEntities += addNode() }
                Map<Entity, Task> tasks = [:]
                addedEntities.each { entity ->
                    tasks.put(entity, entity.invoke(Startable.START, [locations:[ location ]]))
                }

                // TODO Could have CompoundException, rather than propagating first
                Throwable toPropagate = null
                tasks.each { Entity entity, Task task ->
                    try {
                        try {
                            task.get()
                            if (postStartEntity) postStartEntity.call(entity)
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
    
    protected Entity addNode() {
        Map creation = [:]
        creation << createFlags
        if (logger.isDebugEnabled()) logger.debug "Adding a node to {} with properties {}", id, creation

        Entity entity
        if (newEntity.maximumNumberOfParameters > 1) {
            entity = newEntity.call(creation, this)
        } else {
            entity = newEntity.call(creation)
        } 
        if (entity.owner == null) addOwnedChild(entity)
        Preconditions.checkNotNull entity, "newEntity call returned null"
        Preconditions.checkState entity instanceof Entity, "newEntity call returned an object that is not an Entity"
        Preconditions.checkState entity instanceof Startable, "newEntity call returned an object that is not Startable"
 
        addMember(entity)
        entity
    }

    protected Entity removeNode() {
        // TODO use pluggable strategy; default is to remove newest
        // TODO inefficient impl
        if (logger.isDebugEnabled()) logger.debug "Removing a node from {}", id
        Entity entity
        members.each {
            if (it instanceof Startable) entity = it
        }
        Preconditions.checkNotNull entity, "No Startable member entity found to remove from $id"
        removeNode(entity)
    }
    
    protected Entity removeNode(Entity entity) {
        removeMember(entity)
        managementContext.unmanage(entity)
        
        entity
    }
}
