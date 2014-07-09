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
package brooklyn.entity.nosql.couchbase;

import java.util.List;
import java.util.Set;

import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

@ImplementedBy(CouchbaseClusterImpl.class)
public interface CouchbaseCluster extends DynamicCluster {

    AttributeSensor<Integer> ACTUAL_CLUSTER_SIZE = Sensors.newIntegerSensor("coucbase.cluster.actualClusterSize", "returns the actual number of nodes in the cluster");

    @SuppressWarnings("serial")
    AttributeSensor<Set<Entity>> COUCHBASE_CLUSTER_UP_NODES = Sensors.newSensor(new TypeToken<Set<Entity>>() {
    }, "couchbase.cluster.clusterEntities", "the set of service up nodes");

    @SuppressWarnings("serial")
    AttributeSensor<List<String>> COUCHBASE_CLUSTER_BUCKETS = Sensors.newSensor(new TypeToken<List<String>>() {
    }, "couchbase.cluster.buckets", "Names of all the buckets the couchbase cluster");

    AttributeSensor<Entity> COUCHBASE_PRIMARY_NODE = Sensors.newSensor(Entity.class, "couchbase.cluster.primaryNode", "The primary couchbase node to query and issue add-server and rebalance on");

    AttributeSensor<Boolean> IS_CLUSTER_INITIALIZED = Sensors.newBooleanSensor("couchbase.cluster.isClusterInitialized", "flag to emit if the couchbase cluster was intialized");

    @SetFromFlag("intialQuorumSize")
    ConfigKey<Integer> INITIAL_QUORUM_SIZE = ConfigKeys.newIntegerConfigKey("couchbase.cluster.intialQuorumSize", "Initial cluster quorum size - number of initial nodes that must have been successfully started to report success (if < 0, then use value of INITIAL_SIZE)",
            -1);

    @SetFromFlag("delayBeforeAdvertisingCluster")
    ConfigKey<Duration> DELAY_BEFORE_ADVERTISING_CLUSTER = ConfigKeys.newConfigKey(Duration.class, "couchbase.cluster.delayBeforeAdvertisingCluster", "Delay after cluster is started before checking and advertising its availability", Duration.THIRTY_SECONDS);

    @SetFromFlag("serviceUpTimeOut")
    ConfigKey<Duration> SERVICE_UP_TIME_OUT = ConfigKeys.newConfigKey(Duration.class, "couchbase.cluster.serviceUpTimeOut", "Service up time out duration for all the couchbase nodes", Duration.seconds(3 * 60));
    
    @SuppressWarnings("serial")
    AttributeSensor<List<String>> COUCHBASE_CLUSTER_UP_NODE_ADDRESSES = Sensors.newSensor(new TypeToken<List<String>>() {},
        "couchbase.cluster.node.addresses", "List of host:port of all active nodes in the cluster (http admin port, and public hostname/IP)");
    
    // Interesting stats
    AttributeSensor<Double> OPS_PER_NODE = Sensors.newDoubleSensor("couchbase.stats.cluster.per.node.ops", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/ops");
    AttributeSensor<Double> EP_BG_FETCHED_PER_NODE = Sensors.newDoubleSensor("couchbase.stats.cluster.per.node.ep.bg.fetched", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/ep_bg_fetched");
    AttributeSensor<Double> CURR_ITEMS_PER_NODE = Sensors.newDoubleSensor("couchbase.stats.cluster.per.node.curr.items", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/curr_items");
    AttributeSensor<Double> VB_REPLICA_CURR_ITEMS_PER_NODE = Sensors.newDoubleSensor("couchbase.stats.cluster.per.node.vb.replica.curr.items", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/vb_replica_curr_items");
    AttributeSensor<Double> GET_HITS_PER_NODE = Sensors.newDoubleSensor("couchbase.stats.cluster.per.node.get.hits", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/get_hits");
    AttributeSensor<Double> CMD_GET_PER_NODE = Sensors.newDoubleSensor("couchbase.stats.cluster.per.node.cmd.get", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/cmd_get");
    AttributeSensor<Double> CURR_ITEMS_TOT_PER_NODE = Sensors.newDoubleSensor("couchbase.stats.cluster.per.node.curr.items.tot", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/curr_items_tot");
    // Although these are Double (after aggregation), they need to be coerced to Long for ByteSizeStrings rendering
    AttributeSensor<Long> COUCH_DOCS_DATA_SIZE_PER_NODE = Sensors.newLongSensor("couchbase.stats.cluster.per.node.couch.docs.data.size", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/couch_docs_data_size");
    AttributeSensor<Long> MEM_USED_PER_NODE = Sensors.newLongSensor("couchbase.stats.cluster.per.node.mem.used", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/mem_used");
    AttributeSensor<Long> COUCH_VIEWS_ACTUAL_DISK_SIZE_PER_NODE = Sensors.newLongSensor("couchbase.stats.cluster.per.node.couch.views.actual.disk.size", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/couch_views_actual_disk_size");
    AttributeSensor<Long> COUCH_DOCS_ACTUAL_DISK_SIZE_PER_NODE = Sensors.newLongSensor("couchbase.stats.cluster.per.node.couch.docs.actual.disk.size", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/couch_docs_actual_disk_size");
    AttributeSensor<Long> COUCH_VIEWS_DATA_SIZE_PER_NODE = Sensors.newLongSensor("couchbase.stats.cluster.per.node.couch.views.data.size", 
            "Average across cluster for pools/nodes/<current node>/interestingStats/couch_views_data_size");
}
