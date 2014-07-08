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
package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;

public class LocalhostProvisioningAndAccessTest {

    private LocalManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = new LocalManagementContext(BrooklynProperties.Factory.newDefault());
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    @Test(groups="Integration")
    public void testProvisionAndConnect() throws Exception {
        Location location = mgmt.getLocationRegistry().resolve("localhost");
        assertTrue(location instanceof LocalhostMachineProvisioningLocation);
        SshMachineLocation m = ((LocalhostMachineProvisioningLocation)location).obtain();
        int result = m.execCommands("test", Arrays.asList("echo hello world"));
        assertEquals(result, 0);
    }
    
}
