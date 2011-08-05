package brooklyn.entity.group

import brooklyn.entity.basic.AbstractGroup

import java.util.Collection
import java.util.Map
import java.util.concurrent.ExecutionException

import javax.management.InstanceOfQueryExp;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Changeable
import brooklyn.entity.trait.Startable
import brooklyn.event.EntityStartException
import brooklyn.location.Location
import brooklyn.management.Task
import brooklyn.policy.trait.Suspendable

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 */
public class DynamicCluster extends AbstractGroup implements Cluster {
    private static final Logger logger = LoggerFactory.getLogger(DynamicCluster)

    Closure<Entity> newEntity

    Location location
    private Map createFlags

    /**
     * Instantiate a new DynamicCluster.
     * 
     * Valid properties are:
     * <ul>
     * <li>newEntity - a {@link Closure} that creates an {@link Entity} that implements {@link Startable}, taking the {@link Map}
     * of properties from this cluster as an argument. This property is mandatory.
     * <li>initialSize - an {@link Integer} that is the number of nodes to start when the cluster's {@link #start(List)} method is
     * called. This property is optional, with a default of 0.
     * </ul>
     *
     * @param properties the properties of the cluster and any new entity.
     * @param owner the entity that owns this cluster (optional)
     */
    public DynamicCluster(Map properties = [:], Entity owner = null) {
        super(properties, owner)

        Preconditions.checkArgument properties.containsKey('newEntity'), "'newEntity' property is mandatory"
        Preconditions.checkArgument properties.get('newEntity') instanceof Closure, "'newEntity' must be a closure"
        newEntity = properties.remove('newEntity')
        
        setConfigIfValNonNull(INITIAL_SIZE, properties.initialSize)

        // Save flags for use when creating members
        // TODO But we aren't calling remove anymore; passing them to the child isn't good because the 
        // string in the properties isn't as unique as the ConfigKey constant!
        createFlags = properties

        setAttribute(SERVICE_UP, false)
    }

    public void start(Collection<Location> locations) {
        Preconditions.checkNotNull locations, "locations must be supplied"
        Preconditions.checkArgument locations.size() == 1, "Exactly one location must be supplied"
        location = Iterables.getOnlyElement(locations)
        this.locations.add(location)
        resize(getConfig(INITIAL_SIZE))
        policies.each { if (it instanceof Suspendable) it.resume() }
        setAttribute(SERVICE_UP, true)
    }

    public void stop() {
        policies.each { if (it instanceof Suspendable) it.suspend() }
        resize(0)
        setAttribute(SERVICE_UP, false)
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }

    public Integer resize(Integer desiredSize) {
        synchronized (members) {
            int delta = desiredSize - currentSize
            logger.info "Resize from {} to {}; delta = {}", currentSize, desiredSize, delta
    
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
                        } catch (Exception e) {
                            throw unwrapException(e)
                        }
                    } catch (EntityStartException e) {
                        logger.error("Cluster $this failed to start entity $entity", e)
                        removeNode(entity)
                    } catch (InterruptedException e) {
                        throw e
                    } catch (Exception e) {
                        if (!toPropagate) toPropagate = e
                    }
                }
                if (toPropagate) throw toPropagate
                
            } else if (delta < 0) {
                (-delta).times { removedEntities += removeNode() }

                Task invoke = invokeEffectorList(removedEntities, Startable.STOP, [:])
                invoke.get()
                
            } else {
                setAttribute(Changeable.GROUP_SIZE, currentSize)
            }
        }
        return currentSize
    }

    private Throwable unwrapException(Exception e) {
        if (e instanceof ExecutionException) {
            return unwrapException(e.cause)
        } else if (e instanceof org.codehaus.groovy.runtime.InvokerInvocationException) {
            return unwrapException(e.cause)
        } else {
            return e
        }
    }
    
    protected Entity addNode() {
        Map creation = [:]
        creation << createFlags
        logger.trace "Adding a node to {} with properties {}", id, creation

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
        logger.info "Removing a node"
        Entity entity
        members.each {
            if (it instanceof Startable) entity = it
        }
        Preconditions.checkNotNull entity, "No Startable member entity found to remove"
        removeNode(entity)
    }
    
    protected Entity removeNode(Entity entity) {
        removeMember(entity)
        managementContext.unmanage(entity)
        
        entity
    }
}
