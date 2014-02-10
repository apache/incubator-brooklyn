package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;

public class MongoDBShardedDeploymentImpl extends AbstractEntity implements MongoDBShardedDeployment {
    private MongoDBRouterCluster routers;
    private MongoDBShardCluster shards;
    private MongoDBConfigServerCluster configServers;
    
    @Override
    public void init() {
        EntitySpec<MongoDBRouterCluster> routersSpec = EntitySpec.create(MongoDBRouterCluster.class);
        EntitySpec<MongoDBShardCluster> shardsSpec = EntitySpec.create(MongoDBShardCluster.class);
        EntitySpec<MongoDBConfigServerCluster> configServersSpec = EntitySpec.create(MongoDBConfigServerCluster.class);
        
        routers = addChild(routersSpec);
        shards = addChild(shardsSpec);
        configServers = addChild(configServersSpec);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void restart() {
        // TODO Auto-generated method stub
        
    }
}
