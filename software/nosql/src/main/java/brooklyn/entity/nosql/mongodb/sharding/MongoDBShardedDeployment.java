package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

@ImplementedBy(MongoDBShardedDeploymentImpl.class)
public interface MongoDBShardedDeployment extends Entity, Startable {
    @SetFromFlag("configClusterSize")
    ConfigKey<Integer> CONFIG_CLUSTER_SIZE = ConfigKeys.newIntegerConfigKey("mongodb.config.cluster.size", 
            "Number of config servers", 3);
    
    @SetFromFlag("initialRouterClusterSize")
    ConfigKey<Integer> INITIAL_ROUTER_CLUSTER_SIZE = ConfigKeys.newIntegerConfigKey("mongodb.router.cluster.initial.size", 
            "Initial number of routers (mongos)", 0);
    
    @SetFromFlag("initialShardClusterSize")
    ConfigKey<Integer> INITIAL_SHARD_CLUSTER_SIZE = ConfigKeys.newIntegerConfigKey("mongodb.shard.cluster.initial.size", 
            "Initial number of shards (replicasets)", 2);
    
    @SetFromFlag("shardReplicaSetSize")
    ConfigKey<Integer> SHARD_REPLICASET_SIZE = ConfigKeys.newIntegerConfigKey("mongodb.shard.replicaset.size", 
            "Number of servers (mongod) in each shard (replicaset)", 2);
    
    @SetFromFlag("routerUpTimeout")
    ConfigKey<Duration> ROUTER_UP_TIMEOUT = ConfigKeys.newConfigKey(Duration.class, "mongodb.router.up.timeout", 
            "Maximum time to wait for the routers to become available before adding the shards", Duration.FIVE_MINUTES);
    
    public static AttributeSensor<MongoDBConfigServerCluster> CONFIG_SERVER_CLUSTER = Sensors.newSensor(
            MongoDBConfigServerCluster.class, "mongodbshardeddeployment.configservers", "Config servers");
    public static AttributeSensor<MongoDBRouterCluster> ROUTER_CLUSTER = Sensors.newSensor(
            MongoDBRouterCluster.class, "mongodbshardeddeployment.routers", "Routers");
    
    public static AttributeSensor<MongoDBShardCluster> SHARD_CLUSTER = Sensors.newSensor(
            MongoDBShardCluster.class, "mongodbshardeddeployment.shards", "Shards");
    
    public MongoDBConfigServerCluster getConfigCluster();
    
    public MongoDBRouterCluster getRouterCluster();
    
    public MongoDBShardCluster getShardCluster();
}
