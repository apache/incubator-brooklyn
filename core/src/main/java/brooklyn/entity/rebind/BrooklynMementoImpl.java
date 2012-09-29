package brooklyn.entity.rebind;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BrooklynMementoImpl implements BrooklynMemento, Serializable {

    // FIXME There are race conditions for constructing the memento while entities are modifying their parent-child relationships
    
    private static final long serialVersionUID = -5848083830410137654L;
    
    private final List<String> applicationIds = Lists.newArrayList();
    private final List<String> topLevelLocationIds = Lists.newArrayList();
    private final Map<String, EntityMemento> entities = Maps.newLinkedHashMap();
    private final Map<String, LocationMemento> locations = Maps.newLinkedHashMap();
    
    public BrooklynMementoImpl(ManagementContext managementContext, Collection<Application> applications) {
        for (Application app : applications) {
            applicationIds.add(app.getId());
        }
        for (Entity entity : managementContext.getEntities()) {
            entities.put(entity.getId(), ((Rebindable)entity).getRebindSupport().getMemento());
            
            for (Location location : entity.getLocations()) {
                if (!locations.containsKey(location.getId())) {
                	for (Location locationInHierarchy : findLocationsInHierarchy(location)) {
                		locations.put(locationInHierarchy.getId(), ((RebindableLocation)locationInHierarchy).getRebindSupport().getMemento());
                	}
                }
            }
        }
        for (LocationMemento memento : locations.values()) {
            if (memento.getParent() == null) {
                topLevelLocationIds.add(memento.getId());
            }
        }
        
        validateMemento();
    }

    private void validateMemento() {
        // TODO Could also validate integrity of entityReferenceAttributes and entityReferenceConfig
        
        // Ensure every entity's parent/children/locations exists
        for (Map.Entry<String,EntityMemento> entry : entities.entrySet()) {
            EntityMemento memento = entry.getValue();
            if (memento.getParent() != null && !entities.containsKey(memento.getParent())) {
                throw new IllegalStateException("Parent entity "+memento.getParent()+" missing, for entity "+memento);
            }
            for (String child : memento.getChildren()) {
                if (child == null) {
                    throw new IllegalStateException("Null child entity, for entity "+memento);
                }
                if (!entities.containsKey(child)) {
                    throw new IllegalStateException("Child entity "+child+" missing, for entity "+memento);
                }
            }
            for (String location : memento.getLocations()) {
                if (!locations.containsKey(location)) {
                    throw new IllegalStateException("Location "+location+" missing, for entity "+memento);
                }
            }
        }
        
        // Ensure every location's parent/children exists
        for (Map.Entry<String,LocationMemento> entry : locations.entrySet()) {
            LocationMemento memento = entry.getValue();
            if (memento.getParent() != null && !locations.containsKey(memento.getParent())) {
                throw new IllegalStateException("Parent location "+memento.getParent()+" missing, for location "+memento);
            }
            for (String child : memento.getChildren()) {
                if (child == null) {
                    throw new IllegalStateException("Null child location, for location "+memento);
                }
                if (!locations.containsKey(child)) {
                    throw new IllegalStateException("Child location  "+child+" missing, for location "+memento);
                }
            }
        }
    }
    
    @Override
    public EntityMemento getEntityMemento(String id) {
        return entities.get(id);
    }

    @Override
    public LocationMemento getLocationMemento(String id) {
        return locations.get(id);
    }
    
    @Override
    public Collection<String> getApplicationIds() {
        return ImmutableList.copyOf(applicationIds);
    }

    @Override
    public Collection<String> getEntityIds() {
        return Collections.unmodifiableSet(entities.keySet());
    }
    
    @Override
    public Collection<String> getLocationIds() {
        return Collections.unmodifiableSet(locations.keySet());
    }
    
    @Override
    public Collection<String> getTopLevelLocationIds() {
        return Collections.unmodifiableList(topLevelLocationIds);
    }

    private Collection<Location> findLocationsInHierarchy(Location root) {
    	Set<Location> result = Sets.newLinkedHashSet();
    	
        Deque<Location> tovisit = new ArrayDeque<Location>();
        tovisit.addFirst(root);
        
        while (tovisit.size() > 0) {
            Location current = tovisit.pop();
            result.add(current);
            for (Location child : current.getChildLocations()) {
            	if (child != null) {
            		tovisit.push(child);
            	}
            }
        }
        
        return result;
    }
}
