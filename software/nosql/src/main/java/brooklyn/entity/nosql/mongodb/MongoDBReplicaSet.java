package brooklyn.entity.nosql.mongodb;

import brooklyn.config.ConfigKey;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

import java.util.Collection;

/**
 * A replica set of {@link MongoDBServer}s, based on {@link DynamicCluster} which can be resized by a policy
 * if required.
 *
 * <p/><b>Note</b>
 * An issue with <code>mongod</code> on Mac OS X can cause unpredictable failure of servers at start-up.
 * See <a href="https://groups.google.com/forum/#!topic/mongodb-user/QRQYdIXOR2U">this mailing list post</a>
 * for more information.
 *
 * <p/>This replica set implementation has been tested on OS X 10.6 and Ubuntu 12.04.
 *
 * @see <a href="http://docs.mongodb.org/manual/replication/">http://docs.mongodb.org/manual/replication/</a>
 */
@ImplementedBy(MongoDBReplicaSetImpl.class)
public interface MongoDBReplicaSet extends DynamicCluster {

    @SetFromFlag("replicaSetName")
    ConfigKey<String> REPLICA_SET_NAME = new BasicConfigKey<String>(
            String.class, "mongodb.replicaSet.name", "Name of the MongoDB replica set", "BrooklynCluster");

    AttributeSensor<MongoDBServer> PRIMARY = new BasicAttributeSensor<MongoDBServer>(
            MongoDBServer.class, "mongodb.replicaSet.primary", "The primary member of the replica set");

    AttributeSensor<Collection<MongoDBServer>> SECONDARIES = new BasicAttributeSensor(
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
