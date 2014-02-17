package brooklyn.entity.group;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.zoneaware.BalancingNodePlacementStrategy;
import brooklyn.entity.group.zoneaware.ProportionalZoneFailureDetector;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.location.Location;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 * 
 * When quarantine is enabled:
 * <ul
 *   <li> The DynamicCluster will have a child entity named "quarantine", which is a group for nodes that failed to start correctly.
 *   <li> The DynamicCluster's other children will be all nodes in the cluster (that have not been unmanaged/deleted).
 *   <li> The DynamicCluster's members will be all live nodes in the cluster.
 *   <li> The Quarantine group's members will be all problem nodes (all nodes that failed to start correctly)
 * </ul> 
 * 
 * When quarantine is disabled, the DynamicCluster will not have a "quarantine" child. Nodes that fail to start will be 
 * removed from the cluster (i.e. stopped and deleted).
 */
@ImplementedBy(DynamicClusterImpl.class)
public interface DynamicCluster extends AbstractGroup, Cluster {

    @Beta
    public interface NodePlacementStrategy {
        List<Location> locationsForAdditions(Multimap<Location, Entity> currentMembers, Collection<? extends Location> locs, int numToAdd);
        List<Entity> entitiesToRemove(Multimap<Location, Entity> currentMembers, int numToRemove);
    }
    
    @Beta
    public interface ZoneFailureDetector {
        // TODO Would like to add entity-down reporting
        // TODO Should we push any of this into the AvailabilityZoneExtension, rather than on the dynamic cluster?
        void onStartupSuccess(Location loc, Entity entity);
        void onStartupFailure(Location loc, Entity entity, Throwable cause);
        boolean hasFailed(Location loc);
    }
    
    public static final MethodEffector<String> REPLACE_MEMBER = new MethodEffector<String>(DynamicCluster.class, "replaceMember");

    @SetFromFlag("quarantineFailedEntities")
    public static final ConfigKey<Boolean> QUARANTINE_FAILED_ENTITIES = new BasicConfigKey<Boolean>(
            Boolean.class, "dynamiccluster.quarantineFailedEntities", "If true, will quarantine entities that fail to start; if false, will get rid of them (i.e. delete them)", true);

    public static final AttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;

    public static final BasicNotificationSensor<Entity> ENTITY_QUARANTINED = new BasicNotificationSensor<Entity>(Entity.class, "dynamiccluster.entityQuarantined", "Entity failed to start, and has been quarantined");

    public static final AttributeSensor<Group> QUARANTINE_GROUP = new BasicAttributeSensor<Group>(Group.class, "dynamiccluster.quarantineGroup", "Group of quarantined entities that failed to start");
    
    @SetFromFlag("initialQuorumSize")
    ConfigKey<Integer> INITIAL_QUORUM_SIZE = ConfigKeys.newIntegerConfigKey(
            "cluster.initial.quorumSize",
            "Initial cluster quorum size - number of initial nodes that must have been successfully started to report success (if < 0, then use value of INITIAL_SIZE)", 
            -1);

    @SetFromFlag("memberSpec")
    public static final ConfigKey<EntitySpec<?>> MEMBER_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<?>>() {
            }, "dynamiccluster.memberspec", "entity spec for creating new cluster members", null);

    @SetFromFlag("factory")
    public static final ConfigKey<EntityFactory> FACTORY = new BasicConfigKey<EntityFactory>(
            EntityFactory.class, "dynamiccluster.factory", "factory for creating new cluster members", null);

    @SetFromFlag("removalStrategy")
    public static final ConfigKey<Function<Collection<Entity>, Entity>> REMOVAL_STRATEGY = ConfigKeys.newConfigKey(
            new TypeToken<Function<Collection<Entity>, Entity>>() {},
            "dynamiccluster.removalstrategy", "strategy for deciding what to remove when down-sizing", null);
    
    @SetFromFlag("customChildFlags")
    public static final ConfigKey<Map> CUSTOM_CHILD_FLAGS = new BasicConfigKey<Map>(
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
            "dynamiccluster.numAvailabilityZones", "number of availability zones to use (will attempt to auto-discover this number)", 3);

    AttributeSensor<List<Location>> SUB_LOCATIONS = new BasicAttributeSensor<List<Location>>(
            new TypeToken<List<Location>>() {},
            "dynamiccluster.subLocations", "Locations for each availability zone to use");
    
    AttributeSensor<Set<Location>> FAILED_SUB_LOCATIONS = new BasicAttributeSensor<Set<Location>>(
            new TypeToken<Set<Location>>() {},
            "dynamiccluster.failedSubLocations", "Sub locations that seem to have failed");
    
    /**
     * 
     * @param memberId
     * @throws NoSuchElementException If entity cannot be resolved, or it is not a member 
     */
    @Effector(description="Replaces the entity with the given ID, if it is a member; first adds a new member, then removes this one. "+
            "Returns id of the new entity; or throws exception if couldn't be replaced.")
    public String replaceMember(@EffectorParam(name="memberId", description="The entity id of a member to be replaced") String memberId);
    
    public void setRemovalStrategy(Function<Collection<Entity>, Entity> val);

    void setZonePlacementStrategy(NodePlacementStrategy val);
    
    public void setZoneFailureDetector(ZoneFailureDetector val);

    public void setMemberSpec(EntitySpec<?> memberSpec);
    
    public void setFactory(EntityFactory<?> factory);
}
