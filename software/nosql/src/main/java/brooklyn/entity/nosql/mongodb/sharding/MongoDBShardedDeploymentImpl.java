package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.basic.AbstractEntity;

public class MongoDBShardedDeploymentImpl extends AbstractEntity {
    private MongoDBRouterCluster routers;
    private MongoDBShardCluster shards;
    private MongoDBConfigServerCluster configServers;
}
