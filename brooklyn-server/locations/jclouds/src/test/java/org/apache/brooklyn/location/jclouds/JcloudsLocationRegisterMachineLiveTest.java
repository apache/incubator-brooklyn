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
package org.apache.brooklyn.location.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.net.InetAddress;
import java.util.Collections;

import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.api.location.MachineLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class JcloudsLocationRegisterMachineLiveTest extends AbstractJcloudsLiveTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(JcloudsLocationRegisterMachineLiveTest.class);
    
    private static final String EUWEST_IMAGE_ID = AWS_EC2_EUWEST_REGION_NAME+"/"+"ami-ce7b6fba";

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_PROVIDER+":"+AWS_EC2_EUWEST_REGION_NAME);
    }

    @Test(groups = { "Live", "Live-sanity" })
    public void testRegisterWithIncorrectId() throws Exception {
        try {
            jcloudsLocation.registerMachine(ImmutableMap.of("id", "incorrectid", "hostname", "myhostname", "user", "myusername"));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("node not found")) {
                // success
            } else {
                throw e;
            }
        }
    }
    
    @Test(groups = { "Live" })
    public void testRegisterVm() throws Exception {
        // FIXME How to create a machine - go directly through jclouds instead?
        //       Going through LocationRegistry.resolve, loc and loc2 might be same instance
        
        // Create a VM through jclouds
        JcloudsSshMachineLocation machine = obtainMachine(ImmutableMap.of("imageId", EUWEST_IMAGE_ID));
        assertTrue(machine.isSshable());
        LOG.info("obtained "+machine);

        String id = checkNotNull(machine.getJcloudsId(), "id");
        InetAddress address = checkNotNull(machine.getAddress(), "address");
        String hostname = checkNotNull(address.getHostName(), "hostname");
        String user = checkNotNull(machine.getUser(), "user");
        
        // Create a new jclouds location, and re-bind the existing VM to that
        JcloudsLocation loc2 = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_PROVIDER+":"+AWS_EC2_EUWEST_REGION_NAME);
        MachineLocation machineLocation = loc2.registerMachine(ImmutableMap.of("id", id, "hostname", hostname, "user", user));
        assertTrue(machineLocation instanceof SshMachineLocation);
        SshMachineLocation machine2 = (SshMachineLocation) machineLocation;

        LOG.info("Registered " + machine2);
        
        // Confirm the re-bound machine is wired up
        assertTrue(machine2.isSshable());
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), ImmutableSet.of(machine2));
        
        // Confirm can release the re-bound machine via the new jclouds location
        loc2.release(machine2);
        assertFalse(machine.isSshable());
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), Collections.emptySet());
    }
    
    @Test(groups = { "Live" })
    public void testRegisterVmDeprecated() throws Exception {
        // FIXME See comments in testRegisterVm

        // Create a VM through jclouds
        JcloudsSshMachineLocation machine = obtainMachine(ImmutableMap.of("imageId", EUWEST_IMAGE_ID));
        assertTrue(machine.isSshable());

        String id = machine.getJcloudsId();
        InetAddress address = machine.getAddress();
        String hostname = address.getHostName();
        String username = machine.getUser();
        
        // Create a new jclouds location, and re-bind the existing VM to that
        JcloudsLocation loc2 = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_PROVIDER+":"+AWS_EC2_EUWEST_REGION_NAME);
        // pass deprecated userName
        MachineLocation machineLocation = loc2.registerMachine(ImmutableMap.of("id", id, "hostname", hostname, "userName", username));
        assertTrue(machineLocation instanceof SshMachineLocation);
        SshMachineLocation machine2 = (SshMachineLocation) machineLocation;

        // Confirm the re-bound machine is wired up
        assertTrue(machine2.isSshable());
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), ImmutableSet.of(machine2));
        
        // Confirm can release the re-bound machine via the new jclouds location
        loc2.release(machine2);
        assertFalse(machine.isSshable());
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), Collections.emptySet());
    }

    // Useful for debugging; accesss a hard-coded existing instance so don't need to wait for provisioning a new one
    @Test(enabled=false, groups = { "Live" })
    public void testRegisterVmToHardcodedInstance() throws Exception {
        String id = "eu-west-1/i-5504f21d";
        InetAddress address = InetAddress.getByName("ec2-176-34-93-58.eu-west-1.compute.amazonaws.com");
        String hostname = address.getHostName();
        String username = "root";
        
        MachineLocation machineLocation = jcloudsLocation.registerMachine(ImmutableMap.of("id", id, "hostname", hostname, "userName", username));
        assertTrue(machineLocation instanceof SshMachineLocation);
        SshMachineLocation machine = (SshMachineLocation) machineLocation;

        // Confirm the re-bound machine is wired up
        assertTrue(machine.isSshable());
        assertEquals(ImmutableSet.copyOf(jcloudsLocation.getChildren()), ImmutableSet.of(machine));
    }
}
