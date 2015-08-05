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
package brooklyn.entity.nosql.riak;

import java.net.URI;
import java.util.Map;

import org.apache.brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.reflect.TypeToken;

@Catalog(name="Riak Cluster", description="Riak is a distributed NoSQL key-value data store that offers "
        + "extremely high availability, fault tolerance, operational simplicity and scalability.")
@ImplementedBy(RiakClusterImpl.class)
public interface RiakCluster extends DynamicCluster {

    @SuppressWarnings("serial")
    AttributeSensor<Map<Entity, String>> RIAK_CLUSTER_NODES = Sensors.newSensor(
            new TypeToken<Map<Entity, String>>() {}, 
            "riak.cluster.nodes", "Names of all active Riak nodes in the cluster <Entity,Riak Name>");

    @SetFromFlag("delayBeforeAdvertisingCluster")
    ConfigKey<Duration> DELAY_BEFORE_ADVERTISING_CLUSTER = ConfigKeys.newConfigKey(Duration.class, "riak.cluster.delayBeforeAdvertisingCluster", "Delay after cluster is started before checking and advertising its availability", Duration.seconds(2 * 60));

    AttributeSensor<Boolean> IS_CLUSTER_INIT = Sensors.newBooleanSensor("riak.cluster.isClusterInit", "Flag to determine if the cluster was already initialized");

    AttributeSensor<Boolean> IS_FIRST_NODE_SET = Sensors.newBooleanSensor("riak.cluster.isFirstNodeSet", "Flag to determine if the first node has been set");

    AttributeSensor<String> NODE_LIST = Sensors.newStringSensor("riak.cluster.nodeList", "List of nodes (including ports), comma separated");

    AttributeSensor<String> NODE_LIST_PB_PORT = Sensors.newStringSensor("riak.cluster.nodeListPbPort", "List of nodes (including ports for riak db clients), comma separated");

    AttributeSensor<URI> RIAK_CONSOLE_URI = Attributes.MAIN_URI;

    AttributeSensor<Integer> NODE_GETS_1MIN_PER_NODE = Sensors.newIntegerSensor("riak.node.gets.1m.perNode", "Gets in the last minute, averaged across cluster");
    AttributeSensor<Integer> NODE_PUTS_1MIN_PER_NODE = Sensors.newIntegerSensor("riak.node.puts.1m.perNode", "Puts in the last minute, averaged across cluster");
    AttributeSensor<Integer> NODE_OPS_1MIN_PER_NODE = Sensors.newIntegerSensor("riak.node.ops.1m.perNode", "Sum of node gets and puts in the last minute, averaged across cluster");

}
