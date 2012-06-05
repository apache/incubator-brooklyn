package brooklyn.entity.group;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import groovy.lang.Closure;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.CustomAggregatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * When a dynamic fabric is started, it starts an entity in each of its locations. 
 * This entity will be the owner of each of the started entities. 
 */
public class DynamicFabric extends AbstractEntity implements Startable, Fabric {
    private static final Logger logger = LoggerFactory.getLogger(DynamicFabric.class);

    public static final BasicAttributeSensor<Integer> FABRIC_SIZE = new BasicAttributeSensor<Integer>(Integer.class, "fabric.size", "Fabric size");
    
    @SetFromFlag
    ConfigurableEntityFactory factory;

    @SetFromFlag
    String displayNamePrefix;
    @SetFromFlag
    String displayNameSuffix;

    private CustomAggregatingEnricher fabricSizeEnricher;

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
    public DynamicFabric(Map properties, Entity owner) {
        super(properties, owner);

        fabricSizeEnricher = CustomAggregatingEnricher.newSummingEnricher(Changeable.GROUP_SIZE, FABRIC_SIZE);
        addEnricher(fabricSizeEnricher);
        
        setAttribute(SERVICE_UP, false);
    }
    public DynamicFabric(Map properties) {
        this (properties, null);
    }
    public DynamicFabric(Entity owner) {
        this(Collections.emptyMap(), owner);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        Preconditions.checkNotNull(locations, "locations must be supplied");
        Preconditions.checkArgument(locations.size() >= 1, "One or more location must be supplied");
        this.getLocations().addAll(locations);
        
        Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();
        for (Location it : locations) {
            Entity e = addCluster(it);
            // FIXME: this is a quick workaround to ensure that the location is available to any membership change
            //        listeners (notably AbstractDeoDnsService). A more robust mechanism is required; see ENGR-????
            //        for ideas and discussion.
            e.getLocations().add(it);
            if (e instanceof Startable) {
                Task task = e.invoke(Startable.START, ImmutableMap.of("locations", ImmutableList.of(it)));
                tasks.put(e, task);
            }
        }
        waitForTasksOnStart(tasks);
        setAttribute(SERVICE_UP, true);
    }

    protected void waitForTasksOnStart(Map<Entity, Task<?>> tasks) {
        // TODO Could do best-effort for waiting for remaining tasks, rather than failing on first?

        for (Map.Entry<Entity, Task<?>> entry: tasks.entrySet()) {
            try {
                entry.getValue().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw Throwables.propagate(e);
            } catch (ExecutionException ee) {
                throw Throwables.propagate(ee.getCause());
            }
        }
    }
    
    public void stop() {
        Iterable<Entity> stoppableChildren = Iterables.filter(getOwnedChildren(), Predicates.instanceOf(Startable.class));
        Task invoke = Entities.invokeEffectorList(this, stoppableChildren, Startable.STOP);
        try {
	        if (invoke != null) invoke.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        } catch (ExecutionException ee) {
            throw Throwables.propagate(ee.getCause());
        }

        setAttribute(SERVICE_UP, false);
    }

    public void restart() {
        throw new UnsupportedOperationException();
    }

    protected Map getCustomChildFlags() { return Collections.emptyMap(); }
    
    protected Entity addCluster(Location location) {
        String locationName = elvis(location.getLocationProperty("displayName"), location.getName(), null);
        Map creation = Maps.newLinkedHashMap();
        creation.putAll(getCustomChildFlags());
        if (truth(displayNamePrefix) || truth(displayNameSuffix)) {
            String displayName = "" + elvis(displayNamePrefix, "") + elvis(locationName, "unnamed") + elvis(displayNameSuffix,"");
            creation.put("displayName", displayName);
        }
        logger.info("Adding a cluster to {} in {} with properties {}", new Object[] {this, location, creation});

        if (factory==null)
            throw new IllegalStateException("EntityFactory factory not supplied for "+this);
        EntityFactory factoryToUse = (factory instanceof EntityFactoryForLocation) ? ((EntityFactoryForLocation)factory).newFactoryForLocation(location) : factory;
        Entity entity = factoryToUse.newEntity(creation, this);
                
        Preconditions.checkNotNull(entity, this+" factory.newEntity call returned null");
        Preconditions.checkState(entity instanceof Entity, this+" factory.newEntity call returned an object that is not an Entity");
        if (locationName != null) {
            if (entity.getDisplayName()==null)
                ((AbstractEntity)entity).setDisplayName(entity.getClass().getSimpleName() +" ("+locationName+")");
            else if (!entity.getDisplayName().contains(locationName)) 
                ((AbstractEntity)entity).setDisplayName(entity.getDisplayName() +" ("+locationName+")");
        }
        if (entity.getOwner()==null) entity.setOwner(this);
        
        fabricSizeEnricher.addProducer(entity);

        return entity;
    }
}
