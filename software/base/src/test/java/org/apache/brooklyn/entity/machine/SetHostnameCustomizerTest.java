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
package org.apache.brooklyn.entity.machine;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class SetHostnameCustomizerTest extends BrooklynAppUnitTestSupport {

    private SetHostnameCustomizer customizer;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        customizer = new SetHostnameCustomizer(ConfigBag.newInstance());
    }
    
    @Test
    public void testGeneratedHostnameUsesPrivateIp() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("privateAddresses", ImmutableList.of("1.2.3.4", "5.6.7.8"))
                .configure("address", "4.3.2.1"));
        
        assertEquals(customizer.generateHostname(machine), "ip-1-2-3-4-"+machine.getId());
    }
    
    @Test
    public void testGeneratedHostnameUsesPublicIpIfNoPrivate() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "4.3.2.1"));
        
        assertEquals(customizer.generateHostname(machine), "ip-4-3-2-1-"+machine.getId());
    }
    
    @Test
    public void testGeneratedHostnameUsesPublicIpIfEmptyListOfPrivate() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("privateAddresses", ImmutableList.of())
                .configure("address", "4.3.2.1"));
        
        assertEquals(customizer.generateHostname(machine), "ip-4-3-2-1-"+machine.getId());
    }
}
