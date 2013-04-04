/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.basic.Description;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * A replica set of {@link MongoDbServer}s based on {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated MongoDb statistics from replica set
 */
@ImplementedBy(MongoDbReplicaSetImpl.class)
public interface MongoDbReplicaSet extends DynamicCluster {

    @SetFromFlag("replicaSetName")
    BasicAttributeSensorAndConfigKey<String> REPLICA_SET_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "mongodb.replicaSet.name", "Name of the MongoDb replica set", "BrooklynCluster");

    /**
     * The name of the replica set.
     */
    String getReplicaSetName();

    @Description("Updates the replica set members")
    void update();

}
