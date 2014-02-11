package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(MongoDBShardedDeploymentImpl.class)
public interface MongoDBShardedDeployment extends Entity, Startable {
    @SetFromFlag("configClusterSize")
    BasicConfigKey<Integer> CONFIG_CLUSTER_SIZE = new BasicConfigKey<Integer>(
            Integer.class, "mongodb.config.cluster.size", "Number of config servers", 3);
    
    @SetFromFlag("initialRouterClusterSize")
    BasicConfigKey<Integer> INITIAL_ROUTER_CLUSTER_SIZE = new BasicConfigKey<Integer>(
            Integer.class, "mongodb.router.cluster.initial.size", "Initial number of routers (mongos)", 0);
    
    @SetFromFlag("initialShardClusterSize")
    BasicConfigKey<Integer> INITIAL_SHARD_CLUSTER_SIZE = new BasicConfigKey<Integer>(
            Integer.class, "mongodb.shard.cluster.initial.size", "Initial number of shards (mongod)", 2);
}
