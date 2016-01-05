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
package org.apache.brooklyn.core.location;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.util.Arrays;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineDetails;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.BasicMachineDetails;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class MachineDetailsTest {

    private static final Logger LOG = LoggerFactory.getLogger(MachineDetailsTest.class);

    TestApplication app;
    ManagementContext mgmt;
    SshMachineLocation host;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        mgmt = app.getManagementContext();

        LocalhostMachineProvisioningLocation localhost = mgmt.getLocationManager().createLocation(
                LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        host = localhost.obtain();
        app.start(Arrays.asList(host));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    @Test(groups = "Integration")
    public void testGetMachineDetails() {
        Task<BasicMachineDetails> detailsTask = app.getExecutionContext().submit(
                BasicMachineDetails.taskForSshMachineLocation(host));
        MachineDetails machine = detailsTask.getUnchecked();
        LOG.info("Found the following on localhost: {}", machine);
        assertNotNull(machine);
        OsDetails details = machine.getOsDetails();
        assertNotNull(details);
        assertNotNull(details.getArch());
        assertNotNull(details.getName());
        assertNotNull(details.getVersion());
        assertFalse(details.getArch().startsWith("architecture:"), "architecture prefix not removed from details");
        assertFalse(details.getName().startsWith("name:"), "name prefix not removed from details");
        assertFalse(details.getVersion().startsWith("version:"), "version prefix not removed from details");
    }
}
