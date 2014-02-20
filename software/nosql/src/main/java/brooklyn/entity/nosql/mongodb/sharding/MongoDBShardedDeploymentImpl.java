package brooklyn.entity.nosql.mongodb.sharding;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MongoDBShardedDeploymentImpl extends AbstractEntity implements MongoDBShardedDeployment {
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBShardedDeploymentImpl.class);
    
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
        final MongoDBRouterCluster routers = getAttribute(ROUTER_CLUSTER);
        final MongoDBShardCluster shards = getAttribute(SHARD_CLUSTER);
        List<DynamicCluster> clusters = ImmutableList.of(getAttribute(CONFIG_SERVER_CLUSTER), routers, shards);
        try {
            Entities.invokeEffectorList(this, clusters, Startable.START, ImmutableMap.of("locations", locations))
                .get();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        // FIXME: Check if service is up, call connectSensors
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
