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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.location.LocationSpec;
import brooklyn.management.LocationManager;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class LocationManagementTest extends BrooklynAppUnitTestSupport {

    private LocationManager locationManager;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        locationManager = mgmt.getLocationManager();
    }
    
    @Test
    public void testCreateLocationUsingSpec() {
        SshMachineLocation loc = locationManager.createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "1.2.3.4"));
        
        assertEquals(loc.getAddress().getHostAddress(), "1.2.3.4");
        assertSame(locationManager.getLocation(loc.getId()), loc);
    }
    
    @Test
    public void testCreateLocationUsingResolver() {
        String spec = "byon:(hosts=\"1.1.1.1\")";
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = (FixedListMachineProvisioningLocation<SshMachineLocation>) mgmt.getLocationRegistry().resolve(spec);
        SshMachineLocation machine = Iterables.getOnlyElement(loc.getAllMachines());
        
        assertSame(locationManager.getLocation(loc.getId()), loc);
        assertSame(locationManager.getLocation(machine.getId()), machine);
    }
    
    @Test
    public void testChildrenOfManagedLocationAutoManaged() {
        String spec = "byon:(hosts=\"1.1.1.1\")";
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = (FixedListMachineProvisioningLocation<SshMachineLocation>) mgmt.getLocationRegistry().resolve(spec);
        SshMachineLocation machine = new SshMachineLocation(ImmutableMap.of("address", "1.2.3.4"));

        loc.addChild(machine);
        assertSame(locationManager.getLocation(machine.getId()), machine);
        assertTrue(machine.isManaged());
        
        loc.removeChild(machine);
        assertNull(locationManager.getLocation(machine.getId()));
        assertFalse(machine.isManaged());
    }
}
