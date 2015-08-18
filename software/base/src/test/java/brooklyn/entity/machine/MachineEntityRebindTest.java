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
package brooklyn.entity.machine;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.entity.core.Attributes;
import org.apache.brooklyn.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.test.EntityTestUtils;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.basic.EmptySoftwareProcess;

public class MachineEntityRebindTest extends RebindTestFixtureWithApp {

    @Test(groups = "Integration")
    public void testRebindToMachineEntity() throws Exception {
        EmptySoftwareProcess machine = origApp.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class));
        origApp.start(ImmutableList.of(origManagementContext.getLocationRegistry().resolve("localhost")));
        EntityTestUtils.assertAttributeEqualsEventually(machine, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        rebind(false);
        Entity machine2 = newManagementContext.getEntityManager().getEntity(machine.getId());
        EntityTestUtils.assertAttributeEqualsEventually(machine2, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }

}
