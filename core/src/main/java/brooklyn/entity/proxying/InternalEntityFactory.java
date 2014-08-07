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
import static com.google.common.base.Preconditions.checkState;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
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
import brooklyn.util.task.Tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Creates entities (and proxies) of required types, given the 
 * 
 * This is an internal class for use by core-brooklyn. End-users are strongly discouraged from
 * using this class directly.
 * 
 * Used in three situations:
 * <ul>
 *   <li>Normal entity creation (through entityManager.createEntity)
 *   <li>rebind (i.e. Brooklyn restart, or promotion of HA standby manager node)
 *   <li>yaml parsing
 * </ul>
 * 
 * @author aled
 */
public class InternalEntityFactory extends InternalFactory {

    private static final Logger log = LoggerFactory.getLogger(InternalEntityFactory.class);
    
    private final EntityTypeRegistry entityTypeRegistry;
    private final InternalPolicyFactory policyFactory;
    
    public InternalEntityFactory(ManagementContextInternal managementContext, EntityTypeRegistry entityTypeRegistry, InternalPolicyFactory policyFactory) {
        super(managementContext);
        this.entityTypeRegistry = checkNotNull(entityTypeRegistry, "entityTypeRegistry");
        this.policyFactory = checkNotNull(policyFactory, "policyFactory");
    }

    public <T extends Entity> T createEntityProxy(EntitySpec<T> spec, T entity) {
        Set<Class<?>> interfaces = Sets.newLinkedHashSet();
        if (spec.getType().isInterface()) {
            interfaces.add(spec.getType());
        } else {
            log.warn("EntitySpec declared in terms of concrete type "+spec.getType()+"; should be supplied in terms of interface");
        }
        interfaces.addAll(spec.getAdditionalInterfaces());
        
        return createEntityProxy(interfaces, entity);
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> T createEntityProxy(Iterable<Class<?>> interfaces, T entity) {
        // TODO Don't want the proxy to have to implement EntityLocal, but required by how 
        // AbstractEntity.parent is used (e.g. parent.getAllConfig)
        Set<Class<?>> allInterfaces = MutableSet.<Class<?>>builder()
                .add(EntityProxy.class, Entity.class, EntityLocal.class, EntityInternal.class)
                .addAll(interfaces)
                .build();

        // TODO OSGi strangeness! The classloader obtained from the type should be enough.
        // If an OSGi class loader, it should delegate to find things like Entity.class etc.
        // However, we get errors such as:
        //    NoClassDefFoundError: brooklyn.event.AttributeSensor not found by io.brooklyn.brooklyn-test-osgi-entities
        // Building our own aggregating class loader gets around this.
        // But we really should not have to do this! What are the consequences?
        //
        // The reason for the error is that the proxy tries to load all classes
        // referenced from the entity and its interfaces with the single passed loader
        // while a normal class loading would nest the class loaders (loading interfaces'
        // references with their own class loaders which in our case are different).
        Collection<ClassLoader> loaders = Sets.newLinkedHashSet();
        addClassLoaders(entity.getClass(), loaders);
        for (Class<?> iface : allInterfaces) {
            loaders.add(iface.getClassLoader());
        }

        AggregateClassLoader aggregateClassLoader =  AggregateClassLoader.newInstanceWithNoLoaders();
        for (ClassLoader cl : loaders) {
            aggregateClassLoader.addLast(cl);
        }

        return (T) java.lang.reflect.Proxy.newProxyInstance(
                aggregateClassLoader,
                allInterfaces.toArray(new Class[allInterfaces.size()]),
                new EntityProxyImpl(entity));
    }

    private void addClassLoaders(Class<?> type, Collection<ClassLoader> loaders) {
        ClassLoader cl = type.getClassLoader();

        //java.lang.Object.getClassLoader() = null
        if (cl != null) {
            loaders.add(cl);
        }

        Class<?> superType = type.getSuperclass();
        if (superType != null) {
            addClassLoaders(superType, loaders);
        }
        for (Class<?> iface : type.getInterfaces()) {
            addClassLoaders(iface, loaders);
        }
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
    
    @SuppressWarnings("deprecation")
    protected <T extends Entity> T createEntityAndDescendantsUninitialized(EntitySpec<T> spec, Map<String,Entity> entitiesByEntityId, Map<String,EntitySpec<?>> specsByEntityId) {
        if (spec.getFlags().containsKey("parent") || spec.getFlags().containsKey("owner")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent or owner; use spec.parent() instead for "+spec);
        }
        if (spec.getFlags().containsKey("id")) {
            throw new IllegalArgumentException("Spec's flags must not contain id; use spec.id() instead for "+spec);
        }
        if (spec.getId() != null) {
            log.warn("Use of deprecated EntitySpec.id ({}); instead let management context pick the random+unique id", spec);
            if (((LocalEntityManager)managementContext.getEntityManager()).isKnownEntityId(spec.getId())) {
                throw new IllegalArgumentException("Entity with id "+spec.getId()+" already exists; cannot create new entity with this explicit id from spec "+spec);
            }
        }
        
        try {
            Class<? extends T> clazz = getImplementedBy(spec);
            
            T entity = constructEntity(clazz, spec);
            
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
            
            for (Entity member: spec.getMembers()) {
                if (!(entity instanceof Group)) {
                    throw new IllegalStateException("Entity "+entity+" must be a group to add members "+spec.getMembers());
                }
                ((Group)entity).addMember(member);
            }

            for (Group group : spec.getGroups()) {
                group.addMember(entity);
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
            
            entity.getTagSupport().addTags(spec.getTags());
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
     * Sets the entity's proxy. If {@link EntitySpec#id(String)} was set then uses that to override the entity's id, 
     * but that behaviour is deprecated.
     */
    public <T extends Entity> T constructEntity(Class<? extends T> clazz, EntitySpec<T> spec) {
        @SuppressWarnings("deprecation")
        T entity = constructEntityImpl(clazz, spec.getFlags(), spec.getId());
        if (((AbstractEntity)entity).getProxy() == null) ((AbstractEntity)entity).setProxy(createEntityProxy(spec, entity));
        return entity;
    }

    /**
     * Constructs a new-style entity (fails if no no-arg constructor).
     * Sets the entity's id and proxy.
     */
    public <T extends Entity> T constructEntity(Class<T> clazz, Iterable<Class<?>> interfaces, String entityId) {
        if (!isNewStyle(clazz)) {
            throw new IllegalStateException("Cannot construct old-style entity "+clazz);
        }
        checkNotNull(entityId, "entityId");
        checkState(interfaces != null && !Iterables.isEmpty(interfaces), "must have at least one interface for entity %s:%s", clazz, entityId);
        
        T entity = constructEntityImpl(clazz, ImmutableMap.<String, Object>of(), entityId);
        if (((AbstractEntity)entity).getProxy() == null) ((AbstractEntity)entity).setProxy(createEntityProxy(interfaces, entity));
        return entity;
    }
    
    protected <T extends Entity> T constructEntityImpl(Class<? extends T> clazz, Map<String, ?> constructionFlags, String entityId) {
        T entity = super.construct(clazz, constructionFlags);
        
        if (entityId != null) {
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", entityId), entity);
        }
        if (entity instanceof AbstractApplication) {
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("mgmt", managementContext), entity);
        }
        managementContext.prePreManage(entity);
        ((AbstractEntity)entity).setManagementContext(managementContext);

        return entity;
    }

    @Override
    protected <T> T constructOldStyle(Class<T> clazz, Map<String,?> flags) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (flags.containsKey("parent") || flags.containsKey("owner")) {
            throw new IllegalArgumentException("Spec's flags must not contain parent or owner; use spec.parent() instead for "+clazz);
        }
        return super.constructOldStyle(clazz, flags);
    }
    
    private <T extends Entity> Class<? extends T> getImplementedBy(EntitySpec<T> spec) {
        if (spec.getImplementation() != null) {
            return spec.getImplementation();
        } else {
            return entityTypeRegistry.getImplementedBy(spec.getType());
        }
    }
}
