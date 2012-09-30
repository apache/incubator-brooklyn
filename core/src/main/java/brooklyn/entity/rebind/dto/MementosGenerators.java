package brooklyn.entity.rebind.dto;

import java.lang.reflect.Modifier;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.rebind.MementoTransformer;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.entity.rebind.RebindableLocation;
import brooklyn.entity.rebind.TreeUtils;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.flags.FlagUtils;

public class MementosGenerators {

    private MementosGenerators() {}
    
    /**
     * Walks the contents of a ManagementContext, to create a corresponding memento.
     */
    public static BrooklynMemento newBrooklynMemento(ManagementContext managementContext) {
        BrooklynMementoImpl.Builder builder = BrooklynMementoImpl.builder();
                
        for (Application app : managementContext.getApplications()) {
            builder.applicationIds.add(app.getId());
        }
        for (Entity entity : managementContext.getEntities()) {
            builder.entities.put(entity.getId(), ((Rebindable)entity).getRebindSupport().getMemento());
            
            for (Location location : entity.getLocations()) {
                if (!builder.locations.containsKey(location.getId())) {
                    for (Location locationInHierarchy : TreeUtils.findLocationsInHierarchy(location)) {
                        builder.locations.put(locationInHierarchy.getId(), ((RebindableLocation)locationInHierarchy).getRebindSupport().getMemento());
                    }
                }
            }
        }
        for (LocationMemento memento : builder.locations.values()) {
            if (memento.getParent() == null) {
                builder.topLevelLocationIds.add(memento.getId());
            }
        }

        BrooklynMemento result = builder.build();
        MementoValidators.validateMemento(result);
        return result;
    }
    
    /**
     * Inspects an entity to create a corresponding memento.
     */
    public static EntityMemento newEntityMemento(Entity entity) {
        return newEntityMementoBuilder(entity).build();
    }
    
    public static BasicEntityMemento.Builder newEntityMementoBuilder(Entity entity) {
        BasicEntityMemento.Builder builder = BasicEntityMemento.builder();
                
        builder.id = entity.getId();
        builder.displayName = entity.getDisplayName();
        builder.type = entity.getClass().getName();
        
        for (Map.Entry<ConfigKey, Object> entry : ((AbstractEntity)entity).getAllConfig().entrySet()) {
            ConfigKey<?> key = entry.getKey();
            Object value = entry.getValue();
            Object transformedValue = MementoTransformer.transformEntitiesToIds(value);
            if (transformedValue != value) {
                builder.entityReferenceConfigs.add(key);
            } else {
                transformedValue = MementoTransformer.transformLocationsToIds(value);
                if (transformedValue != value) {
                    builder.locationReferenceConfigs.add(key);
                }
            }
            builder.config.put(key, transformedValue); 
        }
        
        for (Sensor<?> key : entity.getEntityType().getSensors()) {
            if (key instanceof AttributeSensor) {
                Object value = entity.getAttribute((AttributeSensor<?>)key);
                Object transformedValue = MementoTransformer.transformEntitiesToIds(value);
                if (transformedValue != value) {
                    builder.entityReferenceAttributes.add((AttributeSensor<?>)key);
                } else {
                    transformedValue = MementoTransformer.transformLocationsToIds(value);
                    if (transformedValue != value) {
                        builder.locationReferenceAttributes.add((AttributeSensor) key);
                    }
                }
                builder.attributes.put((AttributeSensor<?>)key, transformedValue);

            }
        }
        
        for (Location location : entity.getLocations()) {
            builder.locations.add(location.getId()); 
        }
        
        for (Entity child : entity.getOwnedChildren()) {
            builder.children.add(child.getId()); 
        }
        
        Entity parentEntity = entity.getOwner();
        builder.parent = (parentEntity != null) ? parentEntity.getId() : null;

        if (entity instanceof Group) {
            for (Entity member : ((Group)entity).getMembers()) {
                builder.members.add(member.getId()); 
            }
        }
        
        return builder;
    }
    
    /**
     * Given a location, extracts its state for serialization.
     * 
     * For bits of state that are references to other locations, these are treated in a special way:
     * the location reference is replaced by the location id.
     * TODO When we have a cleaner separation of constructor/config for entities and locations, then
     * we will remove this code!
     */
    public static LocationMemento newLocationMemento(Location location) {
        return newLocationMementoBuilder(location).build();
    }
    
    public static BasicLocationMemento.Builder newLocationMementoBuilder(Location location) {
        BasicLocationMemento.Builder builder = BasicLocationMemento.builder();
        
        builder.type = location.getClass().getName();
        builder.id = location.getId();
        builder.displayName = location.getName();
        builder.locationProperties.putAll(location.getLocationProperties());

        builder.flags.putAll(FlagUtils.getFieldsWithFlagsExcludingModifiers(location, Modifier.STATIC ^ Modifier.TRANSIENT));
        for (Map.Entry<String, Object> entry : builder.flags.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Object transformedValue = MementoTransformer.transformLocationsToIds(value);
            if (transformedValue != value) {
                entry.setValue(transformedValue);
                builder.locationReferenceFlags.add(key);
            }
        }
        
        Location parentLocation = location.getParentLocation();
        builder.parent = (parentLocation != null) ? parentLocation.getId() : null;
        
        for (Location child : location.getChildLocations()) {
            builder.children.add(child.getId()); 
        }
        
        return builder;
    }
}
