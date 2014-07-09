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
package brooklyn.entity.nosql.cassandra;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.nosql.cassandra.TokenGenerators.PosNeg63TokenGenerator;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;

/**
 * A group of {@link CassandraNode}s -- based on Brooklyn's {@link DynamicCluster} 
 * (though it is a "Datacenter" in Cassandra terms, where Cassandra's "cluster" corresponds
 * to a Brooklyn Fabric, cf {@link CassandraFabric}). 
 * The Datacenter can be resized, manually or by policy if required.
 * Tokens are selected intelligently.
 * <p>
 * Note that due to how Cassandra assumes ports are the same across a cluster,
 * it is <em>NOT</em> possible to deploy a cluster of size larger than 1 to localhost.
 * (Some exploratory work has been done to use different 127.0.0.x IP's for localhost,
 * and there is evidence this could be made to work.)
 */
@Catalog(name="Apache Cassandra Datacenter Cluster", description="Cassandra is a highly scalable, eventually " +
        "consistent, distributed, structured key-value store which provides a ColumnFamily-based data model " +
        "richer than typical key/value systems", iconUrl="classpath:///cassandra-logo.jpeg")
@ImplementedBy(CassandraDatacenterImpl.class)
public interface CassandraDatacenter extends DynamicCluster, DatastoreMixins.HasDatastoreUrl, DatastoreMixins.CanExecuteScript {

    // FIXME datacenter name -- also CASS_CLUSTER_NODES should be CASS_DC_NODES
    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "cassandra.cluster.name", "Name of the Cassandra cluster", "BrooklynCluster");

    @SetFromFlag("snitchName")
    ConfigKey<String> ENDPOINT_SNITCH_NAME = ConfigKeys.newStringConfigKey("cassandra.cluster.snitchName", "Type of the Cassandra snitch", "SimpleSnitch");

    @SetFromFlag("seedSupplier")
    @SuppressWarnings("serial")
    ConfigKey<Supplier<Set<Entity>>> SEED_SUPPLIER = ConfigKeys.newConfigKey(new TypeToken<Supplier<Set<Entity>>>() { }, "cassandra.cluster.seedSupplier", "For determining the seed nodes", null);

    @SuppressWarnings("serial")
    @SetFromFlag("tokenGeneratorClass")
    ConfigKey<Class<? extends TokenGenerator>> TOKEN_GENERATOR_CLASS = ConfigKeys.newConfigKey(
        new TypeToken<Class<? extends TokenGenerator>>() {}, "cassandra.cluster.tokenGenerator.class", "For determining the tokens of nodes", 
        PosNeg63TokenGenerator.class);

    @SetFromFlag("tokenShift")
    ConfigKey<BigInteger> TOKEN_SHIFT = ConfigKeys.newConfigKey(BigInteger.class, "cassandra.cluster.tokenShift", 
        "Delta applied to all tokens generated for this Cassandra datacenter, "
        + "useful when configuring multiple datacenters which should be shifted; "
        + "if not set, a random shift is applied. (Pass 0 to prevent any shift.)", null);


    /**
     * Additional time after the nodes in the cluster are up when starting
     * before announcing the cluster as up.
     * <p>
     * Useful to ensure nodes have synchronized.
     * <p>
     * On 1.2.2 this could be as much as 120s when using 2 seed nodes,
     * or just a few seconds with 1 seed node. On 1.2.9 it seems a few
     * seconds is sufficient even with 2 seed nodes
     */
    @SetFromFlag("delayBeforeAdvertisingCluster")
    ConfigKey<Duration> DELAY_BEFORE_ADVERTISING_CLUSTER = ConfigKeys.newConfigKey(Duration.class, "cassandra.cluster.delayBeforeAdvertisingCluster", "Delay after cluster is started before checking and advertising its availability", Duration.TEN_SECONDS);

    @SuppressWarnings("serial")
    AttributeSensor<Multimap<String,Entity>> DATACENTER_USAGE = Sensors.newSensor(new TypeToken<Multimap<String,Entity>>() { }, "cassandra.cluster.datacenterUsages", "Current set of datacenters in use, with nodes in each");

    @SuppressWarnings("serial")
    AttributeSensor<Set<String>> DATACENTERS = Sensors.newSensor(new TypeToken<Set<String>>() { }, "cassandra.cluster.datacenters", "Current set of datacenters in use");

    AttributeSensor<Boolean> HAS_PUBLISHED_SEEDS = Sensors.newBooleanSensor("cassandra.cluster.seeds.hasPublished", "Whether we have published any seeds");

    @SuppressWarnings("serial")
    AttributeSensor<Set<Entity>> CURRENT_SEEDS = Sensors.newSensor(new TypeToken<Set<Entity>>() { }, "cassandra.cluster.seeds.current", "Current set of seeds to use to bootstrap the cluster");

    AttributeSensor<String> HOSTNAME = Sensors.newStringSensor("cassandra.cluster.hostname", "Hostname to connect to cluster with");

    @SuppressWarnings("serial")
    AttributeSensor<List<String>> CASSANDRA_CLUSTER_NODES = Sensors.newSensor(new TypeToken<List<String>>() {},
        "cassandra.cluster.nodes", "List of host:port of all active nodes in the cluster (thrift port, and public hostname/IP)");

    AttributeSensor<Integer> THRIFT_PORT = Sensors.newIntegerSensor("cassandra.cluster.thrift.port", "Cassandra Thrift RPC port to connect to cluster with");

    AttributeSensor<Long> FIRST_NODE_STARTED_TIME_UTC = Sensors.newLongSensor("cassandra.cluster.first.node.started.utc", "Time (UTC) when the first node was started");
    @SuppressWarnings("serial")
    AttributeSensor<List<Entity>> QUEUED_START_NODES = Sensors.newSensor(new TypeToken<List<Entity>>() {}, "cassandra.cluster.start.nodes.queued",
        "Nodes queued for starting (for sequential start)");
    
    AttributeSensor<Integer> SCHEMA_VERSION_COUNT = Sensors.newIntegerSensor("cassandra.cluster.schema.versions.count",
            "Number of different schema versions in the cluster; should be 1 for a healthy cluster, 0 when off; " +
            "2 and above indicats a Schema Disagreement Error (and keyspace access may fail)");

    AttributeSensor<Long> READ_PENDING = Sensors.newLongSensor("cassandra.cluster.read.pending", "Current pending ReadStage tasks");
    AttributeSensor<Integer> READ_ACTIVE = Sensors.newIntegerSensor("cassandra.cluster.read.active", "Current active ReadStage tasks");
    AttributeSensor<Long> WRITE_PENDING = Sensors.newLongSensor("cassandra.cluster.write.pending", "Current pending MutationStage tasks");
    AttributeSensor<Integer> WRITE_ACTIVE = Sensors.newIntegerSensor("cassandra.cluster.write.active", "Current active MutationStage tasks");

    AttributeSensor<Long> THRIFT_PORT_LATENCY_PER_NODE = Sensors.newLongSensor("cassandra.cluster.thrift.latency.perNode", "Latency for thrift port connection  averaged over all nodes (ms)");
    AttributeSensor<Double> READS_PER_SECOND_LAST_PER_NODE = Sensors.newDoubleSensor("cassandra.reads.perSec.last.perNode", "Reads/sec (last datapoint) averaged over all nodes");
    AttributeSensor<Double> WRITES_PER_SECOND_LAST_PER_NODE = Sensors.newDoubleSensor("cassandra.write.perSec.last.perNode", "Writes/sec (last datapoint) averaged over all nodes");
    AttributeSensor<Double> PROCESS_CPU_TIME_FRACTION_LAST_PER_NODE = Sensors.newDoubleSensor("cassandra.cluster.metrics.processCpuTime.fraction.perNode", "Fraction of CPU time used (percentage reported by JMX), averaged over all nodes");

    AttributeSensor<Double> READS_PER_SECOND_IN_WINDOW_PER_NODE = Sensors.newDoubleSensor("cassandra.reads.perSec.windowed.perNode", "Reads/sec (over time window) averaged over all nodes");
    AttributeSensor<Double> WRITES_PER_SECOND_IN_WINDOW_PER_NODE = Sensors.newDoubleSensor("cassandra.writes.perSec.windowed.perNode", "Writes/sec (over time window) averaged over all nodes");
    AttributeSensor<Double> THRIFT_PORT_LATENCY_IN_WINDOW_PER_NODE = Sensors.newDoubleSensor("cassandra.thrift.latency.windowed.perNode", "Latency for thrift port (ms, over time window) averaged over all nodes");
    AttributeSensor<Double> PROCESS_CPU_TIME_FRACTION_IN_WINDOW_PER_NODE = Sensors.newDoubleSensor("cassandra.cluster.metrics.processCpuTime.fraction.windowed", "Fraction of CPU time used (percentage, over time window), averaged over all nodes");

    MethodEffector<Void> UPDATE = new MethodEffector<Void>(CassandraDatacenter.class, "update");

    brooklyn.entity.Effector<String> EXECUTE_SCRIPT = Effectors.effector(DatastoreMixins.EXECUTE_SCRIPT)
        .description("executes the given script contents using cassandra-cli")
        .buildAbstract();

    /**
     * Sets the number of nodes used to seed the cluster.
     * <p>
     * Version 1.2.2 is buggy and requires a big delay for 2 nodes both seeds to reconcile,
     * with 1.2.9 this seems fine, with just a few seconds' delay after starting.
     *
     * @see <a href="http://stackoverflow.com/questions/6770894/schemadisagreementexception/18639005" />
     */
    int DEFAULT_SEED_QUORUM = 2;

    /**
     * Can insert a delay after the first node comes up.
     * <p>
     * Reportedly not needed with 1.2.9, but we are still seeing some seed failures so re-introducing it.
     * (This does not seem to help with the bug in 1.2.2.)
     */
    Duration DELAY_AFTER_FIRST = Duration.ONE_MINUTE;

    /**
     * If set (ie non-null), this waits the indicated time after a successful launch of one node
     * before starting the next.  (If it is null, all nodes start simultaneously,
     * possibly after the DELAY_AFTER_FIRST.)
     * <p>
     * When subsequent nodes start simultaneously, we occasionally see schema disagreement problems;
     * if nodes start sequentially, we occasionally get "no sources for (tokenRange]" problems.
     * Either way the node stops. Ideally this can be solved at the Cassandra level,
     * but if not, we will have to introduce some restarts at the Cassandra nodes (which does seem
     * to resolve the problems.)
     */
    Duration DELAY_BETWEEN_STARTS = null;
    
    /**
     * Whether to wait for the first node to start up
     * <p>
     * not sure whether this is needed or not. Need to test in env where not all nodes are seed nodes,
     * what happens if non-seed nodes start before the seed nodes?
     */
    boolean WAIT_FOR_FIRST = true;

    @Effector(description="Updates the cluster members")
    void update();

    /**
     * The name of the cluster.
     */
    String getClusterName();

    Set<Entity> gatherPotentialSeeds();

    Set<Entity> gatherPotentialRunningSeeds();

    String executeScript(String commands);

}
