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
package org.apache.brooklyn.entity.nosql.mongodb;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.test.EntityTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MongoDBRebindIntegrationTest extends RebindTestFixtureWithApp {

    private LocalhostMachineProvisioningLocation loc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = origApp.newLocalhostProvisioningLocation();
    }

    @Test(groups = {"Integration"})
    public void testRebindMongoDb() throws Exception {
        MongoDBServer origEntity = origApp.createAndManageChild(EntitySpec.create(MongoDBServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        origApp.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEventuallyNonNull(origEntity, MongoDBServer.STATUS_BSON);

        // rebind
        rebind();
        final MongoDBServer newEntity = (MongoDBServer) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MongoDBServer.class));

        // confirm effectors still work on entity
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, MongoDBServer.SERVICE_UP, true);
        newEntity.stop();
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, MongoDBServer.SERVICE_UP, false);
    }
}
