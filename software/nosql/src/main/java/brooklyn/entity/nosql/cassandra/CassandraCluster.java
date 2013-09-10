/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Set;

import brooklyn.catalog.Catalog;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

/**
 * A cluster of {@link CassandraNode}s based on {@link DynamicCluster} which can be resized by a policy if required.
 * <p>
 * Note that due to how Cassandra assumes ports are the same across a cluster, 
 * it is NOT possible to deploy a cluster to localhost.
 * <p>
 *
 * TODO add sensors with aggregated Cassandra statistics from cluster
 */
@Catalog(name="Apache Cassandra Database Cluster", description="Cassandra is a highly scalable, eventually consistent, distributed, structured key-value store which provides a ColumnFamily-based data model richer than typical key/value systems", iconUrl="classpath:///cassandra-logo.jpeg")
@ImplementedBy(CassandraClusterImpl.class)
public interface CassandraCluster extends DynamicCluster {

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "cassandra.cluster.name", "Name of the Cassandra cluster", "BrooklynCluster");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    AttributeSensor<Set<Entity>> CURRENT_SEEDS = (AttributeSensor)Sensors.newSensor(Set.class, "cassandra.cluster.seeds.current", "Current set of seeds to use to bootstrap the cluster");
    
    AttributeSensor<String> HOSTNAME = Sensors.newStringSensor("cassandra.cluster.hostname", "Hostname to connect to cluster with");

    AttributeSensor<Integer> THRIFT_PORT = Sensors.newIntegerSensor("cassandra.cluster.thrift.port", "Cassandra Thrift RPC port to connect to cluster with");
    
    AttributeSensor<Long> FIRST_NODE_STARTED_TIME_UTC = Sensors.newLongSensor("cassandra.cluster.first.node.started.utc", "Time (UTC) when the first node was started");
    
    AttributeSensor<Integer> SCHEMA_VERSION_COUNT = Sensors.newIntegerSensor("cassandra.cluster.schema.versions.count", 
            "Number of different schema versions in the cluster; should be 1 for a healthy cluster, 0 when off; " +
            ">=2 indicats a Schema Disagreement Error (and keyspace access may fail)");

    MethodEffector<Void> UPDATE = new MethodEffector<Void>(CassandraCluster.class, "update");

    /** sets the number of nodes used to seed the cluster;
     *  v1.2.2 is buggy and requires a big delay for 2 nodes both seeds to reconcile, 
     *  see http://stackoverflow.com/questions/6770894/schemadisagreementexception/18639005
     *  and posts to cassandra mailing list. (Alex, 9 Sept 2013)
     *  <p>
     *  with v1.2.9 this seems fine, with just a few seconds' delay after starting */
    public static final int DEFAULT_SEED_QUORUM = 2;
    
    /** can insert a delay after the first node comes up;
     * is not needed with 1.2.9 (and does not help with the bug in 1.2.2) */
    public static final Duration DELAY_AFTER_FIRST = Duration.ZERO;
    
    /** whether to wait for the first node to start up */
    // not sure whether this is needed or not; need to test in env where not all nodes are seed nodes,
    // what happens if non-seed nodes start before the seed nodes ?
    public static final boolean WAIT_FOR_FIRST = true;
    
    /** Additional time after the nodes in the cluster are up when starting before announcing the cluster as up;
     * Useful to ensure nodes have synchronized.  */
    // on 1.2.2 this could be as much as 120s when using 2 seed nodes, 
    // or just a few seconds with 1 seed node;
    // on 1.2.9 it seems a few seconds is sufficient even with 2 seed nodes
    public static final Duration DELAY_BEFORE_ADVERTISING_CLUSTER = Duration.TEN_SECONDS;
    
    /**
     * The name of the cluster.
     */
    String getClusterName();

    @Effector(description="Updates the cluster members")
    void update();

}
