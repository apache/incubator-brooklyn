/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.mongodb.sharding;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.policy.PolicySpec;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MongoDBShardedDeploymentImpl extends AbstractEntity implements MongoDBShardedDeployment {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBShardedDeploymentImpl.class);
    
    @Override
    public void init() {
        setAttribute(CONFIG_SERVER_CLUSTER, addChild(EntitySpec.create(MongoDBConfigServerCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(CONFIG_CLUSTER_SIZE))));
        setAttribute(ROUTER_CLUSTER, addChild(EntitySpec.create(MongoDBRouterCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_ROUTER_CLUSTER_SIZE))
                .configure(MongoDBRouter.CONFIG_SERVERS, attributeWhenReady(getAttribute(CONFIG_SERVER_CLUSTER), MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES))));
        setAttribute(SHARD_CLUSTER, addChild(EntitySpec.create(MongoDBShardCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_SHARD_CLUSTER_SIZE))));
        addEnricher(Enrichers.builder()
                .propagating(MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES)
                .from(getAttribute(CONFIG_SERVER_CLUSTER))
                .build());
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        setAttribute(Attributes.SERVICE_STATE, Lifecycle.STARTING);
        try {
            final MongoDBRouterCluster routers = getAttribute(ROUTER_CLUSTER);
            final MongoDBShardCluster shards = getAttribute(SHARD_CLUSTER);
            List<DynamicCluster> clusters = ImmutableList.of(getAttribute(CONFIG_SERVER_CLUSTER), routers, shards);
            Entities.invokeEffectorList(this, clusters, Startable.START, ImmutableMap.of("locations", locations))
                .get();

            if (getConfigRaw(MongoDBShardedDeployment.CO_LOCATED_ROUTER_GROUP, true).isPresent()) {
                addPolicy(PolicySpec.create(ColocatedRouterTrackingPolicy.class)
                        .displayName("Co-located router tracker")
                        .configure("group", (Group)getConfig(MongoDBShardedDeployment.CO_LOCATED_ROUTER_GROUP)));
            }
            setAttribute(SERVICE_UP, true);
            setAttribute(Attributes.SERVICE_STATE, Lifecycle.RUNNING);
        } catch (Exception e) {
            setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
            // no need to log here; the effector invocation should do that
            throw Exceptions.propagate(e);
        }
    }

    public static class ColocatedRouterTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityAdded(Entity member) {
            MongoDBRouterCluster cluster = entity.getAttribute(ROUTER_CLUSTER);
            cluster.addMember(member.getAttribute(CoLocatedMongoDBRouter.ROUTER));
        }
        @Override
        protected void onEntityRemoved(Entity member) {
            MongoDBRouterCluster cluster = entity.getAttribute(ROUTER_CLUSTER);
            cluster.removeMember(member.getAttribute(CoLocatedMongoDBRouter.ROUTER));
        }
    };

    @Override
    public void stop() {
        setAttribute(Attributes.SERVICE_STATE, Lifecycle.STOPPING);
        try {
            Entities.invokeEffectorList(this, ImmutableList.of(getAttribute(CONFIG_SERVER_CLUSTER), getAttribute(ROUTER_CLUSTER), 
                    getAttribute(SHARD_CLUSTER)), Startable.STOP).get();
        } catch (Exception e) {
            setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }
        setAttribute(Attributes.SERVICE_STATE, Lifecycle.STOPPED);
        setAttribute(SERVICE_UP, false);
    }
    
    @Override
    public void restart() {
        throw new UnsupportedOperationException();
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
