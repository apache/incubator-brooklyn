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
package org.apache.brooklyn.entity.group;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.factory.EntityFactory;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.MemberReplaceable;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.sensor.BasicNotificationSensor;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.zoneaware.BalancingNodePlacementStrategy;
import org.apache.brooklyn.entity.group.zoneaware.ProportionalZoneFailureDetector;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
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
@SuppressWarnings("serial")
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

    @SetFromFlag("restartMode")
    ConfigKey<String> RESTART_MODE = ConfigKeys.newStringConfigKey(
            "dynamiccluster.restartMode", 
            "How this cluster should handle restarts; "
            + "by default it is disallowed, but this key can specify a different mode. "
            + "Modes supported by dynamic cluster are 'off', 'sequqential', or 'parallel'. "
            + "However subclasses can define their own modes or may ignore this.", null);

    @SetFromFlag("quarantineFailedEntities")
    ConfigKey<Boolean> QUARANTINE_FAILED_ENTITIES = ConfigKeys.newBooleanConfigKey(
            "dynamiccluster.quarantineFailedEntities", "If true, will quarantine entities that fail to start; if false, will get rid of them (i.e. delete them)", true);

    @SetFromFlag("quarantineFilter")
    ConfigKey<Predicate<? super Throwable>> QUARANTINE_FILTER = ConfigKeys.newConfigKey(
            new TypeToken<Predicate<? super Throwable>>() {},
            "dynamiccluster.quarantineFilter", 
            "Quarantine the failed nodes that pass this filter (given the exception thrown by the node). "
                    + "Default is those that did not fail with NoMachinesAvailableException "
                    + "(Config ignored if quarantineFailedEntities is false)", 
            null);

    AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = Attributes.SERVICE_STATE_ACTUAL;

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

    @SetFromFlag("firstMemberSpec")
    ConfigKey<EntitySpec<?>> FIRST_MEMBER_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<?>>() { },
            "dynamiccluster.firstmemberspec", "entity spec for creating new cluster members, used for the very first member if different", null);

    /** @deprecated since 0.7.0; use {@link #MEMBER_SPEC} instead. */
    @SuppressWarnings("rawtypes")
    @Deprecated
    @SetFromFlag("factory")
    ConfigKey<EntityFactory> FACTORY = ConfigKeys.newConfigKey(
            EntityFactory.class, "dynamiccluster.factory", "factory for creating new cluster members", null);

    @SetFromFlag("removalStrategy")
    ConfigKey<Function<Collection<Entity>, Entity>> REMOVAL_STRATEGY = ConfigKeys.newConfigKey(
            new TypeToken<Function<Collection<Entity>, Entity>>() {},
            "dynamiccluster.removalstrategy", "strategy for deciding what to remove when down-sizing", null);

    @SuppressWarnings("rawtypes")
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

    @SetFromFlag("clusterMemberId")
    ConfigKey<Integer> CLUSTER_MEMBER_ID = ConfigKeys.newIntegerConfigKey(
            "cluster.member.id", "The unique ID number (sequential) of a member of a cluster");

    AttributeSensor<List<Location>> SUB_LOCATIONS = new BasicAttributeSensor<List<Location>>(
            new TypeToken<List<Location>>() {},
            "dynamiccluster.subLocations", "Locations for each availability zone to use");

    AttributeSensor<Set<Location>> FAILED_SUB_LOCATIONS = new BasicAttributeSensor<Set<Location>>(
            new TypeToken<Set<Location>>() {},
            "dynamiccluster.failedSubLocations", "Sub locations that seem to have failed");

    AttributeSensor<Boolean> CLUSTER_MEMBER = Sensors.newBooleanSensor(
            "cluster.member", "Set on an entity if it is a member of a cluster");

    AttributeSensor<Entity> CLUSTER = Sensors.newSensor(Entity.class,
            "cluster.entity", "The cluster an entity is a member of");

    AttributeSensor<Boolean> CLUSTER_ONE_AND_ALL_MEMBERS_UP = Sensors.newBooleanSensor(
            "cluster.one_and_all.members.up", "True cluster is running, there is on member, and all members are service.isUp");

    /**
     * Changes the cluster size by the given number.
     *
     * @param delta number of nodes to add or remove
     * @return successfully added or removed nodes
     * @see #grow(int)
     */
    @Effector(description="Changes the size of the cluster.")
    Collection<Entity> resizeByDelta(@EffectorParam(name="delta", description="The change in number of nodes") int delta);

    void setRemovalStrategy(Function<Collection<Entity>, Entity> val);

    void setZonePlacementStrategy(NodePlacementStrategy val);

    void setZoneFailureDetector(ZoneFailureDetector val);

    void setMemberSpec(EntitySpec<?> memberSpec);

    /** @deprecated since 0.7.0; use {@link #setMemberSpec(EntitySpec)} */
    @Deprecated
    void setFactory(EntityFactory<?> factory);

    Entity addNode(Location loc, Map<?,?> extraFlags);
}
