package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
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
            Integer.class, "mongodb.shard.cluster.initial.size", "Initial number of shards (replicasets)", 2);
    
    @SetFromFlag("shardReplicaSetSize")
    BasicConfigKey<Integer> SHARD_REPLICASET_SIZE = new BasicConfigKey<Integer>(
            Integer.class, "mongodb.shard.replicaset.size", "Number of servers (mongod) in each shard (replicaset)", 2);
    
    public static AttributeSensor<MongoDBConfigServerCluster> CONFIG_SERVER_CLUSTER = new BasicAttributeSensor<MongoDBConfigServerCluster>(
            MongoDBConfigServerCluster.class, "mongodbshardeddeployment.configservers", "Config servers");
    
    public static AttributeSensor<MongoDBRouterCluster> ROUTER_CLUSTER = new BasicAttributeSensor<MongoDBRouterCluster>(
            MongoDBRouterCluster.class, "mongodbshardeddeployment.routers", "Routers");
    
    public static AttributeSensor<MongoDBShardCluster> SHARD_CLUSTER = new BasicAttributeSensor<MongoDBShardCluster>(
            MongoDBShardCluster.class, "mongodbshardeddeployment.shards", "Shards");
    
    public MongoDBConfigServerCluster getConfigCluster();
    
    public MongoDBRouterCluster getRouterCluster();
    
    public MongoDBShardCluster getShardCluster();
}
