/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
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

    ConfigKey<String> SEEDS = new BasicConfigKey<String>(String.class, "cassandra.seeds", "List of seed node hosts in cluster");

    MethodEffector<Void> UPDATE = new MethodEffector<Void>(CassandraCluster.class, "update");

    /**
     * The name of the cluster.
     */
    String getClusterName();

    @Description("Updates the cluster members")
    void update();

}
