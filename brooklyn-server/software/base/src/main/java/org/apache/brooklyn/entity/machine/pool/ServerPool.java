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
package org.apache.brooklyn.entity.machine.pool;

import java.util.Collection;
import java.util.Map;

import com.google.common.annotations.Beta;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.location.dynamic.LocationOwner;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.machine.MachineEntity;

/**
 * A preallocated server pool is an entity that other applications can deploy to.
 * Behaving as a cluster, the machines it creates for its members are reused.
 * <p/>
 * Notes:
 * <ul>
 *     <li>
 *         The pool does not configure ports appropriately for applications subsequently
 *         deployed. If an entity that is to be run in the pool requires any ports open
 *         other than port 22 then thoses port should be configured with the
 *         {@link CloudLocationConfig#INBOUND_PORTS INBOUND_PORTS}
 *         config key as part of the pool's
 *         {@link org.apache.brooklyn.entity.software.base.SoftwareProcess#PROVISIONING_PROPERTIES PROVISIONING_PROPERTIES}.
 *         For example, in YAML:
 *         <pre>
 *     - type: org.apache.brooklyn.entity.machine.pool.ServerPool
 *       brooklyn.config:
 *         # Suitable for TomcatServers
 *         provisioning.properties:
 *         inboundPorts: [22, 31880, 8443, 8080, 31001, 1099]
 *         </pre>
 *         This is a limitation of Brooklyn that will be addressed in a future release.
 *     </li>
 * </ul>
 */
@Catalog(name="Server Pool", description="Creates a pre-allocated server pool, which other applications can deploy to")
@ImplementedBy(ServerPoolImpl.class)
@Beta
public interface ServerPool extends DynamicCluster, LocationOwner<ServerPoolLocation, ServerPool> {

    ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.INITIAL_SIZE, 2);

    AttributeSensor<Integer> AVAILABLE_COUNT = Sensors.newIntegerSensor(
            "pool.available", "The number of locations in the pool that are unused");

    AttributeSensor<Integer> CLAIMED_COUNT = Sensors.newIntegerSensor(
            "pool.claimed", "The number of locations in the pool that are in use");

    ConfigKey<EntitySpec<?>> MEMBER_SPEC = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.MEMBER_SPEC,
            EntitySpec.create(MachineEntity.class));

    MethodEffector<Collection<Entity>> ADD_MACHINES_FROM_SPEC = new MethodEffector<Collection<Entity>>(ServerPool.class, "addExistingMachinesFromSpec");

    public MachineLocation claimMachine(Map<?, ?> flags) throws NoMachinesAvailableException;

    public void releaseMachine(MachineLocation machine);

    /**
     * Sets the pool to use an existing {@link MachineLocation} as a member. Existing locations
     * will count towards the capacity of the pool but will not be terminated when the pool is
     * stopped.
     * @param machine An existing machine.
     * @return the new member of the pool, created with the configured {@link #MEMBER_SPEC}.
     */
    public Entity addExistingMachine(MachineLocation machine);

    /**
     * Adds additional machines to the pool by resolving the given spec.
     * @param spec
     *          A location spec, e.g. <code>byon:(hosts="user@10.9.1.1,user@10.9.1.2,user@10.9.1.3")</code>
     * @return the new members of the pool, created with the configured {@link #MEMBER_SPEC}.
     */
    @Effector(description = "Adds additional machines to the pool by resolving the given spec.")
    public Collection<Entity> addExistingMachinesFromSpec(
            @EffectorParam(name = "spec", description = "Spec") String spec);
}
