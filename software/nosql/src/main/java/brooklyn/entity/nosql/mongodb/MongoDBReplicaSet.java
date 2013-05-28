package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

import java.util.Collection;

/**
 * A replica set of {@link MongoDBServer}s based on {@link DynamicCluster} which can be resized by a policy if required.
 */
@ImplementedBy(MongoDBReplicaSetImpl.class)
public interface MongoDBReplicaSet extends DynamicCluster {

    @SetFromFlag("replicaSetName")
    BasicAttributeSensorAndConfigKey<String> REPLICA_SET_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class,
            "mongodb.replicaSet.name", "Name of the MongoDB replica set", "BrooklynCluster");

    BasicAttributeSensor<MongoDBServer> PRIMARY = new BasicAttributeSensor<MongoDBServer>(MongoDBServer.class,
            "mongodb.replicaSet.primary", "The primary member of the replica set");

    BasicAttributeSensor<Collection<MongoDBServer>> SECONDARIES = new BasicAttributeSensor(
            Collection.class, "mongodb.replicaSet.secondaries", "The secondary members of the replica set");

    /**
     * The name of the replica set.
     */
    String getReplicaSetName();

    /**
     * @return The primary MongoDB server in the replica set.
     */
    MongoDBServer getPrimary();

    /**
     * @return The secondary servers in the replica set.
     */
    Collection<MongoDBServer> getSecondaries();

}
