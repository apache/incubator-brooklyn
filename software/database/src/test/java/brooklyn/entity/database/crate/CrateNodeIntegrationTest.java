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
package brooklyn.entity.database.crate;

import static org.testng.Assert.assertFalse;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.entity.TestApplication;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.trait.Startable;
import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;

public class CrateNodeIntegrationTest {

    private TestApplication app;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Integration")
    public void testCanStartAndStop() throws Exception {
        CrateNode entity = app.createAndManageChild(EntitySpec.create(CrateNode.class));
        app.start(ImmutableList.of(localhostProvisioningLocation));

        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEventuallyNonNull(entity, CrateNode.SERVER_NAME);

        entity.stop();
        assertFalse(entity.getAttribute(Startable.SERVICE_UP));
    }
}
