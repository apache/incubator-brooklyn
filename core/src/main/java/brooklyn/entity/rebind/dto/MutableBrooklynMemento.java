package brooklyn.entity.rebind.dto;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.mementos.TreeNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@JsonAutoDetect(fieldVisibility=Visibility.ANY, getterVisibility=Visibility.NONE)
public class MutableBrooklynMemento implements BrooklynMemento {

    private static final long serialVersionUID = -442895028005849060L;
    
    private final Collection<String> applicationIds = Sets.newLinkedHashSet();
    private final Collection<String> topLevelLocationIds = Sets.newLinkedHashSet();
    private final Map<String, EntityMemento> entities = Maps.newLinkedHashMap();
    private final Map<String, LocationMemento> locations = Maps.newLinkedHashMap();
    private final Map<String, PolicyMemento> policies = Maps.newLinkedHashMap();

    // FIXME Delete this if definitely not needed
    private final boolean maintainIntegrity = false;
    
    public MutableBrooklynMemento() {
    }
    
    public MutableBrooklynMemento(BrooklynMemento memento) {
        reset(memento);
    }
    
    public void reset(BrooklynMemento memento) {
        applicationIds.addAll(memento.getApplicationIds());
        topLevelLocationIds.addAll(memento.getTopLevelLocationIds());
        for (String entityId : memento.getEntityIds()) {
            entities.put(entityId, checkNotNull(memento.getEntityMemento(entityId), entityId));
        }
        for (String locationId : memento.getLocationIds()) {
            locations.put(locationId, checkNotNull(memento.getLocationMemento(locationId), locationId));
        }
    }

    public void updateEntityMemento(EntityMemento memento) {
        updateEntityMementos(ImmutableSet.of(memento));
    }
    
    public void updateLocationMemento(LocationMemento memento) {
        updateLocationMementos(ImmutableSet.of(memento));
    }
    
    public void updatePolicyMemento(PolicyMemento memento) {
        updatePolicyMementos(ImmutableSet.of(memento));
    }
    
    public void updateEntityMementos(Collection<EntityMemento> mementos) {
        // TODO Could check parents exist etc before making modifications, rather than throwing to leave in inconsistent state!
        
        for (EntityMemento memento : mementos) {
            entities.put(memento.getId(), memento);
            
            if (memento.getParent() == null) {
                // FIXME assume it's an app?!
                applicationIds.add(memento.getId());
            }
        }
        
        // Maintains referential integrity: if adding new mementos, ensure parent refs are updated
        if (maintainIntegrity) {
            for (EntityMemento entityMemento : mementos) {
                String entityId = entityMemento.getId();
                String parentId = entityMemento.getParent();
                EntityMemento parentMemento = entities.get(parentId);
                
                if (parentId == null) continue;
                if (parentMemento == null) continue; // rely on subsequent validation (or more mods being made)
                if (!parentMemento.getChildren().contains(entityId)) {
                    EntityMemento updatedParent = BasicEntityMemento.builder().from(parentMemento).addChild(entityId).build();
                    entities.put(parentId, updatedParent);
                }
            }
        }
    }
    
    public void updateLocationMementos(Collection<LocationMemento> mementos) {
        for (LocationMemento locationMemento : mementos) {
            locations.put(locationMemento.getId(), locationMemento);
            
            if (locationMemento.getParent() == null) {
                topLevelLocationIds.add(locationMemento.getId());
            }
        }
        
        // Maintains referential integrity: if adding new mementos, ensure parent refs are updated
        if (maintainIntegrity) {
            for (LocationMemento memento : mementos) {
                String locationId = memento.getId();
                String parentId = memento.getParent();
                LocationMemento parentMemento = locations.get(parentId);
                
                if (parentId == null) continue;
                if (parentMemento == null) continue; // rely on subsequent validation (or more mods being made)
                if (!parentMemento.getChildren().contains(locationId)) {
                    LocationMemento updatedParent = BasicLocationMemento.builder().from(parentMemento).addChild(locationId).build();
                    locations.put(parentId, updatedParent);
                }
            }
        }
    }
    
    public void updatePolicyMementos(Collection<PolicyMemento> mementos) {
        for (PolicyMemento memento : mementos) {
            policies.put(memento.getId(), memento);
        }
    }
    
    /**
     * Removes the entities with the given ids, and for each entity also removes all descendents.
     */
    public void removeEntities(Collection<String> ids) {
        List<EntityMemento> removedMementos = Lists.newArrayList();
        
        for (String id : ids) {
            EntityMemento memento = entities.get(id);
            Collection<String> removedIds = removeNodeAndDescendents(id, entities);
            if (memento != null) {
                removedMementos.add(memento);
            }
            applicationIds.removeAll(removedIds);
        }
        
        // Maintains referential integrity: for parent of each removed entity, remove it as a child
        if (maintainIntegrity) {
            for (EntityMemento entityMemento : removedMementos) {
                String entityId = entityMemento.getId();
                String parentId = entityMemento.getParent();
                EntityMemento parentMemento = entities.get(parentId);
                
                if (parentId == null) continue;
                if (parentMemento == null) continue;
                if (parentMemento.getChildren().contains(entityId)) {
                    EntityMemento updatedParent = BasicEntityMemento.builder().from(parentMemento).removeChild(entityId).build();
                    entities.put(parentId, updatedParent);
                }
            }
        }
    }
    
    /**
     * Removes the entities with the given ids, and for each entity also removes all descendents.
     */
    public void removeLocations(Collection<String> ids) {
        List<LocationMemento> removedMementos = Lists.newArrayList();
        
        for (String id : ids) {
            LocationMemento memento = locations.get(id);
            Collection<String> removedIds = removeNodeAndDescendents(id, locations);
            if (memento != null) {
                removedMementos.add(memento);
            }
            topLevelLocationIds.removeAll(removedIds);
        }
        
        // Maintains referential integrity: for parent of each removed location, remove it as a child
        if (maintainIntegrity) {
            for (LocationMemento locationMemento : removedMementos) {
                String locationyId = locationMemento.getId();
                String parentId = locationMemento.getParent();
                LocationMemento parentMemento = locations.get(parentId);
                
                if (parentId == null) continue;
                if (parentMemento == null) continue;
                if (parentMemento.getChildren().contains(locationyId)) {
                    LocationMemento updatedParent = BasicLocationMemento.builder().from(parentMemento).removeChild(locationyId).build();
                    locations.put(parentId, updatedParent);
                }
            }
        }
    }

    /**
     * Removes the entities with the given ids, and for each entity also removes all descendents.
     */
    public void removePolicies(Collection<String> ids) {
        for (String id : ids) {
            policies.remove(id);
        }
    }

    private <T extends TreeNode> Collection<String> removeNodeAndDescendents(String id, Map<String, T> nodes) {
        List<String> result = Lists.newArrayList();
        result.add(id);
        
        T memento = nodes.remove(id);
        if (memento == null) {
            return result;
        }
        
        Deque<String> tovisit = new LinkedList<String>();
        
        tovisit.addAll(memento.getChildren());
        
        while (tovisit.size() > 0) {
            String currentId = tovisit.pop();
            T currentMemento = nodes.remove(currentId);
            result.add(currentId);
            if (currentMemento != null) {
                tovisit.addAll(currentMemento.getChildren());
            }
        }
        
        return result;
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
    public PolicyMemento getPolicyMemento(String id) {
        return policies.get(id);
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
    public Collection<String> getPolicyIds() {
        return Collections.unmodifiableSet(policies.keySet());
    }
    
    @Override
    public Collection<String> getTopLevelLocationIds() {
        return Collections.unmodifiableCollection(topLevelLocationIds);
    }

    @Override
    public Map<String, EntityMemento> getEntityMementos() {
        return ImmutableMap.copyOf(entities);
    }

    @Override
    public Map<String, LocationMemento> getLocationMementos() {
        return ImmutableMap.copyOf(locations);
    }

    @Override
    public Map<String, PolicyMemento> getPolicyMementos() {
        return ImmutableMap.copyOf(policies);
    }
}
