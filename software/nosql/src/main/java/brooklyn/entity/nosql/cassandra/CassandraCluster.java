/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * A cluster of {@link CassandraNode}s based on {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated Cassandra statistics from cluster
 */
@ImplementedBy(CassandraClusterImpl.class)
public interface CassandraCluster extends DynamicCluster {

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "cassandra.cluster.name", "Name of the Cassandra cluster", "BrooklynCluster");

    ConfigKey<String> SEEDS = new BasicConfigKey<String>(String.class, "cassandra.cluster.seeds", "List of seed node hosts in cluster");

    AttributeSensor<String> HOSTNAME = new BasicAttributeSensor<String>(String.class, "cassandra.cluster.hostname", "Hostname to connect to cluster with");

    AttributeSensor<Integer> THRIFT_PORT = new BasicAttributeSensor<Integer>(Integer.class, "cassandra.cluster.thrift.port", "Cassandra Thrift RPC port to connect to cluster with");

    MethodEffector<Void> UPDATE = new MethodEffector<Void>(CassandraCluster.class, "update");

    /**
     * The name of the cluster.
     */
    String getClusterName();

    @Description("Updates the cluster members")
    void update();

}
