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
package org.apache.brooklyn.entity.nosql.mongodb.sharding;

import static org.apache.brooklyn.core.sensor.DependentConfiguration.attributeWhenReady;

import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceNotUpLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.entity.nosql.mongodb.MongoDBAuthenticationMixins;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBAuthenticationUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MongoDBShardedDeploymentImpl extends AbstractEntity implements MongoDBShardedDeployment, MongoDBAuthenticationMixins {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBShardedDeploymentImpl.class);
    
    @Override
    public void init() {
        super.init();

        EntitySpec<MongoDBConfigServerCluster> configServerClusterSpec = EntitySpec.create(MongoDBConfigServerCluster.class)
                .configure(MongoDBConfigServerCluster.MEMBER_SPEC, getConfig(MONGODB_CONFIG_SERVER_SPEC))
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(CONFIG_CLUSTER_SIZE));
        MongoDBAuthenticationUtils.setAuthenticationConfig(configServerClusterSpec, this);
        sensors().set(CONFIG_SERVER_CLUSTER, addChild(configServerClusterSpec));

        EntitySpec<MongoDBRouterCluster> routerClusterSpec = EntitySpec.create(MongoDBRouterCluster.class)
                .configure(MongoDBRouterCluster.MEMBER_SPEC, getConfig(MONGODB_ROUTER_SPEC))
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_ROUTER_CLUSTER_SIZE))
                .configure(MongoDBRouter.CONFIG_SERVERS, attributeWhenReady(getAttribute(CONFIG_SERVER_CLUSTER), MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES));
        MongoDBAuthenticationUtils.setAuthenticationConfig(routerClusterSpec, this);
        sensors().set(ROUTER_CLUSTER, addChild(routerClusterSpec));

        EntitySpec<MongoDBShardCluster> shardClusterSpec = EntitySpec.create(MongoDBShardCluster.class)
                .configure(MongoDBShardCluster.MEMBER_SPEC, getConfig(MONGODB_REPLICA_SET_SPEC))
                .configure(DynamicCluster.INITIAL_SIZE, getConfig(INITIAL_SHARD_CLUSTER_SIZE));
        MongoDBAuthenticationUtils.setAuthenticationConfig(shardClusterSpec, this);
        sensors().set(SHARD_CLUSTER, addChild(shardClusterSpec));

        enrichers().add(Enrichers.builder()
                .propagating(MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES)
                .from(getAttribute(CONFIG_SERVER_CLUSTER))
                .build());

        // Advertise even if default are used (root password is set in MongoDBAuthenticationUtils)
        sensors().set(MongoDBAuthenticationMixins.AUTHENTICATION_DATABASE, config().get(MongoDBAuthenticationMixins.AUTHENTICATION_DATABASE));
        sensors().set(MongoDBAuthenticationMixins.ROOT_USERNAME, config().get(MongoDBAuthenticationMixins.ROOT_USERNAME));

        ServiceNotUpLogic.updateNotUpIndicator(this, Attributes.SERVICE_STATE_ACTUAL, "stopped");
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        try {
            final MongoDBRouterCluster routers = getAttribute(ROUTER_CLUSTER);
            final MongoDBShardCluster shards = getAttribute(SHARD_CLUSTER);
            List<DynamicCluster> clusters = ImmutableList.of(getAttribute(CONFIG_SERVER_CLUSTER), routers, shards);
            Entities.invokeEffectorList(this, clusters, Startable.START, ImmutableMap.of("locations", locations))
                    .get();

            if (getConfigRaw(MongoDBShardedDeployment.CO_LOCATED_ROUTER_GROUP, true).isPresent()) {
                policies().add(PolicySpec.create(ColocatedRouterTrackingPolicy.class)
                        .displayName("Co-located router tracker")
                        .configure("group", getConfig(MongoDBShardedDeployment.CO_LOCATED_ROUTER_GROUP)));
            }

            ServiceNotUpLogic.clearNotUpIndicator(this, Attributes.SERVICE_STATE_ACTUAL);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } catch (Exception e) {
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            // no need to log here; the effector invocation should do that
            throw Exceptions.propagate(e);
        }
    }

    public static class ColocatedRouterTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityAdded(Entity member) {
            MongoDBAuthenticationUtils.setAuthenticationConfig(member, entity);
            MongoDBRouterCluster cluster = entity.getAttribute(ROUTER_CLUSTER);
            cluster.addMember(member.getAttribute(CoLocatedMongoDBRouter.ROUTER));
        }
        @Override
        protected void onEntityRemoved(Entity member) {
            MongoDBRouterCluster cluster = entity.getAttribute(ROUTER_CLUSTER);
            cluster.removeMember(member.getAttribute(CoLocatedMongoDBRouter.ROUTER));
        }
    }

    @Override
    public void stop() {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        try {
            Entities.invokeEffectorList(this, ImmutableList.of(getAttribute(CONFIG_SERVER_CLUSTER), getAttribute(ROUTER_CLUSTER), 
                    getAttribute(SHARD_CLUSTER)), Startable.STOP).get();
        } catch (Exception e) {
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPED);
        ServiceNotUpLogic.updateNotUpIndicator(this, Attributes.SERVICE_STATE_ACTUAL, "stopped");
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
