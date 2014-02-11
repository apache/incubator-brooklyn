package brooklyn.entity.nosql.mongodb.sharding;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.exceptions.Exceptions;

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
        try {
            Entities.invokeEffectorList(this, ImmutableList.of(configServers, routers, shards), Startable.START, ImmutableMap.of("locations", locations))
                .get();
            // wait for everything to start, then add the sharded servers
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } catch (ExecutionException e) {
            throw Exceptions.propagate(e);
        }
        // FIXME: Check if service is up, call checkSensors
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
