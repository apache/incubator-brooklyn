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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import org.apache.brooklyn.management.AccessController;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.policy.EnricherSpec;
import org.apache.brooklyn.policy.Policy;
import org.apache.brooklyn.policy.PolicySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynLogging;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.proxying.BasicEntityTypeRegistry;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.entity.proxying.EntityProxyImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntityTypeRegistry;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.entity.trait.Startable;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.collections.SetFromLiveMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
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
    protected final Map<String,Entity> preRegisteredEntitiesById = Collections.synchronizedMap(new WeakHashMap<String, Entity>());

    /** Entities that are in the process of being managed, but where management is not yet complete */
    protected final Map<String,Entity> preManagedEntitiesById = Collections.synchronizedMap(new WeakHashMap<String, Entity>());
    
    /** Proxies of the managed entities */
    protected final ConcurrentMap<String,Entity> entityProxiesById = Maps.newConcurrentMap();
    
    /** Real managed entities */
    protected final Map<String,Entity> entitiesById = Maps.newLinkedHashMap();
    
    /** Management mode for each entity */
    protected final Map<String,ManagementTransitionMode> entityModesById = Collections.synchronizedMap(Maps.<String,ManagementTransitionMode>newLinkedHashMap());

    /** Proxies of the managed entities */
    protected final ObservableList entities = new ObservableList();
    
    /** Proxies of the managed entities that are applications */
    protected final Set<Application> applications = Sets.newConcurrentHashSet();

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
    public Collection<Entity> getEntities() {
        return ImmutableList.copyOf(entityProxiesById.values());
    }
    
    @Override
    public Collection<String> getEntityIds() {
        return ImmutableList.copyOf(entityProxiesById.keySet());
    }
    
    @Override
    public Collection<Entity> getEntitiesInApplication(Application application) {
        Predicate<Entity> predicate = EntityPredicates.applicationIdEqualTo(application.getId());
        return ImmutableList.copyOf(Iterables.filter(entityProxiesById.values(), predicate));
    }

    @Override
    public Collection<Entity> findEntities(Predicate<? super Entity> filter) {
        return ImmutableList.copyOf(Iterables.filter(entityProxiesById.values(), filter));
    }
    
    @Override
    public Collection<Entity> findEntitiesInApplication(Application application, Predicate<? super Entity> filter) {
        Predicate<Entity> predicate = Predicates.and(EntityPredicates.applicationIdEqualTo(application.getId()), filter);
        return ImmutableList.copyOf(Iterables.filter(entityProxiesById.values(), predicate));
    }

    @Override
    public Iterable<Entity> getAllEntitiesInApplication(Application application) {
        Predicate<Entity> predicate = EntityPredicates.applicationIdEqualTo(application.getId());
        Iterable<Entity> allentities = Iterables.concat(preRegisteredEntitiesById.values(), preManagedEntitiesById.values(), entityProxiesById.values());
        Iterable<Entity> result = Iterables.filter(allentities, predicate);
        return ImmutableSet.copyOf(Iterables.transform(result, new Function<Entity, Entity>() {
            @Override public Entity apply(Entity input) {
                return Entities.proxy(input);
            }}));
    }

    @Override
    public Entity getEntity(String id) {
        return entityProxiesById.get(id);
    }
    
    Collection<Application> getApplications() {
        return ImmutableList.copyOf(applications);
    }
    
    @Override
    public boolean isManaged(Entity e) {
        return (isRunning() && getEntity(e.getId()) != null);
    }
    
    boolean isPreRegistered(Entity e) {
        return preRegisteredEntitiesById.containsKey(e.getId());
    }
    
    void prePreManage(Entity entity) {
        if (isPreRegistered(entity)) {
            log.warn(""+this+" redundant call to pre-pre-manage entity "+entity+"; skipping", 
                    new Exception("source of duplicate pre-pre-manage of "+entity));
            return;
        }
        preRegisteredEntitiesById.put(entity.getId(), entity);
    }
    
    @Override
    public ManagementTransitionMode getLastManagementTransitionMode(String itemId) {
        return entityModesById.get(itemId);
    }
    
    @Override
    public void setManagementTransitionMode(Entity item, ManagementTransitionMode mode) {
        entityModesById.put(item.getId(), mode);
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
            log.warn(""+this+" redundant call to start management of entity (and descendants of) "+e+"; skipping", 
                    new Exception("source of duplicate management of "+e));
            return;
        }
        manageRecursive(e, ManagementTransitionMode.guessing(BrooklynObjectManagementMode.NONEXISTENT, BrooklynObjectManagementMode.MANAGED_PRIMARY));
    }

    @Override
    public void manageRebindedRoot(Entity item) {
        ManagementTransitionMode mode = getLastManagementTransitionMode(item.getId());
        Preconditions.checkNotNull(mode, "Mode not set for rebinding %s", item);
        manageRecursive(item, mode);
    }
    
    protected void checkManagementAllowed(Entity item) {
        AccessController.Response access = managementContext.getAccessController().canManageEntity(item);
        if (!access.isAllowed()) {
            throw new IllegalStateException("Access controller forbids management of "+item+": "+access.getMsg());
        }
    }
    
    /* TODO we sloppily use "recursive" to ensure ordering of parent-first in many places
     * (which may not be necessary but seems like a good idea),
     * and also to collect many entities when doing a big rebind,
     * ensuring all have #manageNonRecursive called before calling #onManagementStarted.
     * 
     * it would be better to have a manageAll(Map<Entity,ManagementTransitionMode> items)
     * method which did that in two phases, allowing us to selectively rebind, 
     * esp when we come to want supporting different modes and different brooklyn nodes.
     * 
     * the impl of manageAll could sort them with parents before children,
     * (and manageRecursive could simply populate a map and delegate to manageAll).
     * 
     * manageRebindRoot would then go, and the (few) callers would construct the map.
     * 
     * similarly we might want an unmanageAll(), 
     * although possibly all unmanagement should be recursive, if we assume an entity's ancestors are always at least proxied
     * (and the non-recursive RO path here could maybe be dropped)
     */
    
    /** Applies management lifecycle callbacks (onManagementStarting, for all beforehand, then onManagementStopped, for all after) */
    protected void manageRecursive(Entity e, final ManagementTransitionMode initialMode) {
        checkManagementAllowed(e);

        final List<EntityInternal> allEntities =  Lists.newArrayList();
        Predicate<EntityInternal> manageEntity = new Predicate<EntityInternal>() { public boolean apply(EntityInternal it) {
            ManagementTransitionMode mode = getLastManagementTransitionMode(it.getId());
            if (mode==null) {
                setManagementTransitionMode(it, mode = initialMode);
            }
            
            Boolean isReadOnlyFromEntity = it.getManagementSupport().isReadOnlyRaw();
            if (isReadOnlyFromEntity==null) {
                if (mode.isReadOnly()) {
                    // should have been marked by rebinder
                    log.warn("Read-only entity "+it+" not marked as such on call to manage; marking and continuing");
                }
                it.getManagementSupport().setReadOnly(mode.isReadOnly());
            } else {
                if (!isReadOnlyFromEntity.equals(mode.isReadOnly())) {
                    log.warn("Read-only status at entity "+it+" ("+isReadOnlyFromEntity+") not consistent with management mode "+mode);
                }
            }
            
            if (it.getManagementSupport().isDeployed()) {
                if (mode.wasNotLoaded()) {
                    // silently bail out
                    return false;
                } else {
                    if (mode.wasPrimary() && mode.isPrimary()) {
                        // active partial rebind; continue
                    } else if (mode.wasReadOnly() && mode.isReadOnly()) {
                        // reload in RO mode
                    } else {
                        // on initial non-RO rebind, should not have any deployed instances
                        log.warn("Already deployed "+it+" when managing "+mode+"/"+initialMode+"; ignoring this and all descendants");
                        return false;
                    }
                }
            }
            
            // check RO status is consistent
            boolean isNowReadOnly = Boolean.TRUE.equals( ((EntityInternal)it).getManagementSupport().isReadOnly() );
            if (mode.isReadOnly()!=isNowReadOnly) {
                throw new IllegalStateException("Read-only status mismatch for "+it+": "+mode+" / RO="+isNowReadOnly);
            }

            allEntities.add(it);
            preManageNonRecursive(it, mode);
            it.getManagementSupport().onManagementStarting( new ManagementTransitionInfo(managementContext, mode) ); 
            return manageNonRecursive(it, mode);
        } };
        boolean isRecursive = true;
        if (initialMode.wasPrimary() && initialMode.isPrimary()) {
            // already managed, so this shouldn't be recursive 
            // (in ActivePartialRebind we cheat, calling in to this method then skipping recursion).
            // it also falls through to here when doing a redundant promotion,
            // in that case we *should* be recursive; determine by checking whether a child exists and is preregistered.
            // the TODO above removing manageRebindRoot in favour of explicit mgmt list would clean this up a lot!
            Entity aChild = Iterables.getFirst(e.getChildren(), null);
            if (aChild!=null && isPreRegistered(aChild)) {
                log.debug("Managing "+e+" in mode "+initialMode+", doing this recursively because a child is preregistered");
            } else {
                log.debug("Managing "+e+" but skipping recursion, as mode is "+initialMode);
                isRecursive = false;
            }
        }
        if (!isRecursive) {
            manageEntity.apply( (EntityInternal)e );
        } else {
            recursively(e, manageEntity);
        }
        
        for (EntityInternal it : allEntities) {
            if (!it.getManagementSupport().isFullyManaged()) {
                ManagementTransitionMode mode = getLastManagementTransitionMode(it.getId());
                ManagementTransitionInfo info = new ManagementTransitionInfo(managementContext, mode);
                
                it.getManagementSupport().onManagementStarted(info);
                managementContext.getRebindManager().getChangeListener().onManaged(it);
            }
        }
    }
    
    @Override
    public void unmanage(final Entity e) {
        unmanage(e, ManagementTransitionMode.guessing(BrooklynObjectManagementMode.MANAGED_PRIMARY, BrooklynObjectManagementMode.NONEXISTENT));
    }
    
    public void unmanage(final Entity e, final ManagementTransitionMode mode) {
        unmanage(e, mode, false);
    }
    
    private void unmanage(final Entity e, ManagementTransitionMode mode, boolean hasBeenReplaced) {
        if (shouldSkipUnmanagement(e)) return;
        final ManagementTransitionInfo info = new ManagementTransitionInfo(managementContext, mode);
        
        if (hasBeenReplaced) {
            // we are unmanaging an old instance after having replaced it
            // don't unmanage or even clear its fields, because there might be references to it
            
            if (mode.wasReadOnly()) {
                // if coming *from* read only; nothing needed
            } else {
                if (!mode.wasPrimary()) {
                    log.warn("Unexpected mode "+mode+" for unmanage-replace "+e+" (applying anyway)");
                }
                // migrating away or in-place active partial rebind:
                ((EntityInternal)e).getManagementSupport().onManagementStopping(info);
                stopTasks(e);
                ((EntityInternal)e).getManagementSupport().onManagementStopped(info);
            }
            // do not remove from maps below, bail out now
            return;
            
        } else if (mode.wasReadOnly() && mode.isNoLongerLoaded()) {
            // we are unmanaging an instance (secondary); either stopping here or primary destroyed elsewhere
            ((EntityInternal)e).getManagementSupport().onManagementStopping(info);
            unmanageNonRecursive(e);
            stopTasks(e);
            ((EntityInternal)e).getManagementSupport().onManagementStopped(info);
            managementContext.getRebindManager().getChangeListener().onUnmanaged(e);
            if (managementContext.getGarbageCollector() != null) managementContext.getGarbageCollector().onUnmanaged(e);
            
        } else if (mode.wasPrimary() && mode.isNoLongerLoaded()) {
            // unmanaging a primary; currently this is done recursively
            
            /* TODO tidy up when it is recursive and when it isn't; if something is being unloaded or destroyed,
             * that probably *is* recursive, but the old mode might be different if in some cases things are read-only.
             * or maybe nothing needs to be recursive, we just make sure the callers (e.g. HighAvailabilityModeImpl.clearManagedItems)
             * call in a good order
             * 
             * see notes above about recursive/manage/All/unmanageAll
             */
            
            // Need to store all child entities as onManagementStopping removes a child from the parent entity
            final List<EntityInternal> allEntities =  Lists.newArrayList();        
            recursively(e, new Predicate<EntityInternal>() { public boolean apply(EntityInternal it) {
                if (shouldSkipUnmanagement(it)) return false;
                allEntities.add(it);
                it.getManagementSupport().onManagementStopping(info);
                return true;
            } });

            for (EntityInternal it : allEntities) {
                if (shouldSkipUnmanagement(it)) continue;
                unmanageNonRecursive(it);
                stopTasks(it);
            }
            for (EntityInternal it : allEntities) {
                it.getManagementSupport().onManagementStopped(info);
                managementContext.getRebindManager().getChangeListener().onUnmanaged(it);
                if (managementContext.getGarbageCollector() != null) managementContext.getGarbageCollector().onUnmanaged(e);
            }
            
        } else {
            log.warn("Invalid mode for unmanage: "+mode+" on "+e+" (ignoring)");
        }
        
        preRegisteredEntitiesById.remove(e.getId());
        preManagedEntitiesById.remove(e.getId());
        entityProxiesById.remove(e.getId());
        entitiesById.remove(e.getId());
        entityModesById.remove(e.getId());
    }
    
    private void stopTasks(Entity entity) {
        stopTasks(entity, null);
    }
    
    /** stops all tasks (apart from any current one or its descendants) on this entity,
     * optionally -- if a timeout is given -- waiting for completion and warning on incomplete tasks */
    @Beta
    public void stopTasks(Entity entity, @Nullable Duration timeout) {
        CountdownTimer timeleft = timeout==null ? null : timeout.countdownTimer();
        // try forcibly interrupting tasks on managed entities
        Collection<Exception> exceptions = MutableSet.of();
        try {
            Set<Task<?>> tasksCancelled = MutableSet.of();
            for (Task<?> t: managementContext.getExecutionContext(entity).getTasks()) {
                if (entity.equals(BrooklynTaskTags.getContextEntity(Tasks.current())) && hasTaskAsAncestor(t, Tasks.current())) {
                    // don't cancel if we are running inside a task on the target entity and
                    // the task being considered is one we have submitted -- e.g. on "stop" don't cancel ourselves!
                    // but if our current task is from another entity we probably do want to cancel them (we are probably invoking unmanage)
                    continue;
                }
                
                if (!t.isDone()) {
                    try {
                        log.debug("Cancelling "+t+" on "+entity);
                        tasksCancelled.add(t);
                        t.cancel(true);
                    } catch (Exception e) {
                        Exceptions.propagateIfFatal(e);
                        log.debug("Error cancelling "+t+" on "+entity+" (will warn when all tasks are cancelled): "+e, e);
                        exceptions.add(e);
                    }
                }
            }
            
            if (timeleft!=null) {
                Set<Task<?>> tasksIncomplete = MutableSet.of();
                // go through all tasks, not just cancelled ones, in case there are previously cancelled ones which are not complete
                for (Task<?> t: managementContext.getExecutionContext(entity).getTasks()) {
                    if (hasTaskAsAncestor(t, Tasks.current()))
                        continue;
                    if (!Tasks.blockUntilInternalTasksEnded(t, timeleft.getDurationRemaining())) {
                        tasksIncomplete.add(t);
                    }
                }
                if (!tasksIncomplete.isEmpty()) {
                    log.warn("Incomplete tasks when stopping "+entity+": "+tasksIncomplete);
                }
                if (log.isTraceEnabled())
                    log.trace("Cancelled "+tasksCancelled+" tasks for "+entity+", with "+
                            timeleft.getDurationRemaining()+" remaining (of "+timeout+"): "+tasksCancelled);
            } else {
                if (log.isTraceEnabled())
                    log.trace("Cancelled "+tasksCancelled+" tasks for "+entity+": "+tasksCancelled);
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Error inspecting tasks to cancel on unmanagement: "+e, e);
        }
        if (!exceptions.isEmpty())
            log.warn("Error when cancelling tasks for "+entity+" on unmanagement: "+Exceptions.create(exceptions));
    }

    private boolean hasTaskAsAncestor(Task<?> t, Task<?> potentialAncestor) {
        if (t==null || potentialAncestor==null) return false;
        if (t.equals(potentialAncestor)) return true;
        return hasTaskAsAncestor(t.getSubmittedByTask(), potentialAncestor);
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
        } else if (Boolean.TRUE.equals(((EntityInternal)entity).getManagementSupport().isReadOnly())) {
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
        Entity otherPreregistered = preRegisteredEntitiesById.get(e.getId());
        if (otherPreregistered!=null) {
            // if something has been pre-registered, prefer it
            // (e.g. if we recursing through children, we might have a proxy from previous iteration;
            // the most recent will have been pre-registered)
            e = otherPreregistered;
        }
            
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
    private synchronized boolean preManageNonRecursive(Entity e, ManagementTransitionMode mode) {
        Entity realE = toRealEntity(e);
        
        Object old = preManagedEntitiesById.put(e.getId(), realE);
        preRegisteredEntitiesById.remove(e.getId());
        
        if (old!=null && mode.wasNotLoaded()) {
            if (old.equals(e)) {
                log.warn("{} redundant call to pre-start management of entity {}, mode {}; ignoring", new Object[] { this, e, mode });
            } else {
                throw new IllegalStateException("call to pre-manage entity "+e+" ("+mode+") but different entity "+old+" already known under that id at "+this);
            }
            return false;
        } else {
            if (log.isTraceEnabled()) log.trace("{} pre-start management of entity {}, mode {}", 
                new Object[] { this, e, mode });
            return true;
        }
    }

    /**
     * Should ensure that the entity is now managed somewhere, and known about in all the lists.
     * Returns true if the entity has now become managed; false if it was already managed (anything else throws exception)
     * @param isOrWasReadOnly 
     */
    private synchronized boolean manageNonRecursive(Entity e, ManagementTransitionMode mode) {
        Entity old = entitiesById.get(e.getId());
        
        if (old!=null && mode.wasNotLoaded()) {
            if (old.equals(e)) {
                log.warn("{} redundant call to start management of entity {}; ignoring", this, e);
            } else {
                throw new IllegalStateException("call to manage entity "+e+" ("+mode+") but different entity "+old+" already known under that id at "+this);
            }
            return false;
        }
        
        BrooklynLogging.log(log, BrooklynLogging.levelDebugOrTraceIfReadOnly(e), 
            "{} starting management of entity {}", this, e);
        Entity realE = toRealEntity(e);
        
        Entity oldProxy = entityProxiesById.get(e.getId());
        Entity proxyE;
        if (oldProxy!=null) {
            if (mode.wasNotLoaded()) {
                throw new IllegalStateException("call to manage entity "+e+" from unloaded state ("+mode+") but already had proxy "+oldProxy+" already known under that id at "+this);
            }
            // make the old proxy point at this new delegate
            // (some other tricks done in the call below)
            ((EntityProxyImpl)(Proxy.getInvocationHandler(oldProxy))).resetDelegate(oldProxy, oldProxy, realE);
            proxyE = oldProxy;
        } else {
            proxyE = toProxyEntityIfAvailable(e);
        }
        entityProxiesById.put(e.getId(), proxyE);
        entityTypes.put(e.getId(), realE.getClass().getName());
        entitiesById.put(e.getId(), realE);

        preManagedEntitiesById.remove(e.getId());
        if ((e instanceof Application) && (e.getParent()==null)) {
            applications.add((Application)proxyE);
            applicationIds.add(e.getId());
        }
        if (!entities.contains(proxyE)) 
            entities.add(proxyE);
        
        if (old!=null && old!=e) {
            // passing the transition info will ensure the right shutdown steps invoked for old instance
            unmanage(old, mode, true);
        }
        
        return true;
    }

    /**
     * Should ensure that the entity is no longer managed anywhere, remove from all lists.
     * Returns true if the entity has been removed from management; if it was not previously managed (anything else throws exception) 
     */
    private boolean unmanageNonRecursive(Entity e) {
        /*
         * When method is synchronized, hit deadlock: 
         * 1. thread called unmanage() on a member of a group, so we got the lock and called group.removeMember;
         *    this ties to synchronize on AbstractGroupImpl.members 
         * 2. another thread was doing AbstractGroupImpl.addMember, which is synchronized on AbstractGroupImpl.members;
         *    it tries to call Entities.manage(child) which calls LocalEntityManager.getEntity(), which is
         *    synchronized on this.
         * 
         * We MUST NOT call alien code from within the management framework while holding locks. 
         * The AbstractGroup.removeMember is effectively alien because a user could override it, and because
         * it is entity specific.
         * 
         * TODO Does getting then removing from groups risk this entity being added to other groups while 
         * this is happening? Should abstractEntity.onManagementStopped or some such remove the entity
         * from its groups?
         */
        
        if (!getLastManagementTransitionMode(e.getId()).isReadOnly()) {
            e.clearParent();
            Collection<Group> groups = e.getGroups();
            for (Group group : groups) {
                if (!Entities.isNoLongerManaged(group)) group.removeMember(e);
            }
            if (e instanceof Group) {
                Collection<Entity> members = ((Group)e).getMembers();
                for (Entity member : members) {
                    if (!Entities.isNoLongerManaged(member)) member.removeGroup((Group)e);
                }
            }
        } else {
            log.debug("No relations being updated on unmanage of read only {}", e);
        }

        synchronized (this) {
            Entity proxyE = toProxyEntityIfAvailable(e);
            if (e instanceof Application) {
                applications.remove(proxyE);
                applicationIds.remove(e.getId());
            }

            entities.remove(proxyE);
            entityProxiesById.remove(e.getId());
            entityModesById.remove(e.getId());
            Object old = entitiesById.remove(e.getId());

            entityTypes.remove(e.getId());
            if (old==null) {
                log.warn("{} call to stop management of unknown entity (already unmanaged?) {}; ignoring", this, e);
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
            // If we don't already know about the proxy, then use the real thing; presumably it's 
            // the legacy way of creating the entity so didn't get a preManage() call

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
        Entity result;
        // prefer the preRegistered and preManaged entities, during hot proxying, they should be newer
        result = preRegisteredEntitiesById.get(id);
        if (result==null)
            result = preManagedEntitiesById.get(id);
        if (result==null)
            entitiesById.get(id);
        return result;
    }
    
    private boolean isRunning() {
        return managementContext.isRunning();
    }

}
