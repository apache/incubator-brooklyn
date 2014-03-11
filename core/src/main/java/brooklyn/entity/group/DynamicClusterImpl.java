package brooklyn.entity.group;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.location.basic.Locations;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 */
public class DynamicClusterImpl extends AbstractGroupImpl implements DynamicCluster {
    private static final Logger LOG = LoggerFactory.getLogger(DynamicClusterImpl.class);

    /**
     * Mutex for synchronizing during re-size operations.
     * Sub-classes should use with great caution, to not introduce deadlocks!
     */
    protected final Object mutex = new Object[0];

    private static final Function<Collection<Entity>, Entity> defaultRemovalStrategy = new Function<Collection<Entity>, Entity>() {
        @Override public Entity apply(Collection<Entity> contenders) {
            // choose newest entity that is stoppable
            long newestTime = 0;
            Entity newest = null;

            for (Entity contender : contenders) {
                if (contender instanceof Startable && contender.getCreationTime() > newestTime) {
                    newest = contender;
                    newestTime = contender.getCreationTime();
                }
            }
            return newest;
        }
    };

    public DynamicClusterImpl() {
    }

    @Override
    public void init() {
        super.init();
        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void setRemovalStrategy(Function<Collection<Entity>, Entity> val) {
        setConfig(REMOVAL_STRATEGY, checkNotNull(val, "removalStrategy"));
    }

    protected Function<Collection<Entity>, Entity> getRemovalStrategy() {
        Function<Collection<Entity>, Entity> result = getConfig(REMOVAL_STRATEGY);
        return (result != null) ? result : defaultRemovalStrategy;
    }

    @Override
    public void setZonePlacementStrategy(NodePlacementStrategy val) {
        setConfig(ZONE_PLACEMENT_STRATEGY, checkNotNull(val, "zonePlacementStrategy"));
    }

    protected NodePlacementStrategy getZonePlacementStrategy() {
        return checkNotNull(getConfig(ZONE_PLACEMENT_STRATEGY), "zonePlacementStrategy config");
    }

    @Override
    public void setZoneFailureDetector(ZoneFailureDetector val) {
        setConfig(ZONE_FAILURE_DETECTOR, checkNotNull(val, "zoneFailureDetector"));
    }

    protected ZoneFailureDetector getZoneFailureDetector() {
        return checkNotNull(getConfig(ZONE_FAILURE_DETECTOR), "zoneFailureDetector config");
    }

    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC);
    }

    /** @deprecated since 0.7.0; use {@link #getMemberSpec()} */
    @Deprecated
    protected EntityFactory<?> getFactory() {
        return getConfig(FACTORY);
    }

    @Override
    public void setMemberSpec(EntitySpec<?> memberSpec) {
        setConfigEvenIfOwned(MEMBER_SPEC, memberSpec);
    }

    /** @deprecated since 0.7.0; use {@link #setMemberSpec(EntitySpec)} */
    @Deprecated
    @Override
    public void setFactory(EntityFactory<?> factory) {
        setConfigEvenIfOwned(FACTORY, factory);
    }

    private Location getLocation() {
        Collection<? extends Location> ll = Locations.getLocationsCheckingAncestors(getLocations(), this);
        return Iterables.getOnlyElement(ll);
    }

    protected boolean isAvailabilityZoneEnabled() {
        return getConfig(ENABLE_AVAILABILITY_ZONES);
    }

    protected boolean isQuarantineEnabled() {
        return getConfig(QUARANTINE_FAILED_ENTITIES);
    }

    protected Group getQuarantineGroup() {
        return getAttribute(QUARANTINE_GROUP);
    }

    protected int getInitialQuorumSize() {
        int initialSize = getConfig(INITIAL_SIZE).intValue();
        int initialQuorumSize = getConfig(INITIAL_QUORUM_SIZE).intValue();
        if (initialQuorumSize < 0) initialQuorumSize = initialSize;
        if (initialQuorumSize > initialSize) {
            LOG.warn("On start of cluster {}, misconfigured initial quorum size {} greater than initial size{}; using {}", new Object[] {initialQuorumSize, initialSize, initialSize});
            initialQuorumSize = initialSize;
        }
        return initialQuorumSize;
    }

    @Override
    public void start(Collection<? extends Location> locsO) {
        if (locsO!=null) {
            checkArgument(locsO.size() <= 1, "Wrong number of locations supplied to start %s: %s", this, locsO);
            addLocations(locsO);
        }
        Location loc = getLocation();

        EntitySpec<?> spec = getConfig(MEMBER_SPEC);
        if (spec!=null) {
            setDefaultDisplayName("Cluster of "+JavaClassNames.simpleClassName(spec.getType())
                +" ("+loc+")"
                );
        }

        if (isAvailabilityZoneEnabled()) {
            setAttribute(SUB_LOCATIONS, findSubLocations(loc));
        }

        setAttribute(SERVICE_STATE, Lifecycle.STARTING);
        try {
            if (isQuarantineEnabled()) {
                Group quarantineGroup = addChild(EntitySpec.create(BasicGroup.class).displayName("quarantine"));
                Entities.manage(quarantineGroup);
                setAttribute(QUARANTINE_GROUP, quarantineGroup);
            }

            int initialSize = getConfig(INITIAL_SIZE).intValue();
            int initialQuorumSize = getInitialQuorumSize();

            resize(initialSize);

            Maybe<Task<?>> firstFailed = Maybe.next(Tasks.failed(Tasks.children(Tasks.current())).iterator());

            int currentSize = getCurrentSize().intValue();
            if (currentSize < initialQuorumSize) {
                String message;
                if (currentSize == 0 && firstFailed.isPresent()) {
                    message = "All nodes in cluster "+this+" failed";
                } else {
                    message = "On start of cluster " + this + ", failed to get to initial size of " + initialSize
                        + "; size is " + getCurrentSize()
                        + (initialQuorumSize != initialSize ? " (initial quorum size is " + initialQuorumSize + ")" : "");
                }
                Throwable firstError = Tasks.getError(firstFailed.orNull());
                if (firstError!=null) message += "; first failure is: "+Exceptions.collapseText(firstError);
                throw new IllegalStateException(message, firstError);
            } else if (currentSize < initialSize) {
                LOG.warn(
                        "On start of cluster {}, size {} reached initial minimum quorum size of {} but did not reach desired size {}; continuing",
                        new Object[] { this, currentSize, initialQuorumSize, initialSize });
            }

            for (Policy it : getPolicies()) {
                it.resume();
            }
            setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
            setAttribute(SERVICE_UP, calculateServiceUp());
        } catch (Exception e) {
            setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }
    }

    protected List<Location> findSubLocations(Location loc) {
        if (!loc.hasExtension(AvailabilityZoneExtension.class)) {
            throw new IllegalStateException("Availability zone extension not supported for location " + loc);
        }

        AvailabilityZoneExtension zoneExtension = loc.getExtension(AvailabilityZoneExtension.class);

        Collection<String> zoneNames = getConfig(AVAILABILITY_ZONE_NAMES);
        Integer numZones = getConfig(NUM_AVAILABILITY_ZONES);

        List<Location> subLocations;
        if (zoneNames == null || zoneNames.isEmpty()) {
            if (numZones != null) {
                subLocations = zoneExtension.getSubLocations(numZones);

                checkArgument(numZones > 0, "numZones must be greater than zero: %s", numZones);
                if (numZones > subLocations.size()) {
                    throw new IllegalStateException("Number of required zones (" + numZones + ") not satisfied in " + loc
                            + "; only " + subLocations.size() + " available: " + subLocations);
                }
            } else {
                subLocations = zoneExtension.getAllSubLocations();
            }
        } else {
            // TODO check that these are valid region / availabilityZones?
            subLocations = zoneExtension.getSubLocationsByName(StringPredicates.equalToAny(zoneNames), zoneNames.size());

            if (zoneNames.size() > subLocations.size()) {
                throw new IllegalStateException("Number of required zones (" + zoneNames.size() + " - " + zoneNames
                        + ") not satisfied in " + loc + "; only " + subLocations.size() + " available: " + subLocations);
            }
        }

        LOG.info("Returning {} sub-locations: {}", subLocations.size(), Iterables.toString(subLocations));
        return subLocations;
    }

    @Override
    public void stop() {
        setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
        try {
            setAttribute(SERVICE_UP, calculateServiceUp());
            for (Policy it : getPolicies()) { it.suspend(); }
            resize(0);

            // also stop any remaining stoppable children -- eg those on fire
            // (this ignores the quarantine node which is not stoppable)
            StartableMethods.stop(this);

            setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
            setAttribute(SERVICE_UP, calculateServiceUp());
        } catch (Exception e) {
            setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer resize(Integer desiredSize) {
        synchronized (mutex) {
            int currentSize = getCurrentSize();
            int delta = desiredSize - currentSize;
            if (delta != 0) {
                LOG.info("Resize {} from {} to {}", new Object[] {this, currentSize, desiredSize});
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("Resize no-op {} from {} to {}", new Object[] {this, currentSize, desiredSize});
            }

            if (delta > 0) {
                grow(delta);
            } else if (delta < 0) {
                shrink(delta);
            }
        }
        return getCurrentSize();
    }

    /**
     * {@inheritDoc}
     *
     * <strong>Note</strong> for sub-clases; this method can be called while synchronized on {@link #mutex}.
     */
    @Override
    public String replaceMember(String memberId) {
        Entity member = getEntityManager().getEntity(memberId);
        LOG.info("In {}, replacing member {} ({})", new Object[] {this, memberId, member});

        if (member == null) {
            throw new NoSuchElementException("In "+this+", entity "+memberId+" cannot be resolved, so not replacing");
        }

        synchronized (mutex) {
            if (!getMembers().contains(member)) {
                throw new NoSuchElementException("In "+this+", entity "+member+" is not a member so not replacing");
            }

            Location memberLoc = null;
            if (isAvailabilityZoneEnabled()) {
                // this entity's member could be a machine provisioned by a sub-location, or the actual sub-location
                List<Location> subLocations = getAttribute(SUB_LOCATIONS);
                Location actualMemberLoc = checkNotNull(Iterables.getOnlyElement(member.getLocations()), "member's location (%s)", member);
                Location contenderMemberLoc = actualMemberLoc;
                boolean foundMatch = false;
                do {
                    if (subLocations.contains(contenderMemberLoc)) {
                        memberLoc = contenderMemberLoc;
                        foundMatch = true;
                        LOG.debug("In {} replacing member {} ({}), inferred its sub-location is {}", new Object[] {this, memberId, member, memberLoc});
                    }
                    contenderMemberLoc = contenderMemberLoc.getParent();
                } while (!foundMatch && contenderMemberLoc != null);
                if (!foundMatch) {
                    memberLoc = actualMemberLoc;
                    LOG.warn("In {} replacing member {} ({}), could not find matching sub-location; falling back to its actual location: {}", new Object[] {this, memberId, member, memberLoc});
                } else if (memberLoc == null) {
                    // impossible to get here, based on logic above!
                    throw new IllegalStateException("Unexpected condition! cluster="+this+"; member="+member+"; actualMemberLoc="+actualMemberLoc);
                }
            } else {
                memberLoc = getLocation();
            }

            Entity replacement = replaceMember(member, memberLoc);
            return replacement.getId();
        }
    }

    protected Entity replaceMember(Entity member, Location memberLoc) {
        synchronized (mutex) {
            Optional<Entity> added = growByOne(memberLoc, ImmutableMap.of());
            if (!added.isPresent()) {
                String msg = String.format("In %s, failed to grow, to replace %s; not removing", this, member);
                throw new IllegalStateException(msg);
            }

            stopAndRemoveNode(member);

            return added.get();
        }
    }

    protected Multimap<Location, Entity> getMembersByLocation() {
        Multimap<Location, Entity> result = LinkedHashMultimap.create();
        for (Entity member : getMembers()) {
            Collection<Location> memberLocs = member.getLocations();
            Location memberLoc = Iterables.getFirst(memberLocs, null);
            if (memberLoc != null) {
                result.put(memberLoc, member);
            }
        }
        return result;
    }

    protected List<Location> getNonFailedSubLocations() {
        List<Location> result = Lists.newArrayList();
        Set<Location> failed = Sets.newLinkedHashSet();
        List<Location> subLocations = getAttribute(SUB_LOCATIONS);
        Set<Location> oldFailedSubLocations = getAttribute(FAILED_SUB_LOCATIONS);
        if (oldFailedSubLocations == null)
            oldFailedSubLocations = ImmutableSet.<Location> of();

        for (Location subLocation : subLocations) {
            if (getZoneFailureDetector().hasFailed(subLocation)) {
                failed.add(subLocation);
            } else {
                result.add(subLocation);
            }
        }

        Set<Location> newlyFailed = Sets.difference(failed, oldFailedSubLocations);
        Set<Location> newlyRecovered = Sets.difference(oldFailedSubLocations, failed);
        setAttribute(FAILED_SUB_LOCATIONS, failed);
        if (newlyFailed.size() > 0) {
            LOG.warn("Detected probably zone failures for {}: {}", this, newlyFailed);
        }
        if (newlyRecovered.size() > 0) {
            LOG.warn("Detected probably zone recoveries for {}: {}", this, newlyRecovered);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <strong>Note</strong> for sub-clases; this method can be called while synchronized on {@link #mutex}.
     */
    @Override
    public Collection<Entity> grow(int delta) {
        synchronized (mutex) {
            // choose locations to be deployed to
            List<Location> chosenLocations;
            if (isAvailabilityZoneEnabled()) {
                List<Location> subLocations = getNonFailedSubLocations();
                Multimap<Location, Entity> membersByLocation = getMembersByLocation();
                chosenLocations = getZonePlacementStrategy().locationsForAdditions(membersByLocation, subLocations, delta);
                if (chosenLocations.size() != delta) {
                    throw new IllegalStateException("Node placement strategy chose " + Iterables.size(chosenLocations)
                            + ", when expected delta " + delta + " in " + this);
                }
            } else {
                chosenLocations = Collections.nCopies(delta, getLocation());
            }

            // create the entities and start them
            List<Entity> addedEntities = Lists.newArrayList();
            Map<Entity, Location> addedEntityLocations = Maps.newLinkedHashMap();
            Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();
            for (Location chosenLocation : chosenLocations) {
                Entity entity = addNode(chosenLocation, ImmutableMap.of());
                addedEntities.add(entity);
                addedEntityLocations.put(entity, chosenLocation);
                Map<String, ?> args = ImmutableMap.of("locations", ImmutableList.of(chosenLocation));
                Task<Void> task = Effectors.invocation(entity, Startable.START, args).asTask();
                tasks.put(entity, task);
            }
            DynamicTasks.queueIfPossible(Tasks.parallel("starting "+tasks.size()+" node"+Strings.s(tasks.size())+" (parallel)", tasks.values())).orSubmitAsync(this);
            Map<Entity, Throwable> errors = waitForTasksOnEntityStart(tasks);

            // if tracking, then report success/fail to the ZoneFailureDetector
            if (isAvailabilityZoneEnabled()) {
                for (Map.Entry<Entity, Location> entry : addedEntityLocations.entrySet()) {
                    Entity entity = entry.getKey();
                    Location loc = entry.getValue();
                    Throwable err = errors.get(entity);
                    if (err == null) {
                        getZoneFailureDetector().onStartupSuccess(loc, entity);
                    } else {
                        getZoneFailureDetector().onStartupFailure(loc, entity, err);
                    }
                }
            }

            // quarantine/cleanup as necessary
            if (!errors.isEmpty()) {
                if (isQuarantineEnabled()) {
                    quarantineFailedNodes(errors.keySet());
                } else {
                    cleanupFailedNodes(errors.keySet());
                }
            }

            return MutableList.<Entity> builder().addAll(addedEntities).removeAll(errors.keySet()).build();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <strong>Note</strong> for sub-clases; this method can be called while synchronized on {@link #mutex}.
     */
    @Override
    public Optional<Entity> growByOne(Location loc, Map<?,?> extraFlags) {
        synchronized (mutex) {
            // TODO remove duplication from #grow(int)
            Entity entity = addNode(loc, extraFlags);
            Map<String, ?> args = ImmutableMap.of("locations", ImmutableList.of(loc));
            Task<?> task = entity.invoke(Startable.START, args);
            Map<Entity, Throwable> errors = waitForTasksOnEntityStart(ImmutableMap.of(entity, task));

            // if tracking, then report success/fail to the ZoneFailureDetector
            if (isAvailabilityZoneEnabled()) {
                Throwable err = errors.get(entity);
                if (err == null) {
                    getZoneFailureDetector().onStartupSuccess(loc, entity);
                } else {
                    getZoneFailureDetector().onStartupFailure(loc, entity, err);
                }
            }

            // quarantine/cleanup as necessary
            if (!errors.isEmpty()) {
                if (isQuarantineEnabled()) {
                    quarantineFailedNodes(ImmutableList.of(entity));
                } else {
                    cleanupFailedNodes(ImmutableList.of(entity));
                }
            }

            if (errors.isEmpty()) {
                return Optional.of(entity);
            } else {
                return Optional.absent();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <strong>Note</strong> for sub-clases; this method can be called while synchronized on {@link #mutex}.
     */
    @Override
    public void shrink(int delta) {
        synchronized (mutex) {
            Collection<Entity> removedEntities = pickAndRemoveMembers(delta * -1);

            // FIXME symmetry in order of added as child, managed, started, and added to group
            // FIXME assume stoppable; use logic of grow?
            Task<?> invoke = Entities.invokeEffector(this, removedEntities, Startable.STOP, Collections.<String,Object>emptyMap());
            try {
                invoke.get();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            } finally {
                for (Entity removedEntity : removedEntities) {
                    discardNode(removedEntity);
                }
            }
        }
    }

    protected void quarantineFailedNodes(Collection<Entity> failedEntities) {
        for (Entity entity : failedEntities) {
            emit(ENTITY_QUARANTINED, entity);
            getQuarantineGroup().addMember(entity);
            removeMember(entity);
        }
    }

    protected void cleanupFailedNodes(Collection<Entity> failedEntities) {
        // TODO Could also call stop on them?
        for (Entity entity : failedEntities) {
            discardNode(entity);
        }
    }

    /**
     * Default impl is to be up when running, and !up otherwise.
     */
    protected boolean calculateServiceUp() {
        return getAttribute(SERVICE_STATE) == Lifecycle.RUNNING;
    }

    protected Map<Entity, Throwable> waitForTasksOnEntityStart(Map<? extends Entity,? extends Task<?>> tasks) {
        // TODO Could have CompoundException, rather than propagating first
        Map<Entity, Throwable> errors = Maps.newLinkedHashMap();

        for (Map.Entry<? extends Entity,? extends Task<?>> entry : tasks.entrySet()) {
            Entity entity = entry.getKey();
            Task<?> task = entry.getValue();
            try {
                task.get();
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            } catch (Throwable t) {
                Throwable interesting = Exceptions.getFirstInteresting(t);
                LOG.error("Cluster "+this+" failed to start entity "+entity+" (removing): "+interesting, interesting);
                LOG.debug("Trace for: Cluster "+this+" failed to start entity "+entity+" (removing): "+t, t);
                // previously we unwrapped but now there is no need I think
                errors.put(entity, t);
            }
        }
        return errors;
    }

    @Override
    public boolean removeChild(Entity child) {
        boolean changed = super.removeChild(child);
        if (changed) {
            removeMember(child);
        }
        return changed;
    }

    protected Map<?,?> getCustomChildFlags() {
        return getConfig(CUSTOM_CHILD_FLAGS);
    }

    /** {@inheritDoc} */
    @Override
    public Entity addNode(Location loc, Map<?,?> extraFlags) {
        Map<?,?> createFlags = MutableMap.builder()
                .putAll(getCustomChildFlags())
                .putAll(extraFlags)
                .build();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating and adding a node to cluster {}({}) with properties {}", new Object[] { this, getId(), createFlags });
        }

        Entity entity = createNode(loc, createFlags);
        Entities.manage(entity);
        addMember(entity);
        return entity;
    }

    protected Entity createNode(@Nullable Location loc, Map<?,?> flags) {
        EntitySpec<?> memberSpec = getMemberSpec();
        if (memberSpec != null) {
            return addChild(EntitySpec.create(memberSpec).configure(flags).location(loc));
        }

        EntityFactory<?> factory = getFactory();
        if (factory == null) {
            throw new IllegalStateException("No member spec nor entity factory supplied for dynamic cluster "+this);
        }
        EntityFactory<?> factoryToUse = (factory instanceof EntityFactoryForLocation) ? ((EntityFactoryForLocation<?>) factory).newFactoryForLocation(loc) : factory;
        Entity entity = factoryToUse.newEntity(flags, this);
        if (entity==null) {
            throw new IllegalStateException("EntityFactory factory routine returned null entity, in "+this);
        }
        if (entity.getParent()==null) entity.setParent(this);

        return entity;
    }

    /** @deprecated since 0.6; use {@link #createNode(Location, Map)}, so can take that location into account when configuring node */
    @Deprecated
    protected Entity createNode(Map<?,?> flags) {
        return createNode(getLocation(), flags);
    }

    protected List<Entity> pickAndRemoveMembers(int delta) {
        if (delta == 1 && !isAvailabilityZoneEnabled()) {
            return ImmutableList.of(pickAndRemoveMember()); // for backwards compatibility in sub-classes
        }

        // TODO inefficient impl
        Preconditions.checkState(getMembers().size() > 0, "Attempt to remove a node when members is empty, from cluster " + this);
        if (LOG.isDebugEnabled()) LOG.debug("Removing a node from {}", this);

        if (isAvailabilityZoneEnabled()) {
            Multimap<Location, Entity> membersByLocation = getMembersByLocation();
            List<Entity> entities = getZonePlacementStrategy().entitiesToRemove(membersByLocation, delta);

            Preconditions.checkState(entities.size() == delta, "Incorrect num entity chosen for removal from %s (%s when expected %s)",
                    getId(), entities.size(), delta);

            for (Entity entity : entities) {
                removeMember(entity);
            }
            return entities;
        } else {
            List<Entity> entities = Lists.newArrayList();
            for (int i = 0; i < delta; i++) {
                entities.add(pickAndRemoveMember());
            }
            return entities;
        }
    }

    /**
     * @deprecated since 0.6.0; subclasses should instead override {@link #pickAndRemoveMembers(int)} if they really need to!
     */
    protected Entity pickAndRemoveMember() {
        assert !isAvailabilityZoneEnabled() : "should instead call pickAndRemoveMembers(int) if using availability zones";

        // TODO inefficient impl
        Preconditions.checkState(getMembers().size() > 0, "Attempt to remove a node when members is empty, from cluster "+this);
        if (LOG.isDebugEnabled()) LOG.debug("Removing a node from {}", this);

        Entity entity = getRemovalStrategy().apply(getMembers());
        Preconditions.checkNotNull(entity, "No entity chosen for removal from "+getId());
        Preconditions.checkState(entity instanceof Startable, "Chosen entity for removal not stoppable: cluster="+this+"; choice="+entity);

        removeMember(entity);
        return entity;
    }

    protected void discardNode(Entity entity) {
        removeMember(entity);
        Entities.unmanage(entity);
    }

    protected void stopAndRemoveNode(Entity member) {
        removeMember(member);

        try {
            if (member instanceof Startable) {
                Task<?> task = member.invoke(Startable.STOP, Collections.<String,Object>emptyMap());
                try {
                    task.get();
                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }
        } finally {
            Entities.unmanage(member);
        }
    }
}
