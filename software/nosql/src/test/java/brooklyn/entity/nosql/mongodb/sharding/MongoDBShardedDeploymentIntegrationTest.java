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

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import brooklyn.entity.nosql.mongodb.MongoDBTestHelper;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mongodb.DBObject;

public class MongoDBShardedDeploymentIntegrationTest {
    
    private static final Integer ROUTER_CLUSTER_SIZE = 2;
    private static final Integer REPLICASET_SIZE = 2;
    private static final Integer SHARD_CLUSTER_SIZE = 3;
    
    private TestApplication app;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    private MongoDBShardedDeployment makeAndStartDeployment() {
        final MongoDBShardedDeployment deployment = app.createAndManageChild(EntitySpec.create(MongoDBShardedDeployment.class)
                .configure(MongoDBShardedDeployment.INITIAL_ROUTER_CLUSTER_SIZE, ROUTER_CLUSTER_SIZE)
                .configure(MongoDBShardedDeployment.SHARD_REPLICASET_SIZE, REPLICASET_SIZE)
                .configure(MongoDBShardedDeployment.INITIAL_SHARD_CLUSTER_SIZE, SHARD_CLUSTER_SIZE));
        app.start(ImmutableList.of(localhostProvisioningLocation));
        EntityTestUtils.assertAttributeEqualsEventually(deployment, Startable.SERVICE_UP, true);
        return deployment;
    }
    
    @Test(groups = "Integration")
    public void testCanStartAndStopDeployment() {
        MongoDBShardedDeployment deployment = makeAndStartDeployment();
        deployment.stop();
        Assert.assertFalse(deployment.getAttribute(Startable.SERVICE_UP));
    }
    
    @Test(groups = "Integration")
    public void testDeployedStructure() {
        MongoDBShardedDeployment deployment = makeAndStartDeployment();
        MongoDBConfigServerCluster configServers = deployment.getConfigCluster();
        MongoDBRouterCluster routers = deployment.getRouterCluster();
        MongoDBShardCluster shards = deployment.getShardCluster();
        Assert.assertNotNull(configServers);
        Assert.assertNotNull(routers);
        Assert.assertNotNull(shards);
        Assert.assertEquals(configServers.getCurrentSize(), MongoDBShardedDeployment.CONFIG_CLUSTER_SIZE.getDefaultValue());
        Assert.assertEquals(routers.getCurrentSize(), ROUTER_CLUSTER_SIZE);
        Assert.assertEquals(shards.getCurrentSize(), SHARD_CLUSTER_SIZE);
        for (Entity entity : deployment.getShardCluster().getMembers()) {
            Assert.assertEquals(((MongoDBReplicaSet)entity).getCurrentSize(), REPLICASET_SIZE);
        }
        for (Entity entity : configServers.getMembers()) {
            checkEntityTypeAndServiceUp(entity, MongoDBConfigServer.class);
        }
        for (Entity entity : routers.getMembers()) {
            checkEntityTypeAndServiceUp(entity, MongoDBRouter.class);
        }
        for (Entity entity : shards.getMembers()) {
            checkEntityTypeAndServiceUp(entity, MongoDBReplicaSet.class);
        }
    }
    
    @Test(groups = "Integration")
    private void testReadAndWriteDifferentRouters() {
        MongoDBShardedDeployment deployment = makeAndStartDeployment();
        EntityTestUtils.assertAttributeEqualsEventually(deployment, Startable.SERVICE_UP, true);
        MongoDBRouter router1 = (MongoDBRouter) Iterables.get(deployment.getRouterCluster().getMembers(), 0);
        MongoDBRouter router2 = (MongoDBRouter) Iterables.get(deployment.getRouterCluster().getMembers(), 1);
        EntityTestUtils.assertAttributeEqualsEventually(router1, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(router2, Startable.SERVICE_UP, true);
        
        String documentId = MongoDBTestHelper.insert(router1, "meaning-of-life", 42);
        DBObject docOut = MongoDBTestHelper.getById(router2, documentId);
        Assert.assertEquals(docOut.get("meaning-of-life"), 42);
        
        for (Entity entity : Iterables.filter(app.getManagementContext().getEntityManager().getEntitiesInApplication(app), AbstractMongoDBServer.class)) {
            EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        }
    }
    
    private void checkEntityTypeAndServiceUp(Entity entity, Class<? extends Entity> expectedClass) {
        Assert.assertNotNull(entity);
        Assert.assertTrue(expectedClass.isAssignableFrom(entity.getClass()), "expected: " + expectedClass 
                + " on interfaces, found: " + entity.getClass().getInterfaces());
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
    }

}
