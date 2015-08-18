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

import groovy.time.TimeDuration;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.entity.AbstractEc2LiveTest;
import org.apache.brooklyn.entity.core.Entities;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBServer;
import org.apache.brooklyn.test.Asserts;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * NOTE: These test will provision 9 machines in AWS, which can cause 'Request limit exceeded' and
 * 'Exhausted available authentication methods' exceptions, depending upon current AWS load. You can
 * mitigate this issue by adding the following lines to your brooklyn.properties:
 *
 * brooklyn.location.jclouds.machineCreateAttempts=3
 * brooklyn.jclouds.aws-ec2.maxConcurrentMachineCreations=5
 */
@Test
public class MongoDBShardedDeploymentEc2LiveTest extends AbstractEc2LiveTest {

    private static final Integer ROUTER_CLUSTER_SIZE = 2;
    private static final Integer REPLICASET_SIZE = 2;
    private static final Integer SHARD_CLUSTER_SIZE = 3;
    private static final TimeDuration TIMEOUT = new TimeDuration(0, 3, 0, 0);

    @Override
    protected void doTest(Location loc) throws Exception {
        final MongoDBShardedDeployment deployment = app.createAndManageChild(EntitySpec.create(MongoDBShardedDeployment.class)
                .configure(MongoDBShardedDeployment.INITIAL_ROUTER_CLUSTER_SIZE, ROUTER_CLUSTER_SIZE)
                .configure(MongoDBShardedDeployment.SHARD_REPLICASET_SIZE, REPLICASET_SIZE)
                .configure(MongoDBShardedDeployment.INITIAL_SHARD_CLUSTER_SIZE, SHARD_CLUSTER_SIZE)
                .configure(MongoDBShardedDeployment.MONGODB_REPLICA_SET_SPEC, EntitySpec.create(MongoDBReplicaSet.class)
                        .configure(MongoDBServer.MONGODB_CONF_TEMPLATE_URL, "classpath:///test-mongodb.conf")
                        .configure(MongoDBReplicaSet.MEMBER_SPEC, EntitySpec.create(MongoDBServer.class)))
                .configure(MongoDBShardedDeployment.MONGODB_ROUTER_SPEC, EntitySpec.create(MongoDBRouter.class)
                        .configure(MongoDBConfigServer.MONGODB_CONF_TEMPLATE_URL, "classpath:///test-mongodb-router.conf"))
                .configure(MongoDBShardedDeployment.MONGODB_CONFIG_SERVER_SPEC, EntitySpec.create(MongoDBConfigServer.class)
                        .configure(MongoDBConfigServer.MONGODB_CONF_TEMPLATE_URL, "classpath:///test-mongodb-configserver.conf")));

        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);

        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Runnable() {
            public void run() {
                Assert.assertEquals(deployment.getRouterCluster().getCurrentSize(), ROUTER_CLUSTER_SIZE);
                Assert.assertEquals(deployment.getShardCluster().getCurrentSize(), SHARD_CLUSTER_SIZE);
                Assert.assertEquals(deployment.getConfigCluster().getCurrentSize(), MongoDBShardedDeployment.CONFIG_CLUSTER_SIZE.getDefaultValue());
                for (Entity entity : deployment.getShardCluster().getMembers()) {
                    Assert.assertEquals(((MongoDBReplicaSet) entity).getCurrentSize(), REPLICASET_SIZE);
                }
            }
        });
    }
}
