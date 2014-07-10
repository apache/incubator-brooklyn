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
package brooklyn.entity.proxying;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalEntityManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.javalang.AggregateClassLoader;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.task.Tasks;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

/**
 * Creates entities (and proxies) of required types, given the 
 * 
 * This is an internal class for use by core-brooklyn. End-users are strongly discouraged from
 * using this class directly.
 * 
 * @author aled
 */
public class InternalEntityFactory {

    private static final Logger log = LoggerFactory.getLogger(InternalEntityFactory.class);
    
    private final ManagementContextInternal managementContext;
    private final EntityTypeRegistry entityTypeRegistry;
    private final InternalPolicyFactory policyFactory;
    
    /**
     * For tracking if AbstractEntity constructor has been called by framework, or in legacy way (i.e. directly).
     * 
     * To be deleted once we delete support for constructing entities directly (and expecting configure() to be
     * called inside the constructor, etc).
     * 
     * @author aled
     */
    public static class FactoryConstructionTracker {
        private static ThreadLocal<Boolean> constructing = new ThreadLocal<Boolean>();
        
        public static boolean isConstructing() {
            return (constructing.get() == Boolean.TRUE);
        }
        
        static void reset() {
            constructing.set(Boolean.FALSE);
        }
        
        static void setConstructing() {
            constructing.set(Boolean.TRUE);
        }
    }

    /**
     * Returns true if this is a "new-style" entity (i.e. where not expected to call the constructor to instantiate it).
     * That means it is an entity with a no-arg constructor.
     * @param managementContext
     * @param clazz
     */
    public static boolean isNewStyleEntity(ManagementContext managementContext, Class<?> clazz) {
        try {
            return isNewStyleEntity(clazz);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    public static boolean isNewStyleEntity(Class<?> clazz) {
        if (!Entity.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not an entity");
        }
        
        try {
            clazz.getConstructor(new Class[0]);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    public InternalEntityFactory(ManagementContextInternal managementContext, EntityTypeRegistry entityTypeRegistry, InternalPolicyFactory policyFactory) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
        this.entityTypeRegistry = checkNotNull(entityTypeRegistry, "entityTypeRegistry");
        this.policyFactory = checkNotNull(policyFactory, "policyFactory");
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> T createEntityProxy(EntitySpec<T> spec, T entity) {
        // TODO Don't want the proxy to have to implement EntityLocal, but required by how 
        // AbstractEntity.parent is used (e.g. parent.getAllConfig)
        ClassLoader classloader = (spec.getImplementation() != null ? spec.getImplementation() : spec.getType()).getClassLoader();
        MutableSet.Builder<Class<?>> builder = MutableSet.<Class<?>>builder()
                .add(EntityProxy.class, Entity.class, EntityLocal.class, EntityInternal.class);
        if (spec.getType().isInterface()) {
            builder.add(spec.getType());
        } else {
            log.warn("EntitySpec declared in terms of concrete type "+spec.getType()+"; should be supplied in terms of interface");
        }
        builder.addAll(spec.getAdditionalInterfaces());
        Set<Class<?>> interfaces = builder.build();
        
        // TODO OSGi strangeness! The classloader obtained from the type should be enough.
        // If an OSGi class loader, it should delegate to find things like Entity.class etc.
        // However, we get errors such as:
        //    NoClassDefFoundError: brooklyn.event.AttributeSensor not found by io.brooklyn.brooklyn-test-osgi-entities
        // Building our own aggregating class loader gets around this.
        // But we really should not have to do this! What are the consequences?
        AggregateClassLoader aggregateClassLoader =  AggregateClassLoader.newInstanceWithNoLoaders();
        aggregateClassLoader.addFirst(classloader);
        aggregateClassLoader.addFirst(entity.getClass().getClassLoader());
        for(Class<?> iface : interfaces) {
            aggregateClassLoader.addLast(iface.getClassLoader());
        }

        return (T) java.lang.reflect.Proxy.newProxyInstance(
                aggregateClassLoader,
                interfaces.toArray(new Class[interfaces.size()]),
                new EntityProxyImpl(entity));
    }

    public <T extends Entity> T createEntity(EntitySpec<T> spec) {
        /* Order is important here. Changed Jul 2014 when supporting children in spec.
         * (Previously was much simpler, and parent was set right after running initializers; and there were no children.)
         * <p>
         * It seems we need access to the parent (indeed the root application) when running some initializers (esp children initializers).
         * <p>
         * Now we do two passes, so that hierarchy is fully populated before initialization and policies.
         * (This is needed where some config or initializer might reference another entity by its ID, e.g. yaml $brooklyn:component("id"). 
         * Initialization is done in parent-first order with depth-first children traversal.
         */

        // (maps needed because we need the spec, and we need to keep the AbstractEntity to call init, not a proxy)
        Map<String,Entity> entitiesByEntityId = MutableMap.of();
        Map<String,EntitySpec<?>> specsByEntityId = MutableMap.of();
        
        T entity = createEntityAndDescendantsUninitialized(spec, entitiesByEntityId, specsByEntityId);
        initEntityAndDescendants(entity.getId(), entitiesByEntityId, specsByEntityId);
        return entity;
    }
    
    protected <T extends Entity> T createEntityAndDescendantsUninitialized(EntitySpec<T> spec, Map<String,Entity> entitiesByEntityId, Map<String,EntitySpec<?>> specsByEntityId) {
        if (spec.getFlags().containsKey("parent") || spec.getFlags().containsKey("owner")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent or owner; use spec.parent() instead for "+spec);
        }
        if (spec.getFlags().containsKey("id")) {
            throw new IllegalArgumentException("Spec's flags must not contain id; use spec.id() instead for "+spec);
        }
        if (spec.getId() != null && ((LocalEntityManager)managementContext.getEntityManager()).isKnownEntityId(spec.getId())) {
            throw new IllegalArgumentException("Entity with id "+spec.getId()+" already exists; cannot create new entity with this explicit id from spec "+spec);
        }
        
        try {
            Class<? extends T> clazz = getImplementedBy(spec);
            
            T entity = constructEntity(clazz, spec);
            
            // TODO Could move setManagementContext call into constructEntity; would that break rebind?
            if (spec.getId() != null) {
                FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", spec.getId()), entity);
            }
            ((AbstractEntity)entity).setManagementContext(managementContext);
            managementContext.prePreManage(entity);

            loadUnitializedEntity(entity, spec);

            entitiesByEntityId.put(entity.getId(), entity);
            specsByEntityId.put(entity.getId(), spec);

            for (EntitySpec<?> childSpec : spec.getChildren()) {
                if (childSpec.getParent()!=null) {
                    if (!childSpec.getParent().equals(entity)) {
                        throw new IllegalStateException("Spec "+childSpec+" has parent "+childSpec.getParent()+" defined, "
                            + "but it is defined as a child of "+entity);
                    }
                    log.warn("Child spec "+childSpec+" is already set with parent "+entity+"; how did this happen?!");
                }
                childSpec.parent(entity);
                Entity child = createEntityAndDescendantsUninitialized(childSpec, entitiesByEntityId, specsByEntityId);
                entity.addChild(child);
            }
            
            return entity;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected <T extends Entity> T loadUnitializedEntity(final T entity, final EntitySpec<T> spec) {
        try {
            if (spec.getDisplayName()!=null)
                ((AbstractEntity)entity).setDisplayName(spec.getDisplayName());
            
            if (((AbstractEntity)entity).getProxy() == null) ((AbstractEntity)entity).setProxy(createEntityProxy(spec, entity));
            ((AbstractEntity)entity).configure(MutableMap.copyOf(spec.getFlags()));
            
            for (Map.Entry<ConfigKey<?>, Object> entry : spec.getConfig().entrySet()) {
                ((EntityLocal)entity).setConfig((ConfigKey)entry.getKey(), entry.getValue());
            }

            Entity parent = spec.getParent();
            if (parent != null) {
                parent = (parent instanceof AbstractEntity) ? ((AbstractEntity)parent).getProxyIfAvailable() : parent;
                entity.setParent(parent);
            }
            
            return entity;
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    protected <T extends Entity> void initEntityAndDescendants(String entityId, final Map<String,Entity> entitiesByEntityId, final Map<String,EntitySpec<?>> specsByEntityId) {
        final Entity entity = entitiesByEntityId.get(entityId);
        final EntitySpec<?> spec = specsByEntityId.get(entityId);
        
        if (entity==null || spec==null) {
            log.debug("Skipping initialization of "+entityId+" found as child of entity being initialized, "
                + "but this child is not one we created; likely it was created by an initializer, "
                + "and thus it should be already fully initialized.");
            return;
        }
        
        /* Marked transient so that the task is not needlessly kept around at the highest level.
         * Note that the task is not normally visible in the GUI, because 
         * (a) while it is running, the entity is parentless (and so not in the tree);
         * and (b) when it is completed it is GC'd, as it is transient.
         * However task info is available via the API if you know its ID,
         * and if better subtask querying is available it will be picked up as a background task 
         * of the parent entity creating this child entity
         * (note however such subtasks are currently filtered based on parent entity so is excluded).
         */
        ((EntityInternal)entity).getExecutionContext().submit(Tasks.builder().dynamic(false).name("Entity initialization")
                .tag(BrooklynTaskTags.tagForContextEntity(entity))
                .tag(BrooklynTaskTags.TRANSIENT_TASK_TAG)
                .body(new Runnable() {
            @Override
            public void run() {
                ((AbstractEntity)entity).init();
                
                ((AbstractEntity)entity).addLocations(spec.getLocations());

                for (EntityInitializer initializer: spec.getInitializers())
                    initializer.apply((EntityInternal)entity);
                /* 31 Mar 2014, moved initialization (above) into this task: primarily for consistency and traceability on failure.
                 * TBC whether this is good/bad/indifferent. My (Alex) opinion is that whether it is done in a subtask 
                 * should be the same as whether enricher/policy/etc (below) is done subtasks, which is was added recently
                 * in 249c96fbb18bd9d763029475e0a3dc251c01b287. @nakomis can you give exact reason code below is needed in a task
                 * commit message said was to do with wiring up yaml sensors and policies -- which makes sense but specifics would be handy!
                 * and would let me know if there is any reason to do / not_do the initializer code above also here! 
                 */
                
                for (Enricher enricher : spec.getEnrichers()) {
                    entity.addEnricher(enricher);
                }
                
                for (EnricherSpec<?> enricherSpec : spec.getEnricherSpecs()) {
                    entity.addEnricher(policyFactory.createEnricher(enricherSpec));
                }
                
                for (Policy policy : spec.getPolicies()) {
                    entity.addPolicy((AbstractPolicy)policy);
                }
                
                for (PolicySpec<?> policySpec : spec.getPolicySpecs()) {
                    entity.addPolicy(policyFactory.createPolicy(policySpec));
                }
                                
                for (Entity child: entity.getChildren()) {
                    // right now descendants are initialized depth-first (see the getUnchecked() call below)
                    // they could be done in parallel, but OTOH initializers should be very quick
                    initEntityAndDescendants(child.getId(), entitiesByEntityId, specsByEntityId);
                }
            }
        }).build()).getUnchecked();        
    }
    
    /**
     * Constructs an entity (if new-style, calls no-arg constructor; if old-style, uses spec to pass in config).
     */
    public <T extends Entity> T constructEntity(Class<? extends T> clazz, EntitySpec<T> spec) {
        try {
            FactoryConstructionTracker.setConstructing();
            try {
                if (isNewStyleEntity(clazz)) {
                    return clazz.newInstance();
                } else {
                    return constructOldStyle(clazz, MutableMap.copyOf(spec.getFlags()));
                }
            } finally {
                FactoryConstructionTracker.reset();
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
         }
     }

    /**
     * Constructs a new-style entity (fails if no no-arg constructor).
     */
    public <T extends Entity> T constructEntity(Class<T> clazz) {
        try {
            FactoryConstructionTracker.setConstructing();
            try {
                if (isNewStyleEntity(clazz)) {
                    return clazz.newInstance();
                } else {
                    throw new IllegalStateException("Entity class "+clazz+" must have a no-arg constructor");
                }
            } finally {
                FactoryConstructionTracker.reset();
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    private <T extends Entity> T constructOldStyle(Class<? extends T> clazz, Map<String,?> flags) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (flags.containsKey("parent") || flags.containsKey("owner")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent or owner; use spec.parent() instead for "+clazz);
        }
        Optional<? extends T> v = Reflections.invokeConstructorWithArgs(clazz, new Object[] {MutableMap.copyOf(flags)}, true);
        if (v.isPresent()) {
            return v.get();
        } else {
            throw new IllegalStateException("No valid constructor defined for "+clazz+" (expected no-arg or single java.util.Map argument)");
        }
    }
    
    private <T extends Entity> Class<? extends T> getImplementedBy(EntitySpec<T> spec) {
        if (spec.getImplementation() != null) {
            return spec.getImplementation();
        } else {
            return entityTypeRegistry.getImplementedBy(spec.getType());
        }
    }
}
