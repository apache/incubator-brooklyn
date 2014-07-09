package brooklyn.entity.group;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.zoneaware.BalancingNodePlacementStrategy;
import brooklyn.entity.group.zoneaware.ProportionalZoneFailureDetector;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.MemberReplaceable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;

/**
 * A {@link Cluster} of entities that can dynamically increase or decrease the number of members.
 * <p>
 * When quarantine is enabled:
 * <ul>
 *   <li>The cluster will have a child entity named <em>quarantine</em>, which is a {@link Group}
 *       for nodes that failed to start correctly.
 *   <li>The cluster's other children will be all nodes in the cluster (that have not been
 *       unmanaged/deleted).
 *   <li>The cluster's members will be all live nodes in the cluster.
 *   <li>The <em>quarantine</em> group's members will be any problem nodes (all nodes that failed
 *       to start correctly)
 * </ul>
 * When quarantine is disabled, the cluster will not have a <em>quarantine</em> child. Nodes that
 * fail to start will be removed from the cluster (i.e. stopped and deleted).
 * <p>
 * Advanced users will wish to examine the configuration for the {@link NodePlacementStrategy} and
 * {@link ZoneFailureDetector} interfaces and their implementations, which are used here to control
 * the placement of nodes in particular availability zones and locations when the cluster is resized.
 * 
 * @see DynamicGroup
 * @see DynamicFabric
 */
// TODO document use of advanced availability zone configuration and features
@ImplementedBy(DynamicClusterImpl.class)
public interface DynamicCluster extends AbstractGroup, Cluster, MemberReplaceable {

    @Beta
    interface NodePlacementStrategy {
        List<Location> locationsForAdditions(Multimap<Location, Entity> currentMembers, Collection<? extends Location> locs, int numToAdd);
        List<Entity> entitiesToRemove(Multimap<Location, Entity> currentMembers, int numToRemove);
    }

    @Beta
    interface ZoneFailureDetector {
        // TODO Would like to add entity-down reporting
        // TODO Should we push any of this into the AvailabilityZoneExtension, rather than on the dynamic cluster?
        void onStartupSuccess(Location loc, Entity entity);
        void onStartupFailure(Location loc, Entity entity, Throwable cause);
        boolean hasFailed(Location loc);
    }

    MethodEffector<Collection<Entity>> RESIZE_BY_DELTA = new MethodEffector<Collection<Entity>>(DynamicCluster.class, "resizeByDelta");

    @SetFromFlag("quarantineFailedEntities")
    ConfigKey<Boolean> QUARANTINE_FAILED_ENTITIES = ConfigKeys.newBooleanConfigKey(
            "dynamiccluster.quarantineFailedEntities", "If true, will quarantine entities that fail to start; if false, will get rid of them (i.e. delete them)", true);

    AttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;

    BasicNotificationSensor<Entity> ENTITY_QUARANTINED = new BasicNotificationSensor<Entity>(Entity.class, "dynamiccluster.entityQuarantined", "Entity failed to start, and has been quarantined");

    AttributeSensor<QuarantineGroup> QUARANTINE_GROUP = Sensors.newSensor(QuarantineGroup.class, "dynamiccluster.quarantineGroup", "Group of quarantined entities that failed to start");

    @SetFromFlag("initialQuorumSize")
    ConfigKey<Integer> INITIAL_QUORUM_SIZE = ConfigKeys.newIntegerConfigKey(
            "cluster.initial.quorumSize",
            "Initial cluster quorum size - number of initial nodes that must have been successfully started to report success (if < 0, then use value of INITIAL_SIZE)",
            -1);

    @SetFromFlag("memberSpec")
    ConfigKey<EntitySpec<?>> MEMBER_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<?>>() { },
            "dynamiccluster.memberspec", "entity spec for creating new cluster members", null);

    /** @deprecated since 0.7.0; use {@link #MEMBER_SPEC} instead. */
    @Deprecated
    @SetFromFlag("factory")
    ConfigKey<EntityFactory> FACTORY = ConfigKeys.newConfigKey(
            EntityFactory.class, "dynamiccluster.factory", "factory for creating new cluster members", null);

    @SetFromFlag("removalStrategy")
    ConfigKey<Function<Collection<Entity>, Entity>> REMOVAL_STRATEGY = ConfigKeys.newConfigKey(
            new TypeToken<Function<Collection<Entity>, Entity>>() {},
            "dynamiccluster.removalstrategy", "strategy for deciding what to remove when down-sizing", null);

    @SetFromFlag("customChildFlags")
    ConfigKey<Map> CUSTOM_CHILD_FLAGS = ConfigKeys.newConfigKey(
            Map.class, "dynamiccluster.customChildFlags", "Additional flags to be passed to children when they are being created", ImmutableMap.of());

    @SetFromFlag("enableAvailabilityZones")
    ConfigKey<Boolean> ENABLE_AVAILABILITY_ZONES = ConfigKeys.newBooleanConfigKey(
            "dynamiccluster.zone.enable", "Whether to use availability zones, or just deploy everything into the generic location", false);

    @SetFromFlag("zoneFailureDetector")
    ConfigKey<ZoneFailureDetector> ZONE_FAILURE_DETECTOR = ConfigKeys.newConfigKey(
            ZoneFailureDetector.class, "dynamiccluster.zone.failureDetector", "Zone failure detector", new ProportionalZoneFailureDetector(2, Duration.ONE_HOUR, 0.9));

    @SetFromFlag("zonePlacementStrategy")
    ConfigKey<NodePlacementStrategy> ZONE_PLACEMENT_STRATEGY = ConfigKeys.newConfigKey(
            NodePlacementStrategy.class, "dynamiccluster.zone.placementStrategy", "Node placement strategy", new BalancingNodePlacementStrategy());

    @SetFromFlag("availabilityZoneNames")
    ConfigKey<Collection<String>> AVAILABILITY_ZONE_NAMES = ConfigKeys.newConfigKey(
            new TypeToken<Collection<String>>() {},
            "dynamiccluster.availabilityZones", "availability zones to use (if non-null, overrides other configuration)", null);

    @SetFromFlag("numAvailabilityZones")
    ConfigKey<Integer> NUM_AVAILABILITY_ZONES = ConfigKeys.newIntegerConfigKey(
            "dynamiccluster.numAvailabilityZones", "number of availability zones to use (will attempt to auto-discover this number)");

    AttributeSensor<List<Location>> SUB_LOCATIONS = new BasicAttributeSensor<List<Location>>(
            new TypeToken<List<Location>>() {},
            "dynamiccluster.subLocations", "Locations for each availability zone to use");

    AttributeSensor<Set<Location>> FAILED_SUB_LOCATIONS = new BasicAttributeSensor<Set<Location>>(
            new TypeToken<Set<Location>>() {},
            "dynamiccluster.failedSubLocations", "Sub locations that seem to have failed");

    /**
     * Changes the cluster size by the given number.
     *
     * @param delta number of nodes to add or remove
     * @return successfully added or removed nodes
     * @see #grow(int)
     */
    @Effector(description="Changes the size of the cluster.")
    Collection<Entity> resizeByDelta(@EffectorParam(name="delta", description="The change in number of nodes") int delta);

    /**
     * Adds a node to the cluster in a single {@link Location}
     */
    Optional<Entity> addInSingleLocation(Location loc, Map<?,?> extraFlags);

    /**
     * Adds a node to the cluster in each {@link Location}
     */
    Collection<Entity> addInEachLocation(Iterable<Location> locs, Map<?,?> extraFlags);

    void setRemovalStrategy(Function<Collection<Entity>, Entity> val);

    void setZonePlacementStrategy(NodePlacementStrategy val);

    void setZoneFailureDetector(ZoneFailureDetector val);

    void setMemberSpec(EntitySpec<?> memberSpec);

    /** @deprecated since 0.7.0; use {@link #setMemberSpec(EntitySpec)} */
    @Deprecated
    void setFactory(EntityFactory<?> factory);
}
