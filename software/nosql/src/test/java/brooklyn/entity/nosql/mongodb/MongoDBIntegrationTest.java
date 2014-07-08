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
package brooklyn.entity.nosql.mongodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.mongodb.DBObject;

public class MongoDBIntegrationTest {

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

    @Test(groups = "Integration")
    public void testCanStartAndStop() throws Exception {
        MongoDBServer entity = app.createAndManageChild(EntitySpec.create(MongoDBServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(localhostProvisioningLocation));

        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        entity.stop();
        assertFalse(entity.getAttribute(Startable.SERVICE_UP));
    }

    @Test(groups = "Integration", dependsOnMethods = { "testCanStartAndStop" })
    public void testCanReadAndWrite() throws Exception {
        MongoDBServer entity = app.createAndManageChild(EntitySpec.create(MongoDBServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(localhostProvisioningLocation));

        String id = MongoDBTestHelper.insert(entity, "hello", "world!");
        DBObject docOut = MongoDBTestHelper.getById(entity, id);
        assertEquals(docOut.get("hello"), "world!");
    }

    @Test(groups = "Integration", dependsOnMethods = { "testCanStartAndStop" })
    public void testPollInsertCountSensor() throws Exception {
        MongoDBServer entity = app.createAndManageChild(EntitySpec.create(MongoDBServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(localhostProvisioningLocation));
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);

        EntityTestUtils.assertAttributeEventuallyNonNull(entity, MongoDBServer.OPCOUNTERS_INSERTS);
        Long initialInserts = entity.getAttribute(MongoDBServer.OPCOUNTERS_INSERTS);
        MongoDBTestHelper.insert(entity, "a", Boolean.TRUE);
        MongoDBTestHelper.insert(entity, "b", Boolean.FALSE);
        EntityTestUtils.assertAttributeEqualsEventually(entity, MongoDBServer.OPCOUNTERS_INSERTS, initialInserts + 2);
    }

}
