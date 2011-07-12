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

    public static final BasicConfigKey<Integer> INITIAL_SIZE = [ Integer, "initial.size", "Initial cluster size" ]

    public static final BasicAttributeSensor<String> CLUSTER_SIZE = [ Integer, "cluster.size", "Cluster size" ]

    Closure<Entity> newEntity
    int initialSize
    Map properties

    private Location location

    /**
     * Instantiate a new DynamicCluster. Valid properties are:
     * <ul>
     * <li>template - an {@link Entity} that implements {@link Startable} that will be the template for nodes in the cluster.
     * <li>initialSize - an {@link Integer} that is the number of nodes to start when the cluster's {@link #start(Collection)} method is
     * called.
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

        initialSize = getConfig(INITIAL_SIZE) ?: properties.remove("initialSize") ?: 1
        setConfig(INITIAL_SIZE, initialSize)

        this.properties = properties
    }

    public void start(Collection<? extends Location> locations) {
        Preconditions.checkNotNull locations, "locations must be supplied"
        Preconditions.checkArgument locations.size() == 1, "Exactly one location must be supplied"
        location = locations.any { true }
        resize(initialSize)
    }

    public void stop() {
        resize(0)
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }

    public Integer resize(int desiredSize) {
        int delta = desiredSize - currentSize
        logger.info "Resize from {} to {}; delta = {}", currentSize, desiredSize, delta

        Collection<Entity> addedEntities = []
        Collection<Entity> removedEntities = []

        if (delta > 0) {
            delta.times { addedEntities += addNode() }
        } else if (delta < 0) {
            (-delta).times { removedEntities += removeNode() }
        }

        setAttribute(CLUSTER_SIZE, currentSize)

        return currentSize
    }

    protected Entity addNode() {
        logger.info "Adding a node"
        Entity entity = newEntity.call(this, properties)
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
 
    public int getCurrentSize() {
        return members.size()
    }
}
