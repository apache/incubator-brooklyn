package brooklyn.entity.nosql.mongodb.sharding;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class MongoDBShardedDeploymentImpl extends AbstractEntity implements MongoDBShardedDeployment {
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBShardedDeploymentImpl.class);
    
    // Used to ensure that the shards on only added the first time that SERVICE_UP is set on the shard cluster
    private AtomicBoolean shardsAdded = new AtomicBoolean(false);

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
        subscribe(shards, MongoDBReplicaSet.SERVICE_UP, new SensorEventListener<Boolean>() {
            public void onEvent(SensorEvent<Boolean> event) {
                if (event.getValue() && shardsAdded.compareAndSet(false, true)) {
                    // TODO: What to do if there are no routers (yet)
                    // Shards are added via a router, but only need to be added to one router. The other routers will read the shard configuration from the config servers
                    MongoDBRouter router = (MongoDBRouter) Iterables.getFirst(routers.getMembers(), null);
                    router.waitForServiceUp(getConfig(ROUTER_UP_TIMEOUT));
                    MongoDBClientSupport client;
                    try {
                        client = MongoDBClientSupport.forServer(router);
                    } catch (UnknownHostException e) {
                        throw Exceptions.propagate(e);
                    }
                    for (Entity entity : shards.getMembers()) {
                        MongoDBReplicaSet replicaSet = (MongoDBReplicaSet) entity;
                        MongoDBServer primary = null;
                        primary = replicaSet.getAttribute(MongoDBReplicaSet.PRIMARY_ENTITY);
                        String replicaSetURL = replicaSet.getName() + "/"
                                + Strings.removeFromStart(primary.getAttribute(MongoDBServer.MONGO_SERVER_ENDPOINT), "http://");
                        LOG.info("Using {} to add shard URL {}...", router, replicaSetURL);
                        client.addShardToRouter(replicaSetURL);
                    }
                    // FIXME: Check if service is up, call checkSensors
                    setAttribute(SERVICE_UP, true);
                }
            }
        });
        try {
            Entities.invokeEffectorList(this, clusters, Startable.START, ImmutableMap.of("locations", locations))
                .get();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
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
