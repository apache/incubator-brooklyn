package brooklyn.entity.nosql.mongodb.sharding;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.nosql.mongodb.MongoDBClientSupport;
import brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import brooklyn.entity.nosql.mongodb.MongoDBServer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class MongoDBShardedDeploymentImpl extends AbstractEntity implements MongoDBShardedDeployment {

    @Override
    public void init() {
        setAttribute(CONFIG_SERVER_CLUSTER, addChild(EntitySpec.create(MongoDBConfigServerCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(CONFIG_CLUSTER_SIZE))));
        setAttribute(ROUTER_CLUSTER, addChild(EntitySpec.create(MongoDBRouterCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_ROUTER_CLUSTER_SIZE))
                .configure(MongoDBRouter.CONFIG_SERVERS, attributeWhenReady(getAttribute(CONFIG_SERVER_CLUSTER), MongoDBConfigServerCluster.SERVER_ADDRESSES))));
        setAttribute(SHARD_CLUSTER, addChild(EntitySpec.create(MongoDBShardCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_SHARD_CLUSTER_SIZE))));
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        MongoDBRouterCluster routers = getAttribute(ROUTER_CLUSTER);
        MongoDBShardCluster shards = getAttribute(SHARD_CLUSTER);
        List<DynamicCluster> clusters = ImmutableList.of(getAttribute(CONFIG_SERVER_CLUSTER), routers, shards);
        try {
            Entities.invokeEffectorList(this, clusters, Startable.START, ImmutableMap.of("locations", locations))
                .get();
            // wait for everything to start, then add the sharded servers
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        // TODO: What to do if there are no routers (yet)
        // Shards are added via a router, but only need to be added to one router. The other routers will read the shard configuration from the config servers
        MongoDBRouter router = (MongoDBRouter) Iterables.getFirst(routers.getMembers(), null);
        MongoDBClientSupport client;
        try {
            client = MongoDBClientSupport.forServer(router);
        } catch (UnknownHostException e) {
            throw Exceptions.propagate(e);
        }
        for (Entity entity : shards.getMembers()) {
            MongoDBReplicaSet replicaSet = (MongoDBReplicaSet)entity;
            
            String replicaSetURL = replicaSet.getName() + "/" + Strings.removeFromStart(replicaSet.getPrimary().getAttribute(MongoDBServer.MONGO_SERVER_ENDPOINT), "http://");
            // TODO: Throw exception if shard cannot be added, or set shard on fire?
            client.addShardToRouter(replicaSetURL);
        }
        // FIXME: Check if service is up, call checkSensors
        setAttribute(SERVICE_UP, true);
    }

    @Override
    public void stop() {
        setAttribute(Attributes.SERVICE_STATE, Lifecycle.STOPPING);
        try {
            Entities.invokeEffectorList(this, ImmutableList.of(getAttribute(CONFIG_SERVER_CLUSTER), getAttribute(ROUTER_CLUSTER), 
                    getAttribute(SHARD_CLUSTER)), Startable.STOP).get();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        setAttribute(Attributes.SERVICE_STATE, Lifecycle.STOPPED);
        setAttribute(SERVICE_UP, false);
    }
    
    @Override
    public void restart() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public MongoDBConfigServerCluster getConfigCluster() {
        return getAttribute(CONFIG_SERVER_CLUSTER);
    }

    @Override
    public MongoDBRouterCluster getRouterCluster() {
        return getAttribute(ROUTER_CLUSTER);
    }

    @Override
    public MongoDBShardCluster getShardCluster() {
        return getAttribute(SHARD_CLUSTER);
    }

}
