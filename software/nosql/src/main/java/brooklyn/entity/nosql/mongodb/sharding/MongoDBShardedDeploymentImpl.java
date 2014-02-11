package brooklyn.entity.nosql.mongodb.sharding;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;

import java.util.Collection;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;

public class MongoDBShardedDeploymentImpl extends AbstractEntity implements MongoDBShardedDeployment {
    private MongoDBRouterCluster routers;
    private MongoDBShardCluster shards;
    private MongoDBConfigServerCluster configServers;

    @Override
    public void init() {
        configServers = addChild(EntitySpec.create(MongoDBConfigServerCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(CONFIG_CLUSTER_SIZE)));
        routers = addChild(EntitySpec.create(MongoDBRouterCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_ROUTER_CLUSTER_SIZE))
                .configure(MongoDBRouter.CONFIG_SERVERS, attributeWhenReady(configServers, MongoDBConfigServerCluster.SERVER_ADDRESSES)));
        shards = addChild(EntitySpec.create(MongoDBShardCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_SHARD_CLUSTER_SIZE)));
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        configServers.start(locations);
        routers.start(locations);
        shards.start(locations);
        // FIXME: Check if service is up
        setAttribute(SERVICE_UP, true);
    }

    @Override
    public void stop() {
        routers.stop();
        shards.stop();
        configServers.stop();
        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void restart() {
        // TODO Auto-generated method stub
        
    }
}
