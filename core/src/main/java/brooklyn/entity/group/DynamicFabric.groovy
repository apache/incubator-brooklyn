package brooklyn.entity.group

import java.util.Collection
import java.util.Map
import java.util.concurrent.ExecutionException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.CustomAggregatingEnricher
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.ConfigurableEntityFactory
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.trait.Changeable
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.management.Task
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions

/**
 * When a dynamic fabric is started, it starts an entity in each of its locations. 
 * This entity will be the owner of each of the started entities. 
 */
public class DynamicFabric extends AbstractEntity implements Startable {
    private static final Logger logger = LoggerFactory.getLogger(DynamicFabric)

    public static final BasicAttributeSensor<Integer> FABRIC_SIZE = [ Integer, "fabric.size", "Fabric size" ]
    
    @SetFromFlag
    ConfigurableEntityFactory factory

    @SetFromFlag
    String displayNamePrefix
    @SetFromFlag
    String displayNameSuffix

    private CustomAggregatingEnricher fabricSizeEnricher

    /**
     * Instantiate a new DynamicFabric.
     * 
     * Valid properties are:
     * <ul>
     * <li>factory - an {@EntityFactory) (or {@link Closure}) that creates an {@link Entity},
     * typically a Cluster which implements {@link Startable}, taking the {@link Map}
     * of properties from this cluster as an argument. This property is mandatory.
     * </ul>
     *
     * @param properties the properties of the fabric and any new entity.
     * @param owner the entity that owns this fabric (optional)
     */
    public DynamicFabric(Map properties = [:], Entity owner = null) {
        super(properties, owner)

        fabricSizeEnricher = CustomAggregatingEnricher.newSummingEnricher(Changeable.GROUP_SIZE, FABRIC_SIZE)
        addEnricher(fabricSizeEnricher)
        
        setAttribute(SERVICE_UP, false)
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        Preconditions.checkNotNull locations, "locations must be supplied"
        Preconditions.checkArgument locations.size() >= 1, "One or more location must be supplied"
        this.locations.addAll(locations)
        
        Map<Entity, Task> tasks = [:]
        locations.each {
            Entity e = addCluster(it)
            // FIXME: this is a quick workaround to ensure that the location is available to any membership change
            //        listeners (notably AbstractDeoDnsService). A more robust mechanism is required; see ENGR-????
            //        for ideas and discussion.
            e.setLocations([it])
            if (e instanceof Startable) {
                Task task = e.invoke(Startable.START, [locations:[it]])
                tasks.put(e, task)
            }
        }
        waitForTasksOnStart(tasks)
        setAttribute(SERVICE_UP, true)
    }

    protected void waitForTasksOnStart(Map tasks) {
        // TODO Could do best-effort for waiting for remaining tasks, rather than failing on first?
        tasks.each { Entity entity, Task task ->
            try {
                task.get()
            } catch (ExecutionException e) {
                throw e.cause
            }
        }
    }
    
    public void stop() {
        Collection<Entity> stoppableChildren = ownedChildren.findAll({it instanceof Startable})
        Task invoke = Entities.invokeEffectorList(this, stoppableChildren, Startable.STOP)
        try {
	        invoke?.get()
        } catch (ExecutionException ee) {
            throw ee.cause
        }

        setAttribute(SERVICE_UP, false)
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }

    protected Map getCustomChildFlags() { [:] }
    
    protected Entity addCluster(Location location) {
        String locationName = location.getLocationProperty("displayName")?:location.name?:null;
        Map creation = [:]
        creation << getCustomChildFlags()
        if (displayNamePrefix || displayNameSuffix)
            creation.displayName = (displayNamePrefix?:"") + (locationName?:"unnamed") + (displayNameSuffix?:"")
        logger.info "Adding a cluster to {} in {} with properties {}", this, location, creation

        if (factory==null)
            throw new IllegalStateException("EntityFactory factory not supplied for $this")
        Entity entity = (factory in EntityFactoryForLocation ? ((EntityFactoryForLocation)factory).newFactoryForLocation(location) : factory).
                newEntity(creation, this)
                
        Preconditions.checkNotNull entity, "$this factory.newEntity call returned null"
        Preconditions.checkState entity instanceof Entity, "$this factory.newEntity call returned an object that is not an Entity"
        if (locationName) {
            if (entity.displayName==null)
                entity.displayName = entity.getClass().getSimpleName() +" ("+locationName+")";
            else if (!entity.displayName.contains(locationName)) 
                entity.displayName = entity.displayName +" ("+locationName+")";
        }
        if (entity.owner==null) entity.setOwner(this)
        
        fabricSizeEnricher.addProducer(entity)

        return entity
    }
}
