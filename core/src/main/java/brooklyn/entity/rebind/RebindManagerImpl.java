package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        checkNotNull(memento, "memento");
        checkNotNull(classLoader, "classLoader");
        
        Reflections reflections = new Reflections(classLoader);
        Map<String,Rebindable> entities = Maps.newLinkedHashMap();
        Map<String,RebindableLocation> locations = Maps.newLinkedHashMap();
        
        final RebindContextImpl rebindContext = new RebindContextImpl(memento, classLoader);

        // Instantiate locations
        LOG.info("RebindManager instantiating locations: {}", memento.getLocationIds());
        for (String locationId : memento.getLocationIds()) {
            LocationMemento locationMemento = checkNotNull(memento.getLocationMemento(locationId), "memento of "+locationId);
            if (LOG.isTraceEnabled()) LOG.trace("RebindManager instantiating location {}", memento);
            
            RebindableLocation location = newLocation(locationMemento, reflections);
            locations.put(locationId, location);
            rebindContext.registerLocation(locationId, (Location) location);
        }
        
        // Reconstruct locations
        LOG.info("RebindManager constructing locations: {}", memento.getLocationIds());
        depthFirst(memento, rebindContext, new LocationVisitor("constructing") {
                @Override public void visit(Location location, LocationMemento memento) {
                    ((RebindableLocation)location).getRebindSupport().reconstruct(rebindContext, memento);
                }});

        // Rebind locations
        LOG.info("RebindManager rebinding locations");
        depthFirst(memento, rebindContext, new LocationVisitor("rebinding") {
            @Override public void visit(Location location, LocationMemento memento) {
                ((RebindableLocation)location).getRebindSupport().rebind(rebindContext, memento);
            }});
        
        // Instantiate entities
        LOG.info("RebindManager instantiating entities");
        for (String entityId : memento.getEntityIds()) {
            EntityMemento entityMemento = checkNotNull(memento.getEntityMemento(entityId), "memento of "+entityId);
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating entity {}", memento);
            
            Rebindable entity = newEntity(entityMemento, reflections);
            entities.put(entityId, entity);
            rebindContext.registerEntity(entityId, (Entity) entity);
        }
        
        // Reconstruct entities
        LOG.info("RebindManager constructing entities");
        depthFirst(memento, rebindContext, new EntityVisitor("constructing") {
                @Override public void visit(Entity entity, EntityMemento memento) {
                    ((Rebindable)entity).getRebindSupport().reconstruct(rebindContext, memento);
                }});

        // Rebind entities
        LOG.info("RebindManager rebinding entities");
        depthFirst(memento, rebindContext, new EntityVisitor("rebinding") {
            @Override public void visit(Entity entity, EntityMemento memento) {
                ((Rebindable)entity).getRebindSupport().rebind(rebindContext, memento);
            }});
        
        // Manage the top-level apps (causing everything under them to become managed)
        LOG.info("RebindManager managing entities");
        for (String appId : memento.getApplicationIds()) {
            managementContext.manage((Application)rebindContext.getEntity(appId));
        }
        
        LOG.info("RebindManager notifying entities of all being managed");
        depthFirst(memento, rebindContext, new EntityVisitor("managed") {
            @Override public void visit(Entity entity, EntityMemento memento) {
                ((Rebindable)entity).getRebindSupport().managed();
            }});
        
        // Return the top-level applications
        List<Application> apps = Lists.newArrayList();
        for (String appId : memento.getApplicationIds()) {
            apps.add((Application)rebindContext.getEntity(appId));
        }
        return apps;
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
                constructor.setAccessible(true);
                return reflections.loadInstance(constructor, args);
            }
        }
        throw new IllegalStateException("Cannot instantiate instance of type "+clazz+"; expected constructor signature not found");
    }
    
    private void visitAllLocations(BrooklynMemento memento, RebindContext rebindContext, LocationVisitor visitor) {
        for (String id : memento.getLocationIds()) {
            Location loc = rebindContext.getLocation(id);
            LocationMemento locMemento = memento.getLocationMemento(id);
            
            if (loc != null) {
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager {} location {}", visitor.getActivityName(), memento);
                visitor.visit(loc, locMemento);
            } else {
                LOG.warn("No location found for id {}; so not {}", id, visitor.getActivityName());
            }
        }
    }

    private void depthFirst(BrooklynMemento memento, RebindContext rebindContext, LocationVisitor visitor) {
        List<String> orderedIds = depthFirstLocationOrder(memento);

        for (String id : orderedIds) {
            Location loc = rebindContext.getLocation(id);
            LocationMemento locMemento = memento.getLocationMemento(id);
            
            if (loc != null) {
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager {} location {}", visitor.getActivityName(), memento);
                visitor.visit(loc, locMemento);
            } else {
                LOG.warn("No location found for id {}; so not {}", id, visitor.getActivityName());
            }
        }
    }

    private void depthFirst(BrooklynMemento memento, RebindContext rebindContext, EntityVisitor visitor) {
        List<String> orderedIds = depthFirstEntityOrder(memento);

        for (String id : orderedIds) {
            Entity loc = rebindContext.getEntity(id);
            EntityMemento locMemento = memento.getEntityMemento(id);
            
            if (loc != null) {
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager {} entity {}", visitor.getActivityName(), memento);
                visitor.visit(loc, locMemento);
            } else {
                LOG.warn("No entity found for id {}; so not {}", id, visitor.getActivityName());
            }
        }
    }

    private List<String> depthFirstLocationOrder(BrooklynMemento memento) {
        Set<String> visited = new HashSet<String>();
        Deque<String> tovisit = new ArrayDeque<String>();
        List<String> result = new ArrayList<String>(memento.getLocationIds().size());
        
        tovisit.addAll(memento.getTopLevelLocationIds());
        
        while (tovisit.size() > 0) {
            String current = tovisit.pop();
            LocationMemento locationMemento = memento.getLocationMemento(current);
            
            if (locationMemento == null) {
                LOG.warn("No memento for location id {}", current);
                
            } else if (visited.add(current)) {
                result.add(current);
                for (String child : locationMemento.getChildren()) {
                    if (child == null) {
                        LOG.warn("Null child location id in location {}", locationMemento);
                    } else if (memento.getLocationMemento(child) == null) {
                        LOG.warn("Unknown child location id {} in location {}", child, locationMemento);
                    } else {
                        tovisit.push(child);
                    }
                }
            } else {
                LOG.warn("Cycle detected in locations (id="+current+")");
            }
        }
        
        return result;
    }

    private List<String> depthFirstEntityOrder(BrooklynMemento memento) {
        Set<String> visited = new HashSet<String>();
        Deque<String> tovisit = new ArrayDeque<String>();
        List<String> result = new ArrayList<String>(memento.getEntityIds().size());
        
        tovisit.addAll(memento.getApplicationIds());
        
        while (tovisit.size() > 0) {
            String current = tovisit.pop();
            EntityMemento entityMemento = memento.getEntityMemento(current);
            
            if (entityMemento == null) {
                LOG.warn("No memento for entity id {}", current);
                
            } else if (visited.add(current)) {
                result.add(current);
                for (String child : entityMemento.getChildren()) {
                    if (child == null) {
                        LOG.warn("Null child entity id in entity {}", entityMemento);
                    } else if (memento.getEntityMemento(child) == null) {
                        LOG.warn("Unknown child entity id {} in entity {}", child, entityMemento);
                    } else {
                        tovisit.push(child);
                    }
                }
            } else {
                LOG.warn("Cycle detected in entity hierarchy (id="+current+")");
            }
        }
        
        return result;
    }

    private static abstract class LocationVisitor {
        private final String activityName;

        public LocationVisitor(String activityName) {
            this.activityName = activityName;
        }
        public abstract void visit(Location location, LocationMemento memento);
        
        public String getActivityName() {
            return activityName;
        }
    }
    
    private static abstract class EntityVisitor {
        private final String activityName;

        public EntityVisitor(String activityName) {
            this.activityName = activityName;
        }
        public abstract void visit(Entity location, EntityMemento memento);
        
        public String getActivityName() {
            return activityName;
        }
    }
    
    private <K,V> Map<K,V> union(Map<? extends K, ? extends V> m1, Map<? extends K, ? extends V> m2) {
    	return MutableMap.<K,V>builder().putAll(m1).putAll(m2).build();
    }
}
