package brooklyn.entity.group.zoneaware;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.collect.Multimap;

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 */
@ImplementedBy(InterAvailabilityZoneDynamicClusterImpl.class)
public interface InterAvailabilityZoneDynamicCluster extends DynamicCluster {

    // TODO Prefer NodePlacementStrategy over REMOVAL_STRATEGY
    
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
    
    @SetFromFlag("zoneFailureDetector")
    ConfigKey<ZoneFailureDetector> ZONE_FAILURE_DETECTOR = ConfigKeys.newConfigKey(
            ZoneFailureDetector.class, "dynamiccluster.zoneFailureDetector", "Zone failure detector", new ProportionalZoneFailureDetector(2, Duration.ONE_HOUR, 0.9));
    
    @SetFromFlag("placementStrategy")
    ConfigKey<NodePlacementStrategy> PLACEMENT_STRATEGY = ConfigKeys.newConfigKey(
            NodePlacementStrategy.class, "dynamiccluster.placementStrategy", "Node placement strategy", new BalancingNodePlacementStrategy());
    
    @SetFromFlag("availabilityZoneNames")
    ConfigKey<Collection<String>> AVAILABILITY_ZONE_NAMES = (ConfigKey) ConfigKeys.newConfigKey(
            Collection.class, "dynamiccluster.availabilityZones", "availability zones to use (if non-null, overrides other configuration)", null);
    
    @SetFromFlag("numAvailabilityZones")
    ConfigKey<Integer> NUM_AVAILABILITY_ZONES = ConfigKeys.newIntegerConfigKey(
            "dynamiccluster.numAvailabilityZones", "number of availability zones to use (will attempt to auto-discover this number)", 3);

    AttributeSensor<List<Location>> SUB_LOCATIONS = new BasicAttributeSensor(List.class, "dynamiccluster.subLocations", "Locations for each availability zone to use");
    
    AttributeSensor<Set<Location>> FAILED_SUB_LOCATIONS = new BasicAttributeSensor(Set.class, "dynamiccluster.failedSubLocations", "Sub locations that seem to have failed");
    
    void setPlacementStrategy(NodePlacementStrategy val);
    
    public void setZoneFailureDetector(ZoneFailureDetector val);
}
