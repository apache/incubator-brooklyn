/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.IntegerAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.StringAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * A cluster of {@link CassandraNode}s based on {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated Cassandra statistics from cluster
 */
@Catalog(name="Apache Cassandra Database Cluster", description="Cassandra is a highly scalable, eventually consistent, distributed, structured key-value store which provides a ColumnFamily-based data model richer than typical key/value systems", iconUrl="classpath:///cassandra-logo.jpeg")
@ImplementedBy(CassandraClusterImpl.class)
public interface CassandraCluster extends DynamicCluster {

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "cassandra.cluster.name", "Name of the Cassandra cluster", "BrooklynCluster");

    ConfigKey<String> SEEDS = new StringConfigKey("cassandra.cluster.seeds", "List of seed node hosts in cluster", null);

    AttributeSensor<String> HOSTNAME = new StringAttributeSensor("cassandra.cluster.hostname", "Hostname to connect to cluster with");

    AttributeSensor<Integer> THRIFT_PORT = new IntegerAttributeSensor("cassandra.cluster.thrift.port", "Cassandra Thrift RPC port to connect to cluster with");

    MethodEffector<Void> UPDATE = new MethodEffector<Void>(CassandraCluster.class, "update");

    /**
     * The name of the cluster.
     */
    String getClusterName();

    @Effector(description="Updates the cluster members")
    void update();

}
