package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoPersister.Delta;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.TreeNode;
import brooklyn.util.MutableMap;
import brooklyn.util.javalang.Reflections;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RebindManagerImpl implements RebindManager {

    public static final Logger LOG = LoggerFactory.getLogger(RebindManagerImpl.class);

    private final ManagementContext managementContext;

    private final ChangeListener changeListener;
    
    private volatile BrooklynMementoPersister persister;

    private volatile boolean running = true;
    
    public RebindManagerImpl(ManagementContext managementContext) {
        this.managementContext = managementContext;
        this.changeListener = new CheckpointingChangeListener();
    }
    
    @Override
    public void setPersister(BrooklynMementoPersister val) {
        if (persister != null && persister != val) {
            throw new IllegalStateException("Dynamically changing persister is not supported: old="+persister+"; new="+val);
        }
        this.persister = checkNotNull(val, "persister");
    }

    public void stop() {
        running = false;
    }
    
    @Override
    public ChangeListener getChangeListener() {
        return changeListener;
    }
    
    @Override
    public BrooklynMemento getMemento() {
        return BrooklynMementos.newMemento(managementContext);
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
        depthFirst(memento, rebindContext, new LocationVisitor("reconstructing") {
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
        depthFirst(memento, rebindContext, new EntityVisitor("reconstructing") {
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

    private void depthFirst(BrooklynMemento memento, RebindContext rebindContext, LocationVisitor visitor) {
        List<String> orderedIds = depthFirstOrder(memento.getTopLevelLocationIds(), memento.getLocationMementos());

        for (String id : orderedIds) {
            Location loc = rebindContext.getLocation(id);
            LocationMemento locMemento = memento.getLocationMemento(id);
            
            if (loc != null) {
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager {} location {}", visitor.getActivityName(), locMemento);
                visitor.visit(loc, locMemento);
            } else {
                LOG.warn("No location found for id {}; so not {}", id, visitor.getActivityName());
            }
        }
    }

    private void depthFirst(BrooklynMemento memento, RebindContext rebindContext, EntityVisitor visitor) {
        List<String> orderedIds = depthFirstOrder(memento.getApplicationIds(), memento.getEntityMementos());

        for (String id : orderedIds) {
            Entity entity = rebindContext.getEntity(id);
            EntityMemento entityMemento = memento.getEntityMemento(id);
            
            if (entity != null) {
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager {} entity {}", visitor.getActivityName(), entityMemento);
                visitor.visit(entity, entityMemento);
            } else {
                LOG.warn("No entity found for id {}; so not {}", id, visitor.getActivityName());
            }
        }
    }

    private List<String> depthFirstOrder(Collection<String> roots, Map<String, ? extends TreeNode> nodes) {
        Set<String> visited = new HashSet<String>();
        Deque<String> tovisit = new ArrayDeque<String>();
        List<String> result = new ArrayList<String>(nodes.size());
        
        tovisit.addAll(roots);
        
        while (tovisit.size() > 0) {
            String currentId = tovisit.pop();
            TreeNode node = nodes.get(currentId);
            
            if (node == null) {
                LOG.warn("No memento for id {}", currentId);
                
            } else if (visited.add(currentId)) {
                result.add(currentId);
                for (String childId : node.getChildren()) {
                    if (childId == null) {
                        LOG.warn("Null child entity id in entity {}", node);
                    } else if (!nodes.containsKey(childId)) {
                        LOG.warn("Unknown child id {} in {}", childId, node);
                    } else {
                        tovisit.push(childId);
                    }
                }
            } else {
                LOG.warn("Cycle detected in hierarchy (id="+currentId+")");
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
        public abstract void visit(Entity entity, EntityMemento memento);
        
        public String getActivityName() {
            return activityName;
        }
    }
    
    private <K,V> Map<K,V> union(Map<? extends K, ? extends V> m1, Map<? extends K, ? extends V> m2) {
    	return MutableMap.<K,V>builder().putAll(m1).putAll(m2).build();
    }
    
    private static class DeltaImpl implements Delta {
        Collection<LocationMemento> locations = Collections.emptyList();
        Collection<EntityMemento> entities = Collections.emptyList();
        Collection <String> removedLocationIds = Collections.emptyList();
        Collection <String> removedEntityIds = Collections.emptyList();
        
        @Override
        public Collection<LocationMemento> locationMementos() {
            return locations;
        }

        @Override
        public Collection<EntityMemento> entityMementos() {
            return entities;
        }

        @Override
        public Collection<String> removedLocationIds() {
            return removedLocationIds;
        }

        @Override
        public Collection<String> removedEntityIds() {
            return removedEntityIds;
        }
    }
    
    private class CheckpointingChangeListener implements ChangeListener {

        @Override
        public void onManaged(Entity entity) {
            if (running && persister != null) {
                // TODO Currently, we get an onManaged call for every entity (rather than just the root of a sub-tree)
                // Also, we'll be told about an entity before its children are officially managed.
                // So it's really the same as "changed".
                onChanged(entity);
            }
        }

        @Override
        public void onManaged(Location location) {
            if (running && persister != null) {
                onChanged(location);
            }
        }
        
        @Override
        public void onChanged(Entity entity) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.entities = ImmutableList.of(((Rebindable)entity).getRebindSupport().getMemento());

                // TODO Should we be told about locations in a different way?
                Map<String, LocationMemento> locations = Maps.newLinkedHashMap();
                for (Location location : entity.getLocations()) {
                    if (!locations.containsKey(location.getId())) {
                        for (Location locationInHierarchy : TreeUtils.findLocationsInHierarchy(location)) {
                            locations.put(locationInHierarchy.getId(), ((RebindableLocation)locationInHierarchy).getRebindSupport().getMemento());
                        }
                    }
                }
                delta.locations = locations.values();

                persister.delta(delta);
            }
        }
        
        @Override
        public void onUnmanaged(Entity entity) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.removedEntityIds = ImmutableList.of(entity.getId());
                persister.delta(delta);
            }
        }

        @Override
        public void onUnmanaged(Location location) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.removedLocationIds = ImmutableList.of(location.getId());
                persister.delta(delta);
            }
        }

        @Override
        public void onChanged(Location location) {
            if (running && persister != null) {
                DeltaImpl delta = new DeltaImpl();
                delta.locations = ImmutableList.of(((RebindableLocation)location).getRebindSupport().getMemento());
                persister.delta(delta);
            }
        }
    }
}
