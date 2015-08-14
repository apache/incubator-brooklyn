/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.rebind.dto;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import brooklyn.basic.BrooklynTypes;

import org.apache.brooklyn.api.basic.BrooklynObject;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Feed;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.api.event.AttributeSensor.SensorPersistenceMode;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.management.ManagementContext;
import org.apache.brooklyn.api.management.Task;
import org.apache.brooklyn.api.mementos.BrooklynMemento;
import org.apache.brooklyn.api.mementos.CatalogItemMemento;
import org.apache.brooklyn.api.mementos.EnricherMemento;
import org.apache.brooklyn.api.mementos.EntityMemento;
import org.apache.brooklyn.api.mementos.FeedMemento;
import org.apache.brooklyn.api.mementos.LocationMemento;
import org.apache.brooklyn.api.mementos.Memento;
import org.apache.brooklyn.api.mementos.PolicyMemento;
import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.policy.EntityAdjunct;
import org.apache.brooklyn.policy.Policy;

import brooklyn.catalog.internal.CatalogItemDo;
import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.AbstractBrooklynObjectRebindSupport;
import brooklyn.entity.rebind.TreeUtils;
import brooklyn.entity.rebind.persister.BrooklynPersistenceUtils;
import brooklyn.event.feed.AbstractFeed;

import org.apache.brooklyn.location.basic.LocationInternal;

import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Predicates;

public class MementosGenerators {

    private MementosGenerators() {}
    
    /** @deprecated since 0.7.0 use {@link #newBasicMemento(BrooklynObject)} */
    public static Memento newMemento(BrooklynObject instance) {
        return newBasicMemento(instance);
    }
    
    /**
     * Inspects a brooklyn object to create a basic corresponding memento.
     * <p>
     * The memento is "basic" in the sense that it does not tie in to any entity-specific customization;
     * the corresponding memento may subsequently be customized by the caller.
     * <p>
     * This method is intended for use by {@link AbstractBrooklynObjectRebindSupport#getMemento()}
     * and callers wanting a memento for an object should use that, or the
     * {@link BrooklynPersistenceUtils#newObjectMemento(BrooklynObject)} convenience.
     */
    @Beta
    public static Memento newBasicMemento(BrooklynObject instance) {
        if (instance instanceof Entity) {
            return newEntityMemento((Entity)instance);
        } else if (instance instanceof Location) {
            return newLocationMemento((Location)instance);
        } else if (instance instanceof Policy) {
            return newPolicyMemento((Policy)instance);
        } else if (instance instanceof Enricher) {
            return newEnricherMemento((Enricher) instance);
        } else if (instance instanceof Feed) {
            return newFeedMemento((Feed)instance);
        } else if (instance instanceof CatalogItem) {
            return newCatalogItemMemento((CatalogItem<?,?>) instance);
        } else {
            throw new IllegalArgumentException("Unexpected brooklyn type: "+(instance == null ? "null" : instance.getClass())+" ("+instance+")");
        }
    }

    /**
     * Walks the contents of a ManagementContext, to create a corresponding memento.
     * 
     * @deprecated since 0.7.0; will be moved to test code; generate each entity/location memento separately
     */
    @Deprecated
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
     * <p>
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static EntityMemento newEntityMemento(Entity entity) {
        return newEntityMementoBuilder(entity).build();
    }

    /**
     * @deprecated since 0.7.0; use {@link #newBasicMemento(BrooklynObject)} instead
     */
    @Deprecated
    public static BasicEntityMemento.Builder newEntityMementoBuilder(Entity entityRaw) {
        EntityInternal entity = (EntityInternal) entityRaw;
        BasicEntityMemento.Builder builder = BasicEntityMemento.builder();
        populateBrooklynObjectMementoBuilder(entity, builder);
        
        EntityDynamicType definedType = BrooklynTypes.getDefinedEntityType(entity.getClass());
                
        // TODO the dynamic attributeKeys and configKeys are computed in the BasicEntityMemento
        // whereas effectors are computed here -- should be consistent! 
        // (probably best to compute attrKeys and configKeys here)
        builder.effectors.addAll(entity.getEntityType().getEffectors());
        builder.effectors.removeAll(definedType.getEffectors().values());
        
        builder.isTopLevelApp = (entity instanceof Application && entity.getParent() == null);

        Map<ConfigKey<?>, Object> localConfig = entity.getConfigMap().getLocalConfig();
        for (Map.Entry<ConfigKey<?>, Object> entry : localConfig.entrySet()) {
            ConfigKey<?> key = checkNotNull(entry.getKey(), localConfig);
            Object value = configValueToPersistable(entry.getValue());
            builder.config.put(key, value); 
        }
        
        Map<String, Object> localConfigUnmatched = MutableMap.copyOf(entity.getConfigMap().getLocalConfigBag().getAllConfig());
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
        Map<AttributeSensor, Object> allAttributes = entity.getAllAttributes();
        for (@SuppressWarnings("rawtypes") Map.Entry<AttributeSensor, Object> entry : allAttributes.entrySet()) {
            AttributeSensor<?> key = checkNotNull(entry.getKey(), allAttributes);
            if (key.getPersistenceMode() != SensorPersistenceMode.NONE) {
                Object value = entry.getValue();
                builder.attributes.put((AttributeSensor<?>)key, value);
            }
        }
        
        for (Location location : entity.getLocations()) {
            builder.locations.add(location.getId()); 
        }

        for (Entity child : entity.getChildren()) {
            builder.children.add(child.getId()); 
        }
        
        for (Policy policy : entity.getPolicies()) {
            builder.policies.add(policy.getId()); 
        }
        
        for (Enricher enricher : entity.getEnrichers()) {
            builder.enrichers.add(enricher.getId()); 
        }
        
        for (Feed feed : entity.feeds().getFeeds()) {
            builder.feeds.add(feed.getId()); 
        }
        
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
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static Function<Entity, EntityMemento> entityMementoFunction() {
        return new Function<Entity,EntityMemento>() {
            @Override
            public EntityMemento apply(Entity input) {
                return MementosGenerators.newEntityMemento(input);
            }
        };
    }

    
    /**
     * Given a location, extracts its state for serialization.
     * 
     * For bits of state that are references to other locations, these are treated in a special way:
     * the location reference is replaced by the location id.
     * TODO When we have a cleaner separation of constructor/config for entities and locations, then
     * we will remove this code!
     * 
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static LocationMemento newLocationMemento(Location location) {
        return newLocationMementoBuilder(location).build();
    }
    
    /**
     * @deprecated since 0.7.0; use {@link #newBasicMemento(BrooklynObject)} instead
     */
    @Deprecated
    public static BasicLocationMemento.Builder newLocationMementoBuilder(Location location) {
        BasicLocationMemento.Builder builder = BasicLocationMemento.builder();
        populateBrooklynObjectMementoBuilder(location, builder);

        Set<String> nonPersistableFlagNames = MutableMap.<String,Object>builder()
                .putAll(FlagUtils.getFieldsWithFlagsWithModifiers(location, Modifier.TRANSIENT))
                .putAll(FlagUtils.getFieldsWithFlagsWithModifiers(location, Modifier.STATIC))
                .put("id", String.class)
                .filterValues(Predicates.not(Predicates.instanceOf(ConfigKey.class)))
                .build()
                .keySet();
        Map<String, Object> persistableFlags = MutableMap.<String, Object>builder()
                .putAll(FlagUtils.getFieldsWithFlagsExcludingModifiers(location, Modifier.STATIC ^ Modifier.TRANSIENT))
                .removeAll(nonPersistableFlagNames)
                .build();
        ConfigBag persistableConfig = new ConfigBag().copy( ((LocationInternal)location).config().getLocalBag() ).removeAll(nonPersistableFlagNames);

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
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static Function<Location, LocationMemento> locationMementoFunction() {
        return new Function<Location,LocationMemento>() {
            @Override
            public LocationMemento apply(Location input) {
                return MementosGenerators.newLocationMemento(input);
            }
        };
    }

    
    /**
     * Given a policy, extracts its state for serialization.
     * 
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static PolicyMemento newPolicyMemento(Policy policy) {
        BasicPolicyMemento.Builder builder = BasicPolicyMemento.builder();
        populateBrooklynObjectMementoBuilder(policy, builder);

        // TODO persist config keys as well? Or only support those defined on policy class;
        // current code will lose the ConfigKey type on rebind for anything not defined on class.
        // Whereas entities support that.
        // TODO Do we need the "nonPersistableFlagNames" that locations use?
        Map<ConfigKey<?>, Object> config = ((AbstractPolicy)policy).getConfigMap().getAllConfig();
        for (Map.Entry<ConfigKey<?>, Object> entry : config.entrySet()) {
            ConfigKey<?> key = checkNotNull(entry.getKey(), "config=%s", config);
            Object value = configValueToPersistable(entry.getValue());
            builder.config.put(key.getName(), value); 
        }
        
        Map<String, Object> persistableFlags = MutableMap.<String, Object>builder()
                .putAll(FlagUtils.getFieldsWithFlagsExcludingModifiers(policy, Modifier.STATIC ^ Modifier.TRANSIENT))
                .remove("id")
                .remove("name")
                .build();
        builder.config.putAll(persistableFlags);

        return builder.build();
    }
    
    /**
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static Function<Policy, PolicyMemento> policyMementoFunction() {
        return new Function<Policy,PolicyMemento>() {
            @Override
            public PolicyMemento apply(Policy input) {
                return MementosGenerators.newPolicyMemento(input);
            }
        };
    }

    /**
     * Given an enricher, extracts its state for serialization.
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static EnricherMemento newEnricherMemento(Enricher enricher) {
        BasicEnricherMemento.Builder builder = BasicEnricherMemento.builder();
        populateBrooklynObjectMementoBuilder(enricher, builder);
        
        // TODO persist config keys as well? Or only support those defined on policy class;
        // current code will lose the ConfigKey type on rebind for anything not defined on class.
        // Whereas entities support that.
        // TODO Do we need the "nonPersistableFlagNames" that locations use?
        Map<ConfigKey<?>, Object> config = ((AbstractEnricher)enricher).getConfigMap().getAllConfig();
        for (Map.Entry<ConfigKey<?>, Object> entry : config.entrySet()) {
            ConfigKey<?> key = checkNotNull(entry.getKey(), "config=%s", config);
            Object value = configValueToPersistable(entry.getValue());
            builder.config.put(key.getName(), value); 
        }
        
        Map<String, Object> persistableFlags = MutableMap.<String, Object>builder()
                .putAll(FlagUtils.getFieldsWithFlagsExcludingModifiers(enricher, Modifier.STATIC ^ Modifier.TRANSIENT))
                .remove("id")
                .remove("name")
                .build();
        builder.config.putAll(persistableFlags);

        return builder.build();
    }

    /**
     * Given a feed, extracts its state for serialization.
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static FeedMemento newFeedMemento(Feed feed) {
        BasicFeedMemento.Builder builder = BasicFeedMemento.builder();
        populateBrooklynObjectMementoBuilder(feed, builder);
        
        // TODO persist config keys as well? Or only support those defined on policy class;
        // current code will lose the ConfigKey type on rebind for anything not defined on class.
        // Whereas entities support that.
        // TODO Do we need the "nonPersistableFlagNames" that locations use?
        Map<ConfigKey<?>, Object> config = ((AbstractFeed)feed).getConfigMap().getAllConfig();
        for (Map.Entry<ConfigKey<?>, Object> entry : config.entrySet()) {
            ConfigKey<?> key = checkNotNull(entry.getKey(), "config=%s", config);
            Object value = configValueToPersistable(entry.getValue());
            builder.config.put(key.getName(), value); 
        }
        
        return builder.build();
    }
    
    /**
     * @deprecated since 0.7.0, see {@link #newBasicMemento(BrooklynObject)}
     */
    @Deprecated
    public static CatalogItemMemento newCatalogItemMemento(CatalogItem<?, ?> catalogItem) {
        if (catalogItem instanceof CatalogItemDo<?,?>) {
            catalogItem = ((CatalogItemDo<?,?>)catalogItem).getDto();
        }
        BasicCatalogItemMemento.Builder builder = BasicCatalogItemMemento.builder();
        populateBrooklynObjectMementoBuilder(catalogItem, builder);
        builder.catalogItemJavaType(catalogItem.getCatalogItemJavaType())
            .catalogItemType(catalogItem.getCatalogItemType())
            .description(catalogItem.getDescription())
            .iconUrl(catalogItem.getIconUrl())
            .javaType(catalogItem.getJavaType())
            .libraries(catalogItem.getLibraries())
            .symbolicName(catalogItem.getSymbolicName())
            .specType(catalogItem.getSpecType())
            .version(catalogItem.getVersion())
            .planYaml(catalogItem.getPlanYaml())
            .deprecated(catalogItem.isDeprecated());
        return builder.build();
    }
    
    private static void populateBrooklynObjectMementoBuilder(BrooklynObject instance, AbstractMemento.Builder<?> builder) {
        if (Proxy.isProxyClass(instance.getClass())) {
            throw new IllegalStateException("Attempt to create memento from proxy "+instance+" (would fail with wrong type)");
        }
        
        builder.id = instance.getId();
        builder.displayName = instance.getDisplayName();
        builder.catalogItemId = instance.getCatalogItemId();
        builder.type = instance.getClass().getName();
        builder.typeClass = instance.getClass();
        if (instance instanceof EntityAdjunct) {
            builder.uniqueTag = ((EntityAdjunct)instance).getUniqueTag();
        }
        for (Object tag : instance.tags().getTags()) {
            builder.tags.add(tag); 
        }
    }

    protected static Object configValueToPersistable(Object value) {
        // TODO Swapping an attributeWhenReady task for the actual value, if completed.
        // Long-term, want to just handle task-persistence properly.
        if (value instanceof Task) {
            Task<?> task = (Task<?>) value;
            if (task.isDone() && !task.isError()) {
                return task.getUnchecked();
            } else {
                // TODO how to record a completed but errored task?
                return null;
            }
        }
        return value;
    }
    
    public static Function<Enricher, EnricherMemento> enricherMementoFunction() {
        return new Function<Enricher,EnricherMemento>() {
            @Override
            public EnricherMemento apply(Enricher input) {
                return MementosGenerators.newEnricherMemento(input);
            }
        };
    }

    public static Function<Feed, FeedMemento> feedMementoFunction() {
        return new Function<Feed,FeedMemento>() {
            @Override
            public FeedMemento apply(Feed input) {
                return MementosGenerators.newFeedMemento(input);
            }
        };
    }
}
