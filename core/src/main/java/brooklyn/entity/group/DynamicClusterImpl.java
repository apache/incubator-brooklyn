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
package brooklyn.entity.group;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.DelegateEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.QuorumCheck.QuorumChecks;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.ServiceStateLogic.ServiceProblemsLogic;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.Locations;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.ReferenceWithError;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
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

    // TODO better mechanism for arbitrary class name to instance type coercion
    static {
        TypeCoercions.registerAdapter(String.class, NodePlacementStrategy.class, new Function<String, NodePlacementStrategy>() {
            @Override
            public NodePlacementStrategy apply(final String input) {
                ClassLoader classLoader = NodePlacementStrategy.class.getClassLoader();
                Optional<NodePlacementStrategy> strategy = Reflections.<NodePlacementStrategy>invokeConstructorWithArgs(classLoader, input);
                if (strategy.isPresent()) {
                    return strategy.get();
                } else {
                    throw new IllegalStateException("Failed to create NodePlacementStrategy "+input);
                }
            }
        });
        TypeCoercions.registerAdapter(String.class, ZoneFailureDetector.class, new Function<String, ZoneFailureDetector>() {
            @Override
            public ZoneFailureDetector apply(final String input) {
                ClassLoader classLoader = ZoneFailureDetector.class.getClassLoader();
                Optional<ZoneFailureDetector> detector = Reflections.<ZoneFailureDetector>invokeConstructorWithArgs(classLoader, input);
                if (detector.isPresent()) {
                    return detector.get();
                } else {
                    throw new IllegalStateException("Failed to create ZoneFailureDetector "+input);
                }
            }
        });
    }

    static {
        RendererHints.register(FIRST, RendererHints.namedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
        RendererHints.register(CLUSTER, RendererHints.namedActionWithUrl("Open", DelegateEntity.EntityUrl.entityUrl()));
    }


    private static final Logger LOG = LoggerFactory.getLogger(DynamicClusterImpl.class);

    /**
     * Mutex for synchronizing during re-size operations.
     * Sub-classes should use with great caution, to not introduce deadlocks!
     */
    protected final Object mutex = new Object[0];

    private static final Function<Collection<Entity>, Entity> defaultRemovalStrategy = new Function<Collection<Entity>, Entity>() {
        @Override public Entity apply(Collection<Entity> contenders) {
            // choose newest entity that is stoppable, or if none are stoppable take the newest non-stoppable
            long newestTime = 0;
            Entity newest = null;

            for (Entity contender : contenders) {
                boolean newer = contender.getCreationTime() > newestTime;
                if ((contender instanceof Startable && newer) || 
                    (!(newest instanceof Startable) && ((contender instanceof Startable) || newer))) {
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
    }

    @Override
    protected void initEnrichers() {
        if (getConfigRaw(UP_QUORUM_CHECK, true).isAbsent() && getConfig(INITIAL_SIZE)==0) {
            // if initial size is 0 then override up check to allow zero if empty
            setConfig(UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty());
            setAttribute(SERVICE_UP, true);
        } else {
            setAttribute(SERVICE_UP, false);
        }
        super.initEnrichers();
        // override previous enricher so that only members are checked
        ServiceStateLogic.newEnricherFromChildrenUp().checkMembersOnly().requireUpChildren(getConfig(UP_QUORUM_CHECK)).addTo(this);
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
        try {
            return Iterables.getOnlyElement(ll);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            if (ll.isEmpty()) throw new IllegalStateException("No location available for "+this);
            else throw new IllegalStateException("Ambiguous location for "+this+"; expected one but had "+ll);
        }
    }

    protected boolean isAvailabilityZoneEnabled() {
        return getConfig(ENABLE_AVAILABILITY_ZONES);
    }

    protected boolean isQuarantineEnabled() {
        return getConfig(QUARANTINE_FAILED_ENTITIES);
    }

    protected QuarantineGroup getQuarantineGroup() {
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
            setDefaultDisplayName("Cluster of "+JavaClassNames.simpleClassName(spec.getType()) +" ("+loc+")");
        }

        if (isAvailabilityZoneEnabled()) {
            setAttribute(SUB_LOCATIONS, findSubLocations(loc));
        }

        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        ServiceProblemsLogic.clearProblemsIndicator(this, START);
        try {
            doStart();
            DynamicTasks.waitForLast();
            
        } catch (Exception e) {
            ServiceProblemsLogic.updateProblemsIndicator(this, START, "start failed with error: "+e);
            throw Exceptions.propagate(e);
        } finally {
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        }
    }

    protected void doStart() {
        if (isQuarantineEnabled()) {
            QuarantineGroup quarantineGroup = getAttribute(QUARANTINE_GROUP);
            if (quarantineGroup==null || !Entities.isManaged(quarantineGroup)) {
                quarantineGroup = addChild(EntitySpec.create(QuarantineGroup.class).displayName("quarantine"));
                Entities.manage(quarantineGroup);
                setAttribute(QUARANTINE_GROUP, quarantineGroup);
            }
        }

        int initialSize = getConfig(INITIAL_SIZE).intValue();
        int initialQuorumSize = getInitialQuorumSize();
        Exception internalError = null;

        try {
            resize(initialSize);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            // Apart from logging, ignore problems here; we extract them below.
            // But if it was this thread that threw the exception (rather than a sub-task), then need
            // to record that failure here.
            LOG.debug("Error resizing "+this+" to size "+initialSize+" (collecting and handling): "+e, e);
            internalError = e;
        }

        Iterable<Task<?>> failed = Tasks.failed(Tasks.children(Tasks.current()));
        boolean noFailed = Iterables.isEmpty(failed);
        boolean severalFailed = Iterables.size(failed) > 1;

        int currentSize = getCurrentSize().intValue();
        if (currentSize < initialQuorumSize) {
            String message;
            if (currentSize == 0 && !noFailed) {
                if (severalFailed)
                    message = "All nodes in cluster "+this+" failed";
                else
                    message = "Node in cluster "+this+" failed";
            } else {
                message = "On start of cluster " + this + ", failed to get to initial size of " + initialSize
                    + "; size is " + getCurrentSize()
                    + (initialQuorumSize != initialSize ? " (initial quorum size is " + initialQuorumSize + ")" : "");
            }
            Throwable firstError = Tasks.getError(Maybe.next(failed.iterator()).orNull());
            if (firstError==null && internalError!=null) {
                // only use the internal error if there were no nested task failures
                // (otherwise the internal error should be a wrapper around the nested failures)
                firstError = internalError;
            }
            if (firstError!=null) {
                if (severalFailed) {
                    message += "; first failure is: "+Exceptions.collapseText(firstError);
                } else {
                    message += ": "+Exceptions.collapseText(firstError);
                }
            }
            throw new IllegalStateException(message, firstError);
            
        } else if (currentSize < initialSize) {
            LOG.warn(
                    "On start of cluster {}, size {} reached initial minimum quorum size of {} but did not reach desired size {}; continuing",
                    new Object[] { this, currentSize, initialQuorumSize, initialSize });
        }

        for (Policy it : getPolicies()) {
            it.resume();
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
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        try {
            for (Policy it : getPolicies()) { it.suspend(); }

            // run shrink without mutex to make things stop even if starting,
            int size = getCurrentSize();
            if (size > 0) { shrink(-size); }

            // run resize with mutex to prevent others from starting things
            resize(0);

            // also stop any remaining stoppable children -- eg those on fire
            // (this ignores the quarantine node which is not stoppable)
            StartableMethods.stop(this);

            ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPED);
        } catch (Exception e) {
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
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
            int originalSize = getCurrentSize();
            int delta = desiredSize - originalSize;
            if (delta != 0) {
                LOG.info("Resize {} from {} to {}", new Object[] {this, originalSize, desiredSize});
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("Resize no-op {} from {} to {}", new Object[] {this, originalSize, desiredSize});
            }
            resizeByDelta(delta);
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
                // this member's location could be a machine provisioned by a sub-location, or the actual sub-location
                List<Location> subLocations = findSubLocations(getLocation());
                Collection<Location> actualMemberLocs = member.getLocations();
                boolean foundMatch = false;
                for (Iterator<Location> iter = actualMemberLocs.iterator(); !foundMatch && iter.hasNext();) {
                    Location actualMemberLoc = iter.next();
                    Location contenderMemberLoc = actualMemberLoc;
                    do {
                        if (subLocations.contains(contenderMemberLoc)) {
                            memberLoc = contenderMemberLoc;
                            foundMatch = true;
                            LOG.debug("In {} replacing member {} ({}), inferred its sub-location is {}", new Object[] {this, memberId, member, memberLoc});
                        }
                        contenderMemberLoc = contenderMemberLoc.getParent();
                    } while (!foundMatch && contenderMemberLoc != null);
                }
                if (!foundMatch) {
                    if (actualMemberLocs.isEmpty()) {
                        memberLoc = subLocations.get(0);
                        LOG.warn("In {} replacing member {} ({}), has no locations; falling back to first availability zone: {}", new Object[] {this, memberId, member, memberLoc});
                    } else {
                        memberLoc = Iterables.tryFind(actualMemberLocs, Predicates.instanceOf(MachineProvisioningLocation.class)).or(Iterables.getFirst(actualMemberLocs, null));
                        LOG.warn("In {} replacing member {} ({}), could not find matching sub-location; falling back to its actual location: {}", new Object[] {this, memberId, member, memberLoc});
                    }
                } else if (memberLoc == null) {
                    // impossible to get here, based on logic above!
                    throw new IllegalStateException("Unexpected condition! cluster="+this+"; member="+member+"; actualMemberLocs="+actualMemberLocs);
                }
            } else {
                if (getMemberSpec() != null && getMemberSpec().getLocations().size() > 0) {
                    memberLoc = getMemberSpec().getLocations().iterator().next();
                } else {
                    memberLoc = getLocation();
                }
            }

            Entity replacement = replaceMember(member, memberLoc, ImmutableMap.of());
            return replacement.getId();
        }
    }

    /**
     * @throws StopFailedRuntimeException If stop failed, after successfully starting replacement
     */
    protected Entity replaceMember(Entity member, Location memberLoc, Map<?, ?> extraFlags) {
        synchronized (mutex) {
            ReferenceWithError<Optional<Entity>> added = addInSingleLocation(memberLoc, extraFlags);

            if (!added.getWithoutError().isPresent()) {
                String msg = String.format("In %s, failed to grow, to replace %s; not removing", this, member);
                if (added.hasError())
                    throw new IllegalStateException(msg, added.getError());
                throw new IllegalStateException(msg);
            }

            try {
                stopAndRemoveNode(member);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                throw new StopFailedRuntimeException("replaceMember failed to stop and remove old member "+member.getId(), e);
            }

            return added.getWithError().get();
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
        List<Location> subLocations = findSubLocations(getLocation());
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
        setAttribute(SUB_LOCATIONS, result);
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
     * <strong>Note</strong> for sub-classes; this method can be called while synchronized on {@link #mutex}.
     */
    @Override
    public Collection<Entity> resizeByDelta(int delta) {
        synchronized (mutex) {
            if (delta > 0) {
                return grow(delta);
            } else if (delta < 0) {
                return shrink(delta);
            } else {
                return ImmutableList.<Entity>of();
            }
        }
    }

    /** <strong>Note</strong> for sub-clases; this method can be called while synchronized on {@link #mutex}. */
    protected Collection<Entity> grow(int delta) {
        Preconditions.checkArgument(delta > 0, "Must call grow with positive delta.");

        // choose locations to be deployed to
        List<Location> chosenLocations;
        chosenLocations = getMemberSpec() == null ? null : getMemberSpec().getLocations();
        if (chosenLocations == null || chosenLocations.size() == 0) {
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
        } else {
            // FIXME: Tidy this up!
            chosenLocations = Collections.nCopies(delta, chosenLocations.get(0));
        }

        // create and start the entities
        return addInEachLocation(chosenLocations, ImmutableMap.of()).getWithError();
    }

    /** <strong>Note</strong> for sub-clases; this method can be called while synchronized on {@link #mutex}. */
    @SuppressWarnings("unchecked")
    protected Collection<Entity> shrink(int delta) {
        Preconditions.checkArgument(delta < 0, "Must call shrink with negative delta.");
        int size = getCurrentSize();
        if (-delta > size) {
            // some subclasses (esp in tests) use custom sizes without the members set always being accurate, so put a limit on the size
            LOG.warn("Call to shrink "+this+" by "+delta+" when size is "+size+"; amending");
            delta = -size;
        }
        if (delta==0) return ImmutableList.<Entity>of();

        Collection<Entity> removedEntities = pickAndRemoveMembers(delta * -1);

        // FIXME symmetry in order of added as child, managed, started, and added to group
        Task<?> invoke = Entities.invokeEffector(this, (Iterable<Entity>)(Iterable<?>)Iterables.filter(removedEntities, Startable.class), Startable.STOP, Collections.<String,Object>emptyMap());
        try {
            invoke.get();
            return removedEntities;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } finally {
            for (Entity removedEntity : removedEntities) {
                discardNode(removedEntity);
            }
        }
    }

    protected ReferenceWithError<Optional<Entity>> addInSingleLocation(Location location, Map<?,?> flags) {
        ReferenceWithError<Collection<Entity>> added = addInEachLocation(ImmutableList.of(location), flags);
        
        Optional<Entity> result = Iterables.isEmpty(added.getWithoutError()) ? Optional.<Entity>absent() : Optional.of(Iterables.getOnlyElement(added.get()));
        if (!added.hasError()) {
            return ReferenceWithError.newInstanceWithoutError( result );
        } else {
            if (added.masksErrorIfPresent()) {
                return ReferenceWithError.newInstanceMaskingError( result, added.getError() );
            } else {
                return ReferenceWithError.newInstanceThrowingError( result, added.getError() );
            }
        }
    }

    protected ReferenceWithError<Collection<Entity>> addInEachLocation(Iterable<Location> locations, Map<?,?> flags) {
        List<Entity> addedEntities = Lists.newArrayList();
        Map<Entity, Location> addedEntityLocations = Maps.newLinkedHashMap();
        Map<Entity, Task<?>> tasks = Maps.newLinkedHashMap();

        for (Location loc : locations) {
            Entity entity = addNode(loc, flags);
            addedEntities.add(entity);
            addedEntityLocations.put(entity, loc);
            if (entity instanceof Startable) {
                Map<String, ?> args = ImmutableMap.of("locations", ImmutableList.of(loc));
                Task<Void> task = Effectors.invocation(entity, Startable.START, args).asTask();
                tasks.put(entity, task);
            }
        }

        Task<List<?>> parallel = Tasks.parallel("starting "+tasks.size()+" node"+Strings.s(tasks.size())+" (parallel)", tasks.values());
        TaskTags.markInessential(parallel);
        DynamicTasks.queueIfPossible(parallel).orSubmitAsync(this);
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
        
        Collection<Entity> result = MutableList.<Entity> builder()
            .addAll(addedEntities)
            .removeAll(errors.keySet())
            .build();

        // quarantine/cleanup as necessary
        if (!errors.isEmpty()) {
            if (isQuarantineEnabled()) {
                quarantineFailedNodes(errors.keySet());
            } else {
                cleanupFailedNodes(errors.keySet());
            }
            return ReferenceWithError.newInstanceMaskingError(result, Exceptions.create(errors.values()));
        }

        return ReferenceWithError.newInstanceWithoutError(result);
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

        ((EntityLocal) entity).setAttribute(CLUSTER_MEMBER, true);
        ((EntityLocal) entity).setAttribute(CLUSTER, this);

        Entities.manage(entity);
        addMember(entity);
        return entity;
    }

    protected Entity createNode(@Nullable Location loc, Map<?,?> flags) {
        EntitySpec<?> memberSpec = null;
        if (getMembers().isEmpty()) memberSpec = getConfig(FIRST_MEMBER_SPEC);
        if (memberSpec == null) memberSpec = getMemberSpec();
        
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
        if (delta==0) 
            return Lists.newArrayList();
        
        if (delta == 1 && !isAvailabilityZoneEnabled()) {
            Maybe<Entity> member = tryPickAndRemoveMember();
            return (member.isPresent()) ? ImmutableList.of(member.get()) : ImmutableList.<Entity>of();
        }

        // TODO inefficient impl
        Preconditions.checkState(getMembers().size() > 0, "Attempt to remove a node (delta "+delta+") when members is empty, from cluster " + this);
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
                // don't assume we have enough members; e.g. if shrinking to zero and someone else concurrently stops a member,
                // then just return what we were able to remove.
                Maybe<Entity> member = tryPickAndRemoveMember();
                if (member.isPresent()) entities.add(member.get());
            }
            return entities;
        }
    }

    private Maybe<Entity> tryPickAndRemoveMember() {
        assert !isAvailabilityZoneEnabled() : "should instead call pickAndRemoveMembers(int) if using availability zones";

        // TODO inefficient impl
        Collection<Entity> members = getMembers();
        if (members.isEmpty()) return Maybe.absent();

        if (LOG.isDebugEnabled()) LOG.debug("Removing a node from {}", this);
        Entity entity = getRemovalStrategy().apply(members);
        Preconditions.checkNotNull(entity, "No entity chosen for removal from "+getId());

        removeMember(entity);
        return Maybe.of(entity);
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
