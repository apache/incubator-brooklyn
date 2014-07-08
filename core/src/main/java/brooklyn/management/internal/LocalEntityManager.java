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
package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import groovy.util.ObservableList;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.proxying.BasicEntityTypeRegistry;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntityTypeRegistry;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.entity.trait.Startable;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.management.AccessController;
import brooklyn.management.internal.ManagementTransitionInfo.ManagementTransitionMode;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.SetFromLiveMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class LocalEntityManager implements EntityManagerInternal {

    private static final Logger log = LoggerFactory.getLogger(LocalEntityManager.class);

    private final LocalManagementContext managementContext;
    private final BasicEntityTypeRegistry entityTypeRegistry;
    private final InternalEntityFactory entityFactory;
    private final InternalPolicyFactory policyFactory;
    
    /** Entities that have been created, but have not yet begun to be managed */
    protected final Map<String,Entity> preRegisteredEntitiesById = new WeakHashMap<String, Entity>();

    /** Entities that are in the process of being managed, but where management is not yet complete */
    protected final Map<String,Entity> preManagedEntitiesById = new WeakHashMap<String, Entity>();
    
    /** Proxies of the managed entities */
    protected final Map<String,Entity> entityProxiesById = Maps.newLinkedHashMap();
    
    /** Real managed entities */
    protected final Map<String,Entity> entitiesById = Maps.newLinkedHashMap();
    
    /** Proxies of the managed entities */
    protected final ObservableList entities = new ObservableList();
    
    /** Proxies of the managed entities that are applications */
    protected final Set<Application> applications = Sets.newLinkedHashSet();

    private final BrooklynStorage storage;
    private final Map<String,String> entityTypes;
    private final Set<String> applicationIds;

    public LocalEntityManager(LocalManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
        this.storage = managementContext.getStorage();
        this.entityTypeRegistry = new BasicEntityTypeRegistry();
        this.policyFactory = new InternalPolicyFactory(managementContext);
        this.entityFactory = new InternalEntityFactory(managementContext, entityTypeRegistry, policyFactory);
        
        entityTypes = storage.getMap("entities");
        applicationIds = SetFromLiveMap.create(storage.<String,Boolean>getMap("applications"));
    }

    public InternalEntityFactory getEntityFactory() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return entityFactory;
    }

    public InternalPolicyFactory getPolicyFactory() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return policyFactory;
    }

    @Override
    public EntityTypeRegistry getEntityTypeRegistry() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return entityTypeRegistry;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Entity> T createEntity(EntitySpec<T> spec) {
        try {
            T entity = entityFactory.createEntity(spec);
            Entity proxy = ((AbstractEntity)entity).getProxy();
            return (T) checkNotNull(proxy, "proxy for entity %s, spec %s", entity, spec);
        } catch (Throwable e) {
            log.warn("Failed to create entity using spec "+spec+" (rethrowing)", e);
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public <T extends Entity> T createEntity(Map<?,?> config, Class<T> type) {
        return createEntity(EntitySpec.create(config, type));
    }

    @Override
    public <T extends Policy> T createPolicy(PolicySpec<T> spec) {
        try {
            return policyFactory.createPolicy(spec);
        } catch (Throwable e) {
            log.warn("Failed to create policy using spec "+spec+" (rethrowing)", e);
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public <T extends Enricher> T createEnricher(EnricherSpec<T> spec) {
        try {
            return policyFactory.createEnricher(spec);
        } catch (Throwable e) {
            log.warn("Failed to create enricher using spec "+spec+" (rethrowing)", e);
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public synchronized Collection<Entity> getEntities() {
        return ImmutableList.copyOf(entityProxiesById.values());
    }
    
    @Override
    public synchronized Collection<Entity> getEntitiesInApplication(Application application) {
        Predicate<Entity> predicate = EntityPredicates.applicationIdEqualTo(application.getId());
        return ImmutableList.copyOf(Iterables.filter(entityProxiesById.values(), predicate));
    }

    @Override
    public synchronized Collection<Entity> findEntities(Predicate<? super Entity> filter) {
        return ImmutableList.copyOf(Iterables.filter(entityProxiesById.values(), filter));
    }
    
    @Override
    public synchronized Collection<Entity> findEntitiesInApplication(Application application, Predicate<? super Entity> filter) {
        Predicate<Entity> predicate = Predicates.and(EntityPredicates.applicationIdEqualTo(application.getId()), filter);
        return ImmutableList.copyOf(Iterables.filter(entityProxiesById.values(), predicate));
    }

    @Override
    public synchronized Iterable<Entity> getAllEntitiesInApplication(Application application) {
        Predicate<Entity> predicate = EntityPredicates.applicationIdEqualTo(application.getId());
        Iterable<Entity> allentities = Iterables.concat(preRegisteredEntitiesById.values(), preManagedEntitiesById.values(), entityProxiesById.values());
        Iterable<Entity> result = Iterables.filter(allentities, predicate);
        return ImmutableSet.copyOf(Iterables.transform(result, new Function<Entity, Entity>() {
            @Override public Entity apply(Entity input) {
                return (input == null) ? null : input instanceof Proxy ? input : ((AbstractEntity)input).getProxyIfAvailable();
            }}));
    }

    @Override
    public synchronized Entity getEntity(String id) {
        return entityProxiesById.get(id);
    }
    
    synchronized Collection<Application> getApplications() {
        return ImmutableList.copyOf(applications);
    }
    
    @Override
    public boolean isManaged(Entity e) {
        return (isRunning() && getEntity(e.getId()) != null);
    }
    
    synchronized boolean isPreRegistered(Entity e) {
        return preRegisteredEntitiesById.containsKey(e.getId());
    }
    
    synchronized void prePreManage(Entity entity) {
        if (isPreRegistered(entity)) {
            log.warn(""+this+" redundant call to pre-pre-manage entity "+entity+"; skipping", 
                    new Exception("source of duplicate pre-pre-manage of "+entity));
            return;
        }
        preRegisteredEntitiesById.put(entity.getId(), entity);
    }

    // TODO synchronization issues here. We guard with isManaged(), but if another thread executing 
    // concurrently then the managed'ness could be set after our check but before we do 
    // onManagementStarting etc. However, we can't just synchronize because we're calling alien code 
    // (the user might override entity.onManagementStarting etc).
    // 
    // TODO We need to do some check about isPreManaged - i.e. is there another thread (or is this a
    // re-entrant call) where the entity is not yet full managed (i.e. isManaged==false) but we're in
    // the middle of managing it.
    // 
    // TODO Also see LocalLocationManager.manage(Entity), if fixing things here
    @Override
    public void manage(Entity e) {
        if (isManaged(e)) {
//            if (log.isDebugEnabled()) {
                log.warn(""+this+" redundant call to start management of entity (and descendants of) "+e+"; skipping", 
                    new Exception("source of duplicate management of "+e));
//            }
            return;
        }
        
        AccessController.Response access = managementContext.getAccessController().canManageEntity(e);
        if (!access.isAllowed()) {
            throw new IllegalStateException("Access controller forbids management of "+e+": "+access.getMsg());
        }

        final ManagementTransitionInfo info = new ManagementTransitionInfo(managementContext, ManagementTransitionMode.NORMAL);
        final List<EntityInternal> allEntities =  Lists.newArrayList();
        recursively(e, new Predicate<EntityInternal>() { public boolean apply(EntityInternal it) {
            if (it.getManagementSupport().isDeployed()) {
                return false;
            } else {
                allEntities.add(it);
                preManageNonRecursive(it);
                it.getManagementSupport().onManagementStarting(info); 
                return manageNonRecursive(it);
            }
        } });
        
        for (EntityInternal it : allEntities) {
            if (!it.getManagementSupport().isFullyManaged()) {
                it.getManagementSupport().onManagementStarted(info);
                managementContext.getRebindManager().getChangeListener().onManaged(it);
            }
        }
    }
    
    @Override
    public void unmanage(Entity e) {
        if (shouldSkipUnmanagement(e)) return;
        // Need to store all child entities as onManagementStopping removes a child from the parent entity
        final List<EntityInternal> allEntities =  Lists.newArrayList();        
        final ManagementTransitionInfo info = new ManagementTransitionInfo(managementContext, ManagementTransitionMode.NORMAL);
        recursively(e, new Predicate<EntityInternal>() { public boolean apply(EntityInternal it) {
            if (shouldSkipUnmanagement(it)) return false;
            allEntities.add(it);
            it.getManagementSupport().onManagementStopping(info); 
            return true;
        } });
        
        for (EntityInternal it : allEntities) {
            if (shouldSkipUnmanagement(it)) continue;
            unmanageNonRecursive(it);            
            it.getManagementSupport().onManagementStopped(info);
            managementContext.getRebindManager().getChangeListener().onUnmanaged(it);
            if (managementContext.gc != null) managementContext.gc.onUnmanaged(it);
        }
    }
    
    /**
     * activates management when effector invoked, warning unless context is acceptable
     * (currently only acceptable context is "start")
     */
    void manageIfNecessary(Entity entity, Object context) {
        if (!isRunning()) {
            return; // TODO Still a race for terminate being called, and then isManaged below returning false
        } else if (((EntityInternal)entity).getManagementSupport().wasDeployed()) {
            return;
        } else if (isManaged(entity)) {
            return;
        } else if (isPreManaged(entity)) {
            return;
        } else {
            Entity rootUnmanaged = entity;
            while (true) {
                Entity candidateUnmanagedParent = rootUnmanaged.getParent();
                if (candidateUnmanagedParent == null || isManaged(candidateUnmanagedParent) || isPreManaged(candidateUnmanagedParent))
                    break;
                rootUnmanaged = candidateUnmanagedParent;
            }
            if (context == Startable.START.getName())
                log.info("Activating local management for {} on start", rootUnmanaged);
            else
                log.warn("Activating local management for {} due to effector invocation on {}: {}", new Object[]{rootUnmanaged, entity, context});
            manage(rootUnmanaged);
        }
    }

    private void recursively(Entity e, Predicate<EntityInternal> action) {
        boolean success = action.apply( (EntityInternal)e );
        if (!success) {
            return; // Don't manage children if action false/unnecessary for parent
        }
        for (Entity child : e.getChildren()) {
            recursively(child, action);
        }
    }

    /**
     * Whether the entity is in the process of being managed.
     */
    private synchronized boolean isPreManaged(Entity e) {
        return preManagedEntitiesById.containsKey(e.getId());
    }

    /**
     * Should ensure that the entity is now known about, but should not be accessible from other entities yet.
     * 
     * Records that the given entity is about to be managed (used for answering {@link isPreManaged(Entity)}.
     * Note that refs to the given entity are stored in a a weak hashmap so if the subsequent management
     * attempt fails then this reference to the entity will eventually be discarded (if no-one else holds 
     * a reference).
     */
    private synchronized boolean preManageNonRecursive(Entity e) {
        Entity realE = toRealEntity(e);
        
        Object old = preManagedEntitiesById.put(e.getId(), realE);
        preRegisteredEntitiesById.remove(e.getId());
        
        if (old!=null) {
            if (old.equals(e)) {
                log.warn("{} redundant call to pre-start management of entity {}", this, e);
            } else {
                throw new IllegalStateException("call to pre-manage entity "+e+" but different entity "+old+" already known under that id at "+this);
            }
            return false;
        } else {
            if (log.isTraceEnabled()) log.trace("{} pre-start management of entity {}", this, e);
            return true;
        }
    }

    /**
     * Should ensure that the entity is now managed somewhere, and known about in all the lists.
     * Returns true if the entity has now become managed; false if it was already managed (anything else throws exception)
     */
    private synchronized boolean manageNonRecursive(Entity e) {
        Entity realE = toRealEntity(e);
        Entity proxyE = toProxyEntityIfAvailable(e);
        
        // If we don't already know about the proxy, then use the real thing; presumably it's 
        // the legacy way of creating the entity so didn't get a preManage() call
        entityProxiesById.put(e.getId(), proxyE);
        
        entityTypes.put(e.getId(), realE.getClass().getName());
        
        Object old = entitiesById.put(e.getId(), realE);
        if (old!=null) {
            if (old.equals(e)) {
                log.warn("{} redundant call to start management of entity {}", this, e);
            } else {
                throw new IllegalStateException("call to manage entity "+e+" but different entity "+old+" already known under that id at "+this);
            }
            return false;
        } else {
            if (log.isDebugEnabled()) log.debug("{} starting management of entity {}", this, e);
            preManagedEntitiesById.remove(e.getId());
            if ((e instanceof Application) && (e.getParent()==null)) {
                applications.add((Application)proxyE);
                applicationIds.add(e.getId());
            }
            entities.add(proxyE);
            return true;
        }
    }

    /**
     * Should ensure that the entity is no longer managed anywhere, remove from all lists.
     * Returns true if the entity has been removed from management; if it was not previously managed (anything else throws exception) 
     */
    private synchronized boolean unmanageNonRecursive(Entity e) {
        Entity proxyE = toProxyEntityIfAvailable(e);
        Collection<Group> groups = e.getGroups();
        
        e.clearParent();
        for (Group group : groups) {
            group.removeMember(e);
        }
        if (e instanceof Group) {
            Collection<Entity> members = ((Group)e).getMembers();
            for (Entity member : members) {
                member.removeGroup((Group)e);
            }
        }
        if (e instanceof Application) {
            applications.remove(proxyE);
            applicationIds.remove(e.getId());
        }
        entities.remove(proxyE);
        entityProxiesById.remove(e.getId());
        Object old = entitiesById.remove(e.getId());

        entityTypes.remove(e.getId());
        
        if (old==null) {
            log.warn("{} call to stop management of unknown entity (already unmanaged?) {}", this, e);
            return false;
        } else if (!old.equals(e)) {
            // shouldn't happen...
            log.error("{} call to stop management of entity {} removed different entity {}", new Object[] { this, e, old });
            return true;
        } else {
            if (log.isDebugEnabled()) log.debug("{} stopped management of entity {}", this, e);
            return true;
        }
    }

    void addEntitySetListener(CollectionChangeListener<Entity> listener) {
        //must notify listener in a different thread to avoid deadlock (issue #378)
        AsyncCollectionChangeAdapter<Entity> wrappedListener = new AsyncCollectionChangeAdapter<Entity>(managementContext.getExecutionManager(), listener);
        entities.addPropertyChangeListener(new GroovyObservablesPropertyChangeToCollectionChangeAdapter(wrappedListener));
    }

    void removeEntitySetListener(CollectionChangeListener<Entity> listener) {
        AsyncCollectionChangeAdapter<Entity> wrappedListener = new AsyncCollectionChangeAdapter<Entity>(managementContext.getExecutionManager(), listener);
        entities.removePropertyChangeListener(new GroovyObservablesPropertyChangeToCollectionChangeAdapter(wrappedListener));
    }
    
    private boolean shouldSkipUnmanagement(Entity e) {
        if (e==null) {
            log.warn(""+this+" call to unmanage null entity; skipping",  
                new IllegalStateException("source of null unmanagement call to "+this));
            return true;
        }
        if (!isManaged(e)) {
            log.warn("{} call to stop management of unknown entity (already unmanaged?) {}; skipping, and all descendants", this, e);
            return true;
        }
        return false;
    }
    
    private Entity toProxyEntityIfAvailable(Entity e) {
        checkNotNull(e, "entity");
        
        if (e instanceof EntityProxy) {
            return e;
        } else if (e instanceof AbstractEntity) {
            Entity result = ((AbstractEntity)e).getProxy();
            return (result == null) ? e : result;
        } else {
            return e;
        }
    }
    
    private Entity toRealEntity(Entity e) {
        checkNotNull(e, "entity");
        
        if (e instanceof AbstractEntity) {
            return e;
        } else {
            Entity result = toRealEntityOrNull(e.getId());
            if (result == null) {
                throw new IllegalStateException("No concrete entity known for entity "+e+" ("+e.getId()+", "+e.getEntityType().getName()+")");
            }
            return result;
        }
    }

    public boolean isKnownEntityId(String id) {
        return entitiesById.containsKey(id) || preManagedEntitiesById.containsKey(id) || preRegisteredEntitiesById.containsKey(id);
    }
    
    private Entity toRealEntityOrNull(String id) {
        Entity result = entitiesById.get(id);
        if (result == null) {
            result = preManagedEntitiesById.get(id);
        }
        if (result == null) {
            result = preRegisteredEntitiesById.get(id);
        }
        return result;
    }
    
    private boolean isRunning() {
        return managementContext.isRunning();
    }
}
