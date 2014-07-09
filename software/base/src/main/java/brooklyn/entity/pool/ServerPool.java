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

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.machine.MachineEntity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.MachineLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.dynamic.LocationOwner;

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
 *         {@link brooklyn.location.cloud.CloudLocationConfig#INBOUND_PORTS INBOUND_PORTS}
 *         config key as part of the pool's
 *         {@link brooklyn.entity.basic.SoftwareProcess#PROVISIONING_PROPERTIES PROVISIONING_PROPERTIES}.
 *         For example, in YAML:
 *         <pre>
 *     - type: brooklyn.entity.pool.ServerPool
 *       brooklyn.config:
 *         # Suitable for TomcatServers
 *         provisioning.properties:
 *         inboundPorts: [22, 31880, 8443, 8080, 31001, 1099]
 *         </pre>
 *         This is a limitation of Brooklyn that will be addressed in a future release.
 *     </li>
 * </ul>
 */
@ImplementedBy(ServerPoolImpl.class)
public interface ServerPool extends DynamicCluster, LocationOwner<ServerPoolLocation, ServerPool> {

    ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.INITIAL_SIZE, 2);

    AttributeSensor<Integer> AVAILABLE_COUNT = Sensors.newIntegerSensor(
            "pool.available", "The number of locations in the pool that are unused");

    AttributeSensor<Integer> CLAIMED_COUNT = Sensors.newIntegerSensor(
            "pool.claimed", "The number of locations in the pool that are in use");

    ConfigKey<EntitySpec<?>> MEMBER_SPEC = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.MEMBER_SPEC,
            EntitySpec.create(MachineEntity.class));

    public MachineLocation claimMachine(Map<?, ?> flags) throws NoMachinesAvailableException;

    public void releaseMachine(MachineLocation machine);

}
