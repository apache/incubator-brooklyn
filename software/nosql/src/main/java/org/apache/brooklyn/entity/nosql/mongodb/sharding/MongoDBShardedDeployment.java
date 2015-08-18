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

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.entity.proxying.ImplementedBy;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.util.flags.SetFromFlag;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBServer;
import org.apache.brooklyn.util.time.Duration;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.Sensors;

import com.google.common.reflect.TypeToken;

@Catalog(name="MongoDB Sharded Deployment",
        description="MongoDB (from \"humongous\") is a scalable, high-performance, open source NoSQL database",
        iconUrl="classpath:///mongodb-logo.png")
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
            "Number of servers (mongod) in each shard (replicaset)", 3);
    
    @SetFromFlag("routerUpTimeout")
    ConfigKey<Duration> ROUTER_UP_TIMEOUT = ConfigKeys.newConfigKey(Duration.class, "mongodb.router.up.timeout", 
            "Maximum time to wait for the routers to become available before adding the shards", Duration.FIVE_MINUTES);
    
    @SetFromFlag("coLocatedRouterGroup")
    ConfigKey<Group> CO_LOCATED_ROUTER_GROUP = ConfigKeys.newConfigKey(Group.class, "mongodb.colocated.router.group", 
            "Group to be monitored for the addition of new CoLocatedMongoDBRouter entities");
    
    @SuppressWarnings("serial")
    ConfigKey<EntitySpec<?>> MONGODB_ROUTER_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<?>>() {},
            "mongodb.router.spec", 
            "Spec for Router instances",
            EntitySpec.create(MongoDBRouter.class));

    @SuppressWarnings("serial")
    ConfigKey<EntitySpec<?>> MONGODB_REPLICA_SET_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<?>>() {},
            "mongodb.replicaset.spec", 
            "Spec for Replica Set",
            EntitySpec.create(MongoDBReplicaSet.class)
                    .configure(MongoDBReplicaSet.MEMBER_SPEC, EntitySpec.create(MongoDBServer.class)));

    @SuppressWarnings("serial")
    ConfigKey<EntitySpec<?>> MONGODB_CONFIG_SERVER_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<?>>() {},
            "mongodb.configserver.spec", 
            "Spec for Config Server instances",
            EntitySpec.create(MongoDBConfigServer.class));

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
