package brooklyn.entity.rebind.dto;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.entity.rebind.TreeUtils;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;

import com.google.common.base.Predicates;

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
        for (Entity entity : managementContext.getEntityManager().getEntities()) {
            builder.entities.put(entity.getId(), ((EntityInternal)entity).getRebindSupport().getMemento());
            
            for (Location location : entity.getLocations()) {
                if (!builder.locations.containsKey(location.getId())) {
                    for (Location locationInHierarchy : TreeUtils.findLocationsInHierarchy(location)) {
                        if (!builder.locations.containsKey(locationInHierarchy.getId())) {
                            builder.locations.put(locationInHierarchy.getId(), ((LocationInternal)locationInHierarchy).getRebindSupport().getMemento());
                        }
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
        EntityDynamicType definedType = EntityTypes.getDefinedEntityType(entity.getClass());
        BasicEntityMemento.Builder builder = BasicEntityMemento.builder();
                
        builder.id = entity.getId();
        builder.displayName = entity.getDisplayName();
        builder.type = entity.getClass().getName();
        builder.typeClass = entity.getClass();

        // TODO the dynamic attributeKeys and configKeys are computed in the BasicEntityMemento
        // whereas effectors are computed here -- should be consistent! 
        // (probably best to compute attrKeys and configKeys here)
        builder.effectors.addAll(entity.getEntityType().getEffectors());
        builder.effectors.removeAll(definedType.getEffectors().values());
        
        builder.isTopLevelApp = (entity instanceof Application && entity.getParent() == null);

        Map<ConfigKey<?>, Object> localConfig = ((EntityInternal)entity).getConfigMap().getLocalConfig();
        for (Map.Entry<ConfigKey<?>, Object> entry : localConfig.entrySet()) {
            ConfigKey<?> key = checkNotNull(entry.getKey(), localConfig);
            Object value = entry.getValue();
            
            // TODO Swapping an attributeWhenReady task for the actual value, if completed.
            // Long-term, want to just handle task-persistence properly.
            if (value instanceof Task) {
                Task<?> task = (Task<?>) value;
                if (task.isDone() && !task.isError()) {
                    value = task.getUnchecked();
                } else {
                    // TODO how to record a completed but errored task?
                    value = null;
                }
            }

            builder.config.put(key, value); 
        }
        
        Map<String, Object> localConfigUnmatched = MutableMap.copyOf(((EntityInternal)entity).getConfigMap().getLocalConfigBag().getAllConfig());
        for (ConfigKey<?> key : localConfig.keySet()) {
            localConfigUnmatched.remove(key.getName());
        }
        for (Map.Entry<String, Object> entry : localConfigUnmatched.entrySet()) {
            String key = checkNotNull(entry.getKey(), localConfig);
            Object value = entry.getValue();
            // TODO Not transforming; that code is deleted in another pending PR anyway!
            builder.configUnmatched.put(key, value); 
        }
        
        @SuppressWarnings("rawtypes")
        Map<AttributeSensor, Object> allAttributes = ((EntityInternal)entity).getAllAttributes();
        for (@SuppressWarnings("rawtypes") Map.Entry<AttributeSensor, Object> entry : allAttributes.entrySet()) {
            AttributeSensor<?> key = checkNotNull(entry.getKey(), allAttributes);
            Object value = entry.getValue();
            builder.attributes.put((AttributeSensor<?>)key, value);
        }
        
        for (Location location : entity.getLocations()) {
            builder.locations.add(location.getId()); 
        }
        
        for (Entity child : entity.getChildren()) {
            builder.children.add(child.getId()); 
        }
        
        // FIXME Not including policies, because lots of places register anonymous inner class policies
        // (e.g. AbstractController registering a AbstractMembershipTrackingPolicy)
        // Also, the entity constructor often re-creates the policy
        // Also see RebindManagerImpl.CheckpointingChangeListener.onChanged(Entity)
//        for (Policy policy : entity.getPolicies()) {
//            builder.policies.add(policy.getId()); 
//        }
        
        Entity parentEntity = entity.getParent();
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

        Set<String> nonPersistableFlagNames = MutableMap.<String,Object>builder()
                .putAll(FlagUtils.getFieldsWithFlagsWithModifiers(location, Modifier.TRANSIENT))
                .putAll(FlagUtils.getFieldsWithFlagsWithModifiers(location, Modifier.STATIC))
                .filterValues(Predicates.not(Predicates.instanceOf(ConfigKey.class)))
                .build()
                .keySet();
        Map<String, Object> persistableFlags = FlagUtils.getFieldsWithFlagsExcludingModifiers(location, Modifier.STATIC ^ Modifier.TRANSIENT);
        ConfigBag persistableConfig = new ConfigBag().copy( ((AbstractLocation)location).getLocalConfigBag() ).removeAll(nonPersistableFlagNames);

        builder.type = location.getClass().getName();
        builder.typeClass = location.getClass();
        builder.id = location.getId();
        builder.displayName = location.getDisplayName();
        builder.copyConfig(persistableConfig);
        builder.locationConfig.putAll(persistableFlags);

        Location parentLocation = location.getParent();
        builder.parent = (parentLocation != null) ? parentLocation.getId() : null;
        
        for (Location child : location.getChildren()) {
            builder.children.add(child.getId()); 
        }
        
        return builder;
    }
    
    /**
     * Given a policy, extracts its state for serialization.
     */
    public static PolicyMemento newPolicyMemento(Policy policy) {
        return newPolicyMementoBuilder(policy).build();
    }
    
    public static BasicPolicyMemento.Builder newPolicyMementoBuilder(Policy policy) {
        BasicPolicyMemento.Builder builder = BasicPolicyMemento.builder();
        
        builder.type = policy.getClass().getName();
        builder.typeClass = policy.getClass();
        builder.id = policy.getId();
        builder.displayName = policy.getName();

        builder.flags.putAll(FlagUtils.getFieldsWithFlagsExcludingModifiers(policy, Modifier.STATIC ^ Modifier.TRANSIENT));
        
        return builder;
    }
}
