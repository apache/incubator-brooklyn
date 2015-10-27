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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.StringPredicates;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
    
    @Test
    public void testGeneratedHostnameIfNoIps() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocationWithNoPublicIps.class)
                .configure("address", "4.3.2.1"));
        
        assertEquals(customizer.generateHostname(machine), "ip-none-"+machine.getId());
    }
    
    @Test
    public void testCustomizerIgnoresNonSshMachines() throws Exception {
        WinRmMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(WinRmMachineLocation.class)
                .configure("address", "4.3.2.1"));
        
        // Confirm not called (as that would cause error); and visual inspection that logs a nice message.
        customizer.customize(machine);
    }
    
    @Test
    public void testCustomizerIgnoresNonMatchingMachine() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocationThrowsException.class)
                .configure("address", "4.3.2.1"));

        customizer = new SetHostnameCustomizer(ConfigBag.newInstance()
                .configure(SetHostnameCustomizer.MACHINE_FILTER, Predicates.not(Predicates.<MachineLocation>equalTo(machine))));

        // Confirm not called (as that would cause error); and visual inspection that logs a nice message.
        customizer.customize(machine);
    }
    
    @Test
    public void testCustomizerPropagatesException() throws Exception {
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocationThrowsException.class)
                .configure("address", "4.3.2.1"));
        
        FixedListMachineProvisioningLocation<?> provisioningLoc = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", MutableSet.of(machine)));

        MachineEntity entity = app.createAndManageChild(EntitySpec.create(MachineEntity.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true)
                .configure(MachineEntity.PROVISIONING_PROPERTIES.subKey(JcloudsLocation.MACHINE_LOCATION_CUSTOMIZERS.getName()), ImmutableSet.of(customizer)));

        try {
            entity.start(ImmutableList.of(provisioningLoc));
            fail();
        } catch (RuntimeException e) {
            if (Exceptions.getFirstThrowableMatching(e, Predicates.compose(StringPredicates.containsLiteral("simulated failure"), Functions.toStringFunction())) == null) {
                throw e;
            };
            assertFalse(Machines.findUniqueMachineLocation(entity.getLocations(), SshMachineLocation.class).isPresent());
        }
        
    }
    
    public static class SshMachineLocationWithNoPublicIps extends SshMachineLocation {
        public SshMachineLocationWithNoPublicIps() {
        }
        @Override
        public Set<String> getPublicAddresses() {
            return ImmutableSet.<String>of();
        }
    }
    
    public static class SshMachineLocationThrowsException extends SshMachineLocation {
        public SshMachineLocationThrowsException() {
        }
        @Override
        public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
            throw new RuntimeException("simulated failure");
        }
    }
}
