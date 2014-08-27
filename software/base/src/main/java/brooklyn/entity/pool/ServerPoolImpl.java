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
package brooklyn.entity.pool;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.MachineLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.Machines;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.management.LocationManager;
import brooklyn.management.Task;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.DynamicTasks;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

public class ServerPoolImpl extends DynamicClusterImpl implements ServerPool {

    private static final Logger LOG = LoggerFactory.getLogger(ServerPoolImpl.class);

    private static enum MachinePoolMemberStatus {
        /** The server is available for use */
        AVAILABLE,
        /** The server has been leased to another application */
        CLAIMED,
        /**
         * The server will not be leased to other applications. It will be the first
         * candidate to release when the pool is shrunk.
         */
        UNUSABLE
    }

    private static final AttributeSensor<MachinePoolMemberStatus> SERVER_STATUS = Sensors.newSensor(MachinePoolMemberStatus.class,
            "pool.serverStatus", "The status of an entity in the pool");

    // The sensors here would be better as private fields but there's not really a
    // good way to manage their state when rebinding.

    /** Accesses must be synchronised by mutex */
    // Would use BiMap but persisting them tends to throw ConcurrentModificationExceptions.
    @SuppressWarnings("serial")
    public static final AttributeSensor<Map<Entity, MachineLocation>> ENTITY_MACHINE = Sensors.newSensor(new TypeToken<Map<Entity, MachineLocation>>() {},
            "pool.entityMachineMap", "A mapping of entities and their machine locations");

    @SuppressWarnings("serial")
    public static final AttributeSensor<Map<MachineLocation, Entity>> MACHINE_ENTITY = Sensors.newSensor(new TypeToken<Map<MachineLocation, Entity>>() {},
            "pool.machineEntityMap", "A mapping of machine locations and their entities");

    public static final AttributeSensor<LocationDefinition> DYNAMIC_LOCATION_DEFINITION = Sensors.newSensor(LocationDefinition.class,
            "pool.locationDefinition", "The location definition used to create the pool's dynamic location");

    public static final ConfigKey<Boolean> REMOVABLE = ConfigKeys.newBooleanConfigKey(
            "pool.member.removable", "Whether a pool member is removable from the cluster. Used to denote additional " +
                    "existing machines that were manually added to the pool", true);

    @SuppressWarnings("unused")
    private MemberTrackingPolicy membershipTracker;

    @Override
    public void init() {
        super.init();
        setAttribute(AVAILABLE_COUNT, 0);
        setAttribute(CLAIMED_COUNT, 0);
        setAttribute(ENTITY_MACHINE, Maps.<Entity, MachineLocation>newHashMap());
        setAttribute(MACHINE_ENTITY, Maps.<MachineLocation, Entity>newHashMap());
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        // super.start must happen before the policy is added else the initial
        // members wont be up (and thus have a MachineLocation) when onEntityAdded
        // is called.
        super.start(locations);
        createLocation();
        addMembershipTrackerPolicy();
    }

    @Override
    public void rebind() {
        super.rebind();
        addMembershipTrackerPolicy();
        createLocation();
    }

    @Override
    public void stop() {
        super.stop();
        deleteLocation();
        synchronized (mutex) {
            setAttribute(AVAILABLE_COUNT, 0);
            setAttribute(CLAIMED_COUNT, 0);
            getAttribute(ENTITY_MACHINE).clear();
            getAttribute(MACHINE_ENTITY).clear();
        }
    }

    private void addMembershipTrackerPolicy() {
        membershipTracker = addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName(getDisplayName() + " membership tracker")
                .configure("group", this));
    }

    @Override
    public ServerPoolLocation getDynamicLocation() {
        return (ServerPoolLocation) getAttribute(DYNAMIC_LOCATION);
    }

    protected ServerPoolLocation createLocation() {
        return createLocation(MutableMap.<String, Object>builder()
                .putAll(getConfig(LOCATION_FLAGS))
                .put(DynamicLocation.OWNER.getName(), this)
                .build());
    }

    @Override
    public ServerPoolLocation createLocation(Map<String, ?> flags) {
        String locationName = getConfig(LOCATION_NAME);
        if (locationName == null) {
            String prefix = getConfig(LOCATION_NAME_PREFIX);
            String suffix = getConfig(LOCATION_NAME_SUFFIX);
            locationName = Joiner.on("-").skipNulls().join(prefix, getId(), suffix);
        }

        String locationSpec = String.format(ServerPoolLocationResolver.POOL_SPEC, getId()) + String.format(":(name=\"%s\")", locationName);
        LocationDefinition definition = new BasicLocationDefinition(locationName, locationSpec, flags);
        getManagementContext().getLocationRegistry().updateDefinedLocation(definition);
        Location location = getManagementContext().getLocationRegistry().resolve(definition);
        LOG.info("Resolved and registered dynamic location {}: {}", locationName, location);

        setAttribute(LOCATION_SPEC, locationSpec);
        setAttribute(DYNAMIC_LOCATION, location);
        setAttribute(LOCATION_NAME, location.getId());
        setAttribute(DYNAMIC_LOCATION_DEFINITION, definition);

        return (ServerPoolLocation) location;
    }

    @Override
    public void deleteLocation() {
        LocationManager mgr = getManagementContext().getLocationManager();
        ServerPoolLocation location = getDynamicLocation();
        if (mgr.isManaged(location)) {
            LOG.debug("{} deleting and unmanaging location {}", this, location);
            mgr.unmanage(location);
        }
        // definition will only be null if deleteLocation has already been called, e.g. by two calls to stop().
        LocationDefinition definition = getAttribute(DYNAMIC_LOCATION_DEFINITION);
        if (definition != null) {
            LOG.debug("{} unregistering dynamic location {}", this, definition);
            getManagementContext().getLocationRegistry().removeDefinedLocation(definition.getId());
        }
        setAttribute(LOCATION_SPEC, null);
        setAttribute(DYNAMIC_LOCATION, null);
        setAttribute(LOCATION_NAME, null);
        setAttribute(DYNAMIC_LOCATION_DEFINITION, null);
    }

    @Override
    public boolean isLocationAvailable() {
        // FIXME: What do true/false mean to callers?
        // Is it valid to return false if availableMachines is empty?
        return getDynamicLocation() != null;
    }

    @Override
    public MachineLocation claimMachine(Map<?, ?> flags) throws NoMachinesAvailableException {
        LOG.info("Obtaining machine with flags: {}", Joiner.on(", ").withKeyValueSeparator("=").join(flags));
        synchronized (mutex) {
            Optional<Entity> claimed = getMemberWithStatus(MachinePoolMemberStatus.AVAILABLE);
            if (claimed.isPresent()) {
                setEntityStatus(claimed.get(), MachinePoolMemberStatus.CLAIMED);
                updateCountSensors();
                LOG.debug("{} has been claimed in {}", claimed, this);
                return getEntityMachineMap().get(claimed.get());
            } else {
                throw new NoMachinesAvailableException("No machines available in " + this);
            }
        }
    }

    @Override
    public void releaseMachine(MachineLocation machine) {
        synchronized (mutex) {
            Entity entity = getMachineEntityMap().get(machine);
            if (entity == null) {
                LOG.warn("{} releasing machine {} but its owning entity is not known!", this, machine);
            } else {
                setEntityStatus(entity, MachinePoolMemberStatus.AVAILABLE);
                updateCountSensors();
                LOG.debug("{} has been released in {}", machine, this);
            }
        }
    }

    @Override
    public Entity addExistingMachine(MachineLocation machine) {
        LOG.info("Adding additional machine to {}: {}", this, machine);
        Entity added = addNode(machine, MutableMap.of(REMOVABLE, false));
        Map<String, ?> args = ImmutableMap.of("locations", ImmutableList.of(machine));
        Task<Void> task = Effectors.invocation(added, Startable.START, args).asTask();
        DynamicTasks.queueIfPossible(task).orSubmitAsync(this);
        return added;
    }

    @Override
    public Collection<Entity> addExistingMachinesFromSpec(String spec) {
        Location location = getManagementContext().getLocationRegistry().resolveIfPossible(spec);
        List<Entity> additions = Lists.newLinkedList();
        if (location == null) {
            LOG.warn("Spec was unresolvable: {}", spec);
        } else {
            Iterable<MachineLocation> machines = FluentIterable.from(location.getChildren())
                    .filter(MachineLocation.class);
            LOG.info("{} adding additional machines: {}", this, machines);
            // Doesn't need to be synchronised on mutex: it will be claimed per-machine
            // as the new members are handled by the membership tracking policy.
            for (MachineLocation machine : machines) {
                additions.add(addExistingMachine(machine));
            }
            LOG.debug("{} added additional machines", this);
        }
        return additions;
    }

    /**
     * Overrides to restrict delta to the number of machines that can be <em>safely</em>
     * removed (i.e. those that are {@link MachinePoolMemberStatus#UNUSABLE unusable} or
     * {@link MachinePoolMemberStatus#AVAILABLE available}).
     * <p/>
     * Does not modify delta if the pool is stopping.
     * @param delta Requested number of members to remove
     * @return The entities that were removed
     */
    @Override
    protected Collection<Entity> shrink(int delta) {
        if (Lifecycle.STOPPING.equals(getAttribute(Attributes.SERVICE_STATE_ACTUAL))) {
            return super.shrink(delta);
        }

        synchronized (mutex) {
            int removable = 0;
            for (Entity entity : getMembers()) {
                // Skip machine marked not for removal and machines that are claimed
                if (!Boolean.FALSE.equals(entity.getConfig(REMOVABLE)) &&
                        !MachinePoolMemberStatus.CLAIMED.equals(entity.getAttribute(SERVER_STATUS))) {
                    removable -= 1;
                }
            }

            if (delta < removable) {
                LOG.info("Too few removable machines in {} to shrink by delta {}. Altered delta to {}",
                        new Object[]{this, delta, removable});
                delta = removable;
            }

            Collection<Entity> removed = super.shrink(delta);
            updateCountSensors();
            return removed;
        }
    }
    
    private Map<Entity, MachineLocation> getEntityMachineMap() {
        return getAttribute(ENTITY_MACHINE);
    }

    private Map<MachineLocation, Entity> getMachineEntityMap() {
        return getAttribute(MACHINE_ENTITY);
    }

    @Override
    public Function<Collection<Entity>, Entity> getRemovalStrategy() {
        return UNCLAIMED_REMOVAL_STRATEGY;
    }

    private final Function<Collection<Entity>, Entity> UNCLAIMED_REMOVAL_STRATEGY = new Function<Collection<Entity>, Entity>() {
        // Semantics of superclass mean that mutex should already be held when apply is called
        @Override
        public Entity apply(Collection<Entity> members) {
            synchronized (mutex) {
                Optional<Entity> choice;
                if (Lifecycle.STOPPING.equals(getAttribute(Attributes.SERVICE_STATE_ACTUAL))) {
                    choice = Optional.of(members.iterator().next());
                } else {
                    // Otherwise should only choose between removable + unusable or available
                    choice = getMemberWithStatusExcludingUnremovable(members, MachinePoolMemberStatus.UNUSABLE)
                            .or(getMemberWithStatusExcludingUnremovable(members, MachinePoolMemberStatus.AVAILABLE));
                }
                if (!choice.isPresent()) {
                    LOG.warn("{} has no machines available to remove!", this);
                    return null;
                } else {
                    LOG.info("{} selected entity to remove from pool: {}", this, choice.get());
                    choice.get().getAttribute(SERVER_STATUS);
                    setEntityStatus(choice.get(), null);
                }
                MachineLocation entityLocation = getEntityMachineMap().remove(choice.get());
                if (entityLocation != null) {
                    getMachineEntityMap().remove(entityLocation);
                }
                return choice.get();
            }
        }
    };

    private void serverAdded(Entity member) {
        Maybe<MachineLocation> machine = Machines.findUniqueMachineLocation(member.getLocations());
        if (member.getAttribute(SERVER_STATUS) != null) {
            LOG.debug("Skipped addition of machine already in the pool: {}", member);
        } else if (machine.isPresentAndNonNull()) {
            MachineLocation m = machine.get();
            LOG.info("New machine in {}: {}", this, m);
            setEntityStatus(member, MachinePoolMemberStatus.AVAILABLE);
            synchronized (mutex) {
                getEntityMachineMap().put(member, m);
                getMachineEntityMap().put(m, member);
                updateCountSensors();
            }
        } else {
            LOG.warn("Member added to {} that does not have a machine location; it will not be used by the pool: {}",
                    ServerPoolImpl.this, member);
            setEntityStatus(member, MachinePoolMemberStatus.UNUSABLE);
        }
    }

    private void setEntityStatus(Entity entity, MachinePoolMemberStatus status) {
        ((EntityInternal) entity).setAttribute(SERVER_STATUS, status);
    }

    private Optional<Entity> getMemberWithStatus(MachinePoolMemberStatus status) {
        return getMemberWithStatus0(getMembers(), status, true);
    }

    private Optional<Entity> getMemberWithStatusExcludingUnremovable(Collection<Entity> entities, MachinePoolMemberStatus status) {
        return getMemberWithStatus0(entities, status, false);
    }

    private Optional<Entity> getMemberWithStatus0(Collection<Entity> entities, final MachinePoolMemberStatus status, final boolean includeUnremovableMachines) {
        return Iterables.tryFind(entities,
                new Predicate<Entity>() {
                    @Override
                    public boolean apply(Entity input) {
                        return (includeUnremovableMachines || isRemovable(input)) &&
                                status.equals(input.getAttribute(SERVER_STATUS));
                    }
                });
    }

    /** @return true if the entity has {@link #REMOVABLE} set to null or true. */
    private boolean isRemovable(Entity entity) {
        return !Boolean.FALSE.equals(entity.getConfig(REMOVABLE));
    }

    private void updateCountSensors() {
        synchronized (mutex) {
            int available = 0, claimed = 0;
            for (Entity member : getMembers()) {
                MachinePoolMemberStatus status = member.getAttribute(SERVER_STATUS);
                if (MachinePoolMemberStatus.AVAILABLE.equals(status)) {
                    available++;
                } else if (MachinePoolMemberStatus.CLAIMED.equals(status)) {
                    claimed++;
                }
            }
            setAttribute(AVAILABLE_COUNT, available);
            setAttribute(CLAIMED_COUNT, claimed);
        }
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityEvent(EventType type, Entity member) {
            Boolean isUp = member.getAttribute(Attributes.SERVICE_UP);
            LOG.info("{} in {}: {} service up is {}", new Object[]{type.name(), entity, member, isUp});
            if (type.equals(EventType.ENTITY_ADDED) || type.equals(EventType.ENTITY_CHANGE)) {
                if (Boolean.TRUE.equals(isUp)) {
                    ((ServerPoolImpl) entity).serverAdded(member);
                } else if (LOG.isDebugEnabled()) {
                    LOG.debug("{} observed event {} but {} is not up (yet) and will not be used by the pool",
                            new Object[]{entity, type.name(), member});
                }
            }
        }
    }
}
