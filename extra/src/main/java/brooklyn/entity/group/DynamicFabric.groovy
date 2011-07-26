package brooklyn.entity.group

import groovy.lang.Closure

import java.util.Collection
import java.util.Map
import java.util.concurrent.ExecutionException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.management.Task
import brooklyn.util.task.ParallelTask

import com.google.common.base.Preconditions

/**
 * When a dynamic fabric is started, it starts an entity in each of its locations. 
 * This entity will be the owner of each of the started entities. 
 */
public class DynamicFabric extends AbstractEntity implements Startable {
    private static final Logger logger = LoggerFactory.getLogger(DynamicFabric)

    Closure<Entity> newEntity
    int initialSize
    Map properties

    /**
     * Instantiate a new DynamicFabric.
     * 
     * Valid properties are:
     * <ul>
     * <li>newEntity - a {@link Closure} that creates an {@link Entity} that implements {@link Startable}, taking the {@link Map}
     * of properties from this cluster as an argument. This property is mandatory.
     * </ul>
     *
     * @param properties the properties of the cluster and any new entity.
     * @param owner the entity that owns this cluster (optional)
     */
    public DynamicFabric(Map properties = [:], Entity owner = null) {
        super(properties, owner)

        Preconditions.checkArgument properties.containsKey('newEntity'), "'newEntity' property is mandatory"
        Preconditions.checkArgument properties.get('newEntity') instanceof Closure, "'newEntity' must be a closure"
        newEntity = properties.remove('newEntity')
        
        this.properties = properties

        setAttribute(SERVICE_UP, false)
    }

    public void start(Collection<Location> locations) {
        Preconditions.checkNotNull locations, "locations must be supplied"
        Preconditions.checkArgument locations.size() >= 1, "One or more location must be supplied"
        this.locations.addAll(locations)
        
        Collection<Task> tasks = locations.collect {
            Entity e = addCluster(it)
            // FIXME: this is a quick workaround to ensure that the location is available to any membership change
            //        listeners (notably AbstractDeoDnsService). A more robust mechanism is required; see ENGR-????
            //        for ideas and discussion.
            e.setLocations([it])
            Task task = e.invoke(Startable.START, [locations:[it]])
            return task
        }

        Task invoke = new ParallelTask(tasks)
        executionContext.submit(invoke)

        if (invoke) {
            try {
                invoke.get()
            } catch (ExecutionException ee) {
                throw ee.cause
            }
        }

        setAttribute(SERVICE_UP, true)
    }

    public void stop() {
        Task invoke = invokeEffectorList(ownedChildren, Startable.STOP)
        try {
            invoke.get()
        } catch (ExecutionException ee) {
            throw ee.cause
        }

        setAttribute(SERVICE_UP, false)
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }

    protected Entity addCluster(Location location) {
        Map creation = [:]
        creation << properties
        creation.displayName = (properties.displayNamePrefix?:"") + (location.getLocationProperty("displayName")?:location.name?:"unnamed") + (properties.displayNameSuffix?:"")
        logger.info "Adding a cluster to {} with properties {}", id, creation

        Entity entity = newEntity.call(creation)
        if (entity.owner == null) addOwnedChild(entity)
        Preconditions.checkNotNull entity, "newEntity call returned null"
        Preconditions.checkState entity instanceof Entity, "newEntity call returned an object that is not an Entity"
        Preconditions.checkState entity instanceof Startable, "newEntity call returned an object that is not Startable"

        return entity
    }
}
