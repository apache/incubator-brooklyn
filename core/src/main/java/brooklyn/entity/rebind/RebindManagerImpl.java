package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.MutableMap;
import brooklyn.util.javalang.Reflections;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RebindManagerImpl implements RebindManager {

    public static final Logger LOG = LoggerFactory.getLogger(RebindManagerImpl.class);

    private final ManagementContext managementContext;

    public RebindManagerImpl(ManagementContext managementContext) {
        this.managementContext = managementContext;
    }
    
    @Override
    public BrooklynMemento getMemento() {
        return new BrooklynMementoImpl(managementContext, managementContext.getApplications());
    }

    @Override
    public List<Application> rebind(final BrooklynMemento memento) {
        return rebind(memento, getClass().getClassLoader());
    }
    
    @Override
    public List<Application> rebind(final BrooklynMemento memento, ClassLoader classLoader) {
        Reflections reflections = new Reflections(classLoader);
        Map<String,Rebindable> entities = Maps.newLinkedHashMap();
        Map<String,RebindableLocation> locations = Maps.newLinkedHashMap();
        List<Application> result = Lists.newArrayList();
        
        final RebindContextImpl rebindContext = new RebindContextImpl(memento);

        // Instantiate locations
        LOG.info("RebindManager constructing locations: {}", memento.getLocationIds());
        for (String locationId : memento.getLocationIds()) {
            LocationMemento locationMemento = checkNotNull(memento.getLocationMemento(locationId), "memento of "+locationId);
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating location {}", memento);
            
            RebindableLocation location = newLocation(locationMemento, reflections);
            location.getRebindSupport().reconstruct(locationMemento);
            
            locations.put(locationId, location);
            rebindContext.registerLocation(locationId, (Location) location);
        }
        
        // Rebind locations
        LOG.info("RebindManager rebinding locations: {}", memento.getLocationIds());
        for (String locationId : memento.getLocationIds()) {
            Iterable<String> topLevelLocationIds = Iterables.filter(memento.getLocationIds(), new Predicate<String>() {
                    @Override public boolean apply(String input) {
                        LocationMemento locationMemento = memento.getLocationMemento(input);
                        return locationMemento.getParent() == null;
                    }});
            
            for (String topLevelLocationId : topLevelLocationIds) {
                Location topLevelLocation = (Location) locations.get(topLevelLocationId);
                depthFirst(topLevelLocation, new Function<Location, Void>() {
                        @Override public Void apply(Location input) {
                            LocationMemento locationMemento = memento.getLocationMemento(input.getId());
                            if (LOG.isDebugEnabled()) LOG.debug("RebindManager rebinding location {}", memento);
                            
                            ((RebindableLocation)input).getRebindSupport().rebind(rebindContext, locationMemento);
                            return null;
                        }});
            }
        }
        
        // Instantiate entities
        LOG.info("RebindManager instantiating entities: {}", memento.getEntityIds());
        for (String entityId : memento.getEntityIds()) {
            EntityMemento entityMemento = checkNotNull(memento.getEntityMemento(entityId), "memento of "+entityId);
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating entity {}", memento);
            
            Rebindable entity = newEntity(entityMemento, reflections);
            entity.getRebindSupport().reconstruct(entityMemento);
            
            entities.put(entityId, entity);
            rebindContext.registerEntity(entityId, (Entity) entity);
        }
        
        // Rebind entities
        LOG.info("RebindManager rebinding entities for apps {}: {}", memento.getApplicationIds(), memento.getEntityIds());
        for (String appId : memento.getApplicationIds()) {
            Application app = (Application) entities.get(appId);
            result.add(app);
            depthFirst(app, new Function<Entity, Void>() {
                    @Override public Void apply(Entity input) {
                        EntityMemento entityMemento = memento.getEntityMemento(input.getId());
                        if (LOG.isDebugEnabled()) LOG.debug("RebindManager rebinding entity {}", memento);
                        
                        ((Rebindable)input).getRebindSupport().rebind(rebindContext, entityMemento);
                        return null;
                    }});
        }
        
        return result;
    }
    
    private Rebindable newEntity(EntityMemento memento, Reflections reflections) {
        String entityId = memento.getId();
        String entityType = checkNotNull(memento.getType(), "entityType of "+entityId);
        Class<?> entityClazz = reflections.loadClass(entityType);

        Map<String,Object> flags = Maps.newLinkedHashMap();
        flags.put("id", entityId);
        if (AbstractApplication.class.isAssignableFrom(entityClazz)) flags.put("mgmt", managementContext);
        
        // There are several possibilities for the constructor; find one that works.
        // Prefer passing in the flags because required for Application to set the management context
        // TODO Feels very hacky!
        return (Rebindable) invokeConstructor(reflections, entityClazz, new Object[] {flags}, new Object[] {flags, null}, new Object[] {null}, new Object[0]);
    }
    
    /**
     * Constructs a new location, passing to its constructor the location id and all of memento.getFlags().
     */
    private RebindableLocation newLocation(LocationMemento memento, Reflections reflections) {
        String locationId = memento.getId();
        String locationType = checkNotNull(memento.getType(), "locationType of "+locationId);
        Class<?> locationClazz = reflections.loadClass(locationType);

        Map<String, Object> flags = MutableMap.<String, Object>builder()
        		.put("id", locationId)
        		.putAll(memento.getFlags())
        		.removeAll(memento.getLocationReferenceFlags())
        		.build();

        return (RebindableLocation) invokeConstructor(reflections, locationClazz, new Object[] {flags});
    }

    private <T> T invokeConstructor(Reflections reflections, Class<T> clazz, Object[]... possibleArgs) {
        for (Object[] args : possibleArgs) {
            Constructor<T> constructor = Reflections.findCallabaleConstructor(clazz, args);
            if (constructor != null) {
                return reflections.loadInstance(constructor, args);
            }
        }
        throw new IllegalStateException("Cannot instantiate instance of type "+clazz+"; expected constructor signature not found");
    }
    
    private void depthFirst(Entity entity, Function<Entity, Void> visitor) {
        Deque<Entity> tovisit = new ArrayDeque<Entity>();
        tovisit.addFirst(entity);
        
        while (tovisit.size() > 0) {
            Entity current = tovisit.pop();
            visitor.apply(current);
            for (Entity child : current.getOwnedChildren()) {
                tovisit.push(child);
            }
        }
    }
    
    private void depthFirst(Location location, Function<Location, Void> visitor) {
        Deque<Location> tovisit = new ArrayDeque<Location>();
        tovisit.addFirst(location);
        
        while (tovisit.size() > 0) {
            Location current = tovisit.pop();
            visitor.apply(current);
            for (Location child : current.getChildLocations()) {
            	if (child != null) {
            		tovisit.push(child);
            	}
            }
        }
    }
    
    private <K,V> Map<K,V> union(Map<? extends K, ? extends V> m1, Map<? extends K, ? extends V> m2) {
    	return MutableMap.<K,V>builder().putAll(m1).putAll(m2).build();
    }
}
