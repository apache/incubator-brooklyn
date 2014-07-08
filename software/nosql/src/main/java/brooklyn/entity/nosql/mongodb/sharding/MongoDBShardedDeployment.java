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

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

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
