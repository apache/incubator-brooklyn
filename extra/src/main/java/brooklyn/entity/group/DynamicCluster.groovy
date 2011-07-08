package brooklyn.entity.group

import brooklyn.entity.trait.Resizable
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import java.util.concurrent.Future
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.Entity
import com.google.common.base.Preconditions
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 */
public class DynamicCluster extends AbstractGroup implements Startable, Resizable {

    private static final Logger logger = LoggerFactory.getLogger(DynamicCluster)

    private Closure<Entity> newEntity;
    private int initialSize;
    private Location location;

    /**
     * Instantiate a new DynamicCluster. Valid properties are:
     * * template: an @{link Entity} that implements @{link Startable} that will be the template for nodes in the cluster.
     * * initialSize: an @{link Integer} that is the number of nodes to start when the cluster's @{link start()} method is
     * called.
     * @param properties the properties of the new entity.
     * @param owner the entity that owns this cluster (optional)
     */
    public DynamicCluster(Map properties = [:], Entity owner = null) {
        super(properties, owner)

        Preconditions.checkArgument properties.containsKey('newEntity'), "'newEntity' property is mandatory"
        Preconditions.checkArgument properties.get('newEntity') instanceof Closure, "'newEntity' must be a closure"
        newEntity = properties.remove('newEntity')

        Preconditions.checkArgument properties.containsKey('initialSize'), "'initialSize' property is mandatory"
        Preconditions.checkArgument properties.get('initialSize') instanceof Integer, "'initialSize' property must be an integer"
        initialSize = properties.remove('initialSize')
    }

    void start(Collection<? extends Location> locations) {
        Preconditions.checkNotNull locations, "locations must be supplied"
        Preconditions.checkArgument locations.size() == 1, "Exactly one location must be supplied"
        location = locations.first()
    }

    List<Future<?>> resize(int desiredSize) {
        List<Future<?>> outcomes = []

        int delta = desiredSize - currentSize
        logger.info "Resize from {} to {}; delta = {}", currentSize, desiredSize, delta

        if (delta > 0) {
            while (delta --> 0) outcomes.add(addNode())
        } else if (delta < 0) {
            throw new Exception("not implemented")
        }

        return outcomes
    }

    protected Future<?> addNode() {
        logger.info "Adding a node"
        Entity e = newEntity.call()
        Preconditions.checkState e != null, "newEntity call returned null"
        Preconditions.checkState e instanceof Entity, "newEntity call returned an object that is not an Entity"
        Preconditions.checkState e instanceof Startable, "newEntity call returned an object that is not Startable"

        e.owner = this
        return e.getExecutionContext().submit({e.start([location])})
    }

    int getCurrentSize() {
        return ownedChildren.size()
    }
}
