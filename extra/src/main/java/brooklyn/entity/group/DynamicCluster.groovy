package brooklyn.entity.group

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Resizable
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location

import com.google.common.base.Preconditions

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 */
public class DynamicCluster extends AbstractGroup implements Startable, Resizable {
    private static final Logger logger = LoggerFactory.getLogger(DynamicCluster)

    public static final BasicConfigKey<Integer> INITIAL_SIZE = [ Integer, "cluster.initial.size", "Initial cluster size" ]

    Closure<Entity> newEntity
    int initialSize
    Map properties

    private Location location

    /**
     * Instantiate a new DynamicCluster. Valid properties are:
     * <ul>
     * <li>template - an {@link Entity} that implements {@link Startable} that will be the template for nodes in the cluster.
     * <li>initialSize - an {@link Integer} that is the number of nodes to start when the cluster's {@link #start(List)} method is
     * called, default is 0.
     * </ul>
     *
     * @param properties the properties of the new entity.
     * @param owner the entity that owns this cluster (optional)
     */
    public DynamicCluster(Map properties = [:], Entity owner = null) {
        super(properties, owner)

        Preconditions.checkArgument properties.containsKey('newEntity'), "'newEntity' property is mandatory"
        Preconditions.checkArgument properties.get('newEntity') instanceof Closure, "'newEntity' must be a closure"
        newEntity = properties.remove('newEntity')
        
        initialSize = getConfig(INITIAL_SIZE) ?: properties.initialSize ?: 0
        setConfig(INITIAL_SIZE, initialSize)

        this.properties = properties

        setAttribute(SERVICE_UP, false)
    }

    public void start(Collection<? extends Location> locations) {
        Preconditions.checkNotNull locations, "locations must be supplied"
        Preconditions.checkArgument locations.size() == 1, "Exactly one location must be supplied"
        location = locations.find { true }
        resize(initialSize)
        setAttribute(SERVICE_UP, true)
    }

    public void stop() {
        resize(0)
        setAttribute(SERVICE_UP, false)
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }

    public synchronized Integer resize(int desiredSize) {
        int delta = desiredSize - currentSize
        logger.info "Resize from {} to {}; delta = {}", currentSize, desiredSize, delta

        Collection<Entity> addedEntities = []
        Collection<Entity> removedEntities = []

        if (delta > 0) {
            delta.times { addedEntities += addNode() }
        } else if (delta < 0) {
            (-delta).times { removedEntities += removeNode() }
        }

        return currentSize
    }

    protected Entity addNode() {
        Map creation = [:]
        creation << properties
        creation.put("owner", this)
        logger.trace "Adding a node to {} with properties {}", id, creation

        Entity entity = newEntity.call(creation)
        Preconditions.checkNotNull entity, "newEntity call returned null"
        Preconditions.checkState entity instanceof Entity, "newEntity call returned an object that is not an Entity"
        Preconditions.checkState entity instanceof Startable, "newEntity call returned an object that is not Startable"
 
        entity.start([location])
        addMember(entity)
        entity
    }

    protected Entity removeNode() {
        logger.info "Removing a node"
        Entity entity = members.find { true } // TODO use specific criteria
        Preconditions.checkNotNull entity, "No member entity found to remove"
        Preconditions.checkState entity instanceof Startable, "Member entity is not Startable"
 
        removeMember(entity)
        entity.stop()
        entity
    }
}
