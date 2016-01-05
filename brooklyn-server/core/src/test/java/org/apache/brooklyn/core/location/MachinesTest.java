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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation.LocalhostMachine;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class MachinesTest extends BrooklynAppUnitTestSupport {

    protected String publicAddr = "1.2.3.4";
    protected String privateAddr = "10.1.2.3";
    
    protected SshMachineLocation sshMachine;
    protected SshMachineLocation sshMachineWithoutPrivate;
    protected SshMachineLocation localMachine;
    protected LocalhostMachineProvisioningLocation otherLoc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        sshMachine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", publicAddr)
                .configure(SshMachineLocation.PRIVATE_ADDRESSES, ImmutableList.of(privateAddr)));
        sshMachineWithoutPrivate = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", publicAddr));
        otherLoc = app.newLocalhostProvisioningLocation();
        localMachine = otherLoc.obtain();
    }
    
    @Test
    public void testFindUniqueMachineLocation() throws Exception {
        assertEquals(Machines.findUniqueMachineLocation(ImmutableList.of(sshMachine, otherLoc)).get(), sshMachine);
        assertFalse(Machines.findUniqueMachineLocation(ImmutableList.of(otherLoc)).isPresent());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testFindUniqueSshMachineLocation() throws Exception {
        assertEquals(Machines.findUniqueSshMachineLocation(ImmutableList.of(sshMachine, otherLoc)).get(), sshMachine);
        assertFalse(Machines.findUniqueSshMachineLocation(ImmutableList.of(otherLoc)).isPresent());
    }
    
    @Test
    public void testFindUniqueMachineLocationOfType() throws Exception {
        assertEquals(Machines.findUniqueMachineLocation(ImmutableList.of(sshMachine, otherLoc), SshMachineLocation.class).get(), sshMachine);
        assertFalse(Machines.findUniqueMachineLocation(ImmutableList.of(sshMachine), LocalhostMachine.class).isPresent());
    }
    
    @Test
    public void testFindSubnetIpFromAttribute() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class)
                .location(sshMachine));
        entity.sensors().set(Attributes.SUBNET_ADDRESS, "myaddr");
        
        assertEquals(Machines.findSubnetIp(entity).get(), "myaddr");
    }
    
    @Test
    public void testFindSubnetIpFromLocation() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class)
                .location(sshMachine));
        
        assertEquals(Machines.findSubnetIp(entity).get(), privateAddr);
    }
    
    @Test
    public void testFindSubnetHostnameFromAttribute() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class)
                .location(sshMachine));
        entity.sensors().set(Attributes.SUBNET_HOSTNAME, "myval");
        assertEquals(Machines.findSubnetHostname(entity).get(), "myval");
    }
    
    @Test
    public void testFindSubnetHostnameFromLocation() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class)
                .location(sshMachine));
        
        assertEquals(Machines.findSubnetHostname(entity).get(), publicAddr);
    }
    
    @Test
    public void testFindSubnetOrPrivateIpWithAddressAttributePrefersLocationPrivateIp() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class)
                .location(sshMachine));
        entity.sensors().set(Attributes.ADDRESS, "myval");
        
        assertEquals(Machines.findSubnetOrPrivateIp(entity).get(), privateAddr);
    }
    
    // TODO Why do we only return the "myval" (rather than publicAddr) if Attributes.ADDRESS is set?
    @Test
    public void testFindSubnetOrPrivateIpFromAttribute() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class)
                .location(sshMachine));
        entity.sensors().set(Attributes.ADDRESS, "ignored-val");
        entity.sensors().set(Attributes.SUBNET_ADDRESS, "myval");
        
        assertEquals(Machines.findSubnetOrPrivateIp(entity).get(), "myval");
    }
    
    // TODO Why do we only return the privateAddr (rather than publicAddr) if Attributes.ADDRESS is set?
    @Test
    public void testFindSubnetOrPrivateIpFromLocation() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class)
                .location(sshMachine));
        entity.sensors().set(Attributes.ADDRESS, "ignored-val");
        
        assertEquals(Machines.findSubnetOrPrivateIp(entity).get(), privateAddr);
    }
    
    @Test
    public void testFindSubnetOrPrivateIpFromLocationWithoutPrivate() throws Exception {
        TestEntity entity = app.addChild(EntitySpec.create(TestEntity.class)
                .location(sshMachineWithoutPrivate));
        entity.sensors().set(Attributes.ADDRESS, "ignored-val");
        
        assertEquals(Machines.findSubnetOrPrivateIp(entity).get(), publicAddr);
    }
    
    @Test
    public void testWarnIfLocalhost() throws Exception {
        assertFalse(Machines.warnIfLocalhost(ImmutableList.of(sshMachine), "my message"));
        
        // Visual inspection test - expect a log.warn
        assertTrue(Machines.warnIfLocalhost(ImmutableList.of(localMachine), "my message"));
    }
}
