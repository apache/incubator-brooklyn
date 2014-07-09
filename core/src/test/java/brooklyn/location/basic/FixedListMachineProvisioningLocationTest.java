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
import static org.testng.Assert.fail;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.stream.Streams;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Provisions {@link SshMachineLocation}s in a specific location from a list of known machines
 */
public class FixedListMachineProvisioningLocationTest {
    SshMachineLocation machine;
    LocalManagementContext mgmt;
    FixedListMachineProvisioningLocation<SshMachineLocation> provisioner;
    FixedListMachineProvisioningLocation<SshMachineLocation> provisioner2;
    
    @BeforeMethod(alwaysRun=true)
    public void createProvisioner() throws UnknownHostException {
        mgmt = new LocalManagementContext();
        
        machine = mgmt.getLocationManager().createLocation(MutableMap.of("address", Inet4Address.getByName("192.168.144.200")), SshMachineLocation.class);
        provisioner = mgmt.getLocationManager().createLocation(
                MutableMap.of("machines", MutableList.of(machine)),
                FixedListMachineProvisioningLocation.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (provisioner != null) Streams.closeQuietly(provisioner);
        if (provisioner2 != null) Streams.closeQuietly(provisioner2);
    }
    
    @Test
    public void testSetsChildLocations() throws NoMachinesAvailableException {
        // Available machines should be listed as children
		assertEquals(ImmutableList.copyOf(provisioner.getChildren()), ImmutableList.of(machine));
        
        // In-use machines should also be listed as children
        provisioner.obtain();
        assertEquals(ImmutableList.copyOf(provisioner.getChildren()), ImmutableList.of(machine));
    }

    @Test
    public void canObtainMachine() throws NoMachinesAvailableException {
        SshMachineLocation obtained = provisioner.obtain();
        assertEquals(obtained, machine);
    }

    @Test(expectedExceptions = { NoMachinesAvailableException.class })
    public void throwsExceptionIfNoMachinesAvailable() throws NoMachinesAvailableException {
        SshMachineLocation machine1 = provisioner.obtain();
        SshMachineLocation machine2 = provisioner.obtain();
        fail("Did not throw NoMachinesAvailableException as expected");
    }

    @Test
    public void canGetAMachineReturnItAndObtainItAgain() throws NoMachinesAvailableException {
        SshMachineLocation obtained = provisioner.obtain();
        provisioner.release(obtained);
        SshMachineLocation obtained2 = provisioner.obtain();
        assertEquals(obtained2, machine);
    }

    @Test
    public void theBuilder() throws NoMachinesAvailableException {
        provisioner2 =
            new FixedListMachineProvisioningLocation.Builder(mgmt.getLocationManager()).
                user("u1").
                addAddress("192.168.0.1").
                addAddress("u2@192.168.0.2").
                addAddress("192.168.0.{3,4}").
                addAddresses("192.168.0.{6-8}").
                addAddressMultipleTimes("192.168.0.{8,7}", 2).
                addAddress("u3@192.168.0.{11-20}").
                build();
        assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0.1");
        assertUserAndHost(provisioner2.obtain(), "u2", "192.168.0.2");
        for (int i=3; i<=4; i++) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int i=6; i<=8; i++) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int j=0; j<2; j++)
            for (int i=8; i>=7; i--) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int i=11; i<=20; i++) assertUserAndHost(provisioner2.obtain(), "u3", "192.168.0."+i);
        try { 
            provisioner2.obtain();
            fail("Should not have obtained");  //throws error so not caught below
        } catch (Exception e) {
            /** expected */
        }
    }
    
    @Test
    public void theBuilderLegacy() throws NoMachinesAvailableException {
        provisioner2 =
            new FixedListMachineProvisioningLocation.Builder(mgmt.getLocationManager()).
                user("u1").
                addAddress("192.168.0.1").
                addAddress("u2@192.168.0.2").
                addAddress("192.168.0.{3,4}").
                addAddresses("192.168.0.{6-8}").
                addAddressMultipleTimes("192.168.0.{8,7}", 2).
                addAddress("u3@192.168.0.{11-20}").
                build();
        assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0.1");
        assertUserAndHost(provisioner2.obtain(), "u2", "192.168.0.2");
        for (int i=3; i<=4; i++) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int i=6; i<=8; i++) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int j=0; j<2; j++)
            for (int i=8; i>=7; i--) assertUserAndHost(provisioner2.obtain(), "u1", "192.168.0."+i);
        for (int i=11; i<=20; i++) assertUserAndHost(provisioner2.obtain(), "u3", "192.168.0."+i);
        try { 
            provisioner2.obtain();
            fail("Should not have obtained");  //throws error so not caught below
        } catch (Exception e) {
            /** expected */
        }
    }
    
    @Test(expectedExceptions = { IllegalStateException.class })
    public void throwsExceptionIfTryingToReleaseUnallocationMachine() throws NoMachinesAvailableException, UnknownHostException {
        SshMachineLocation obtained = provisioner.obtain();
        provisioner.release(new SshMachineLocation(MutableMap.of("address", Inet4Address.getByName("192.168.144.201"))));
        fail("Did not throw IllegalStateException as expected");
    }
    
    @Test
    public void testCanAddMachineToPool() throws UnknownHostException, NoMachinesAvailableException {
        SshMachineLocation machine2 = new SshMachineLocation(
                MutableMap.of("address", Inet4Address.getByName("192.168.144.200")));
        provisioner2 = new FixedListMachineProvisioningLocation<SshMachineLocation>(
                MutableMap.of("machines", MutableList.of()));
        provisioner2.addMachine(machine2);
        
        assertEquals(ImmutableList.copyOf(provisioner2.getChildren()), ImmutableList.of(machine2));
        assertEquals(ImmutableSet.copyOf(provisioner2.getAvailable()), ImmutableSet.of(machine2));
        
        SshMachineLocation obtained = provisioner2.obtain();
        assertEquals(obtained, machine2);
        
        // Can only obtain the added machien once though (i.e. not added multiple times somehow)
        try {
            SshMachineLocation obtained2 = provisioner2.obtain();
            fail("obtained="+obtained2);
        } catch (NoMachinesAvailableException e) {
            // success
        }
    }

    @Test
    public void testCanRemoveAvailableMachineFromPool() {
        provisioner.removeMachine(machine);
        
        Assert.assertTrue(provisioner.getChildren().isEmpty());
        Assert.assertTrue(provisioner.getAvailable().isEmpty());
        
        try {
            SshMachineLocation obtained = provisioner.obtain();
            fail("obtained="+obtained);
        } catch (NoMachinesAvailableException e) {
            // success
        }
    }

    @Test
    public void testCanRemoveObtainedMachineFromPoolSoNotReallocated() throws NoMachinesAvailableException {
        SshMachineLocation obtained = provisioner.obtain();
        provisioner.removeMachine(obtained);
        
        // Continue to know about the machine until it is returned
        assertEquals(ImmutableList.copyOf(provisioner.getChildren()), ImmutableList.of(machine));
        Assert.assertTrue(provisioner.getAvailable().isEmpty());

        // When released, the machine is then removed entirely
        provisioner.release(obtained);

        Assert.assertTrue(provisioner.getChildren().isEmpty());
        Assert.assertTrue(provisioner.getAvailable().isEmpty());

        // So no machines left; cannot re-obtain
        try {
            SshMachineLocation obtained2 = provisioner2.obtain();
            fail("obtained="+obtained2);
        } catch (NoMachinesAvailableException e) {
            // success
        }
    }

    @Test
    public void testObtainDesiredMachineThrowsIfNotKnown() throws Exception {
        SshMachineLocation machine2 = new SshMachineLocation(
                MutableMap.of("address", Inet4Address.getByName("192.168.144.201")));
        try {
            SshMachineLocation obtained = provisioner.obtain(MutableMap.of("desiredMachine", machine2));
            fail("obtained="+obtained);
        } catch (IllegalStateException e) {
            if (!e.toString().contains("machine unknown")) throw e;
        }
    }

    @Test
    public void testObtainDesiredMachineThrowsIfInUse() throws Exception {
        provisioner.addMachine(new SshMachineLocation(
                MutableMap.of("address", Inet4Address.getByName("192.168.144.201"))));
        SshMachineLocation obtained = provisioner.obtain();
        try {
            SshMachineLocation obtained2 = provisioner.obtain(MutableMap.of("desiredMachine", obtained));
            fail("obtained2="+obtained2);
        } catch (IllegalStateException e) {
            if (!e.toString().contains("machine in use")) throw e;
        }
    }

    @Test
    public void testObtainDesiredMachineReturnsDesired() throws Exception {
        int desiredMachineIndex = 10;
        SshMachineLocation desiredMachine = null;
        for (int i = 0; i < 20; i++) {
            SshMachineLocation newMachine = new SshMachineLocation(
                    MutableMap.of("address", Inet4Address.getByName("192.168.144."+(201+i))));
            if (i == desiredMachineIndex) desiredMachine = newMachine;
            provisioner.addMachine(newMachine);
        }
        SshMachineLocation obtained = provisioner.obtain(MutableMap.of("desiredMachine", desiredMachine));
        assertEquals(obtained, desiredMachine);
    }

    @Test
    public void testAddAndRemoveChildUpdatesMachinesSet() throws Exception {
        SshMachineLocation anotherMachine = new SshMachineLocation(
                MutableMap.of("address", Inet4Address.getByName("192.168.144.201")));
        provisioner.addChild(anotherMachine);
        assertEquals(provisioner.getAllMachines(), ImmutableSet.of(machine, anotherMachine));
        
        provisioner.removeChild(anotherMachine);
        assertEquals(provisioner.getAllMachines(), ImmutableSet.of(machine));
    }
    
    @Test
    public void testCanAddAlreadyParentedMachine() throws UnknownHostException, NoMachinesAvailableException {
        provisioner.obtain(); // so no machines left
        
        FixedListMachineProvisioningLocation<SshMachineLocation> provisioner2 = new FixedListMachineProvisioningLocation.Builder(mgmt.getLocationManager())
            .addAddress("1.2.3.4")
            .build();
        SshMachineLocation machine = provisioner2.obtain();
        
        provisioner.addMachine(machine);
        assertEquals(provisioner.obtain(), machine);
    }

    @Test
    public void testCanCreateWithAlreadyParentedMachine() throws UnknownHostException, NoMachinesAvailableException {
        machine = provisioner.obtain();
        
        FixedListMachineProvisioningLocation<SshMachineLocation> provisioner2 = new FixedListMachineProvisioningLocation.Builder(mgmt.getLocationManager())
            .add(machine)
            .build();
        assertEquals(provisioner2.obtain(), machine);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMachinesObtainedInOrder() throws Exception {
        List<SshMachineLocation> machines = ImmutableList.of(
                mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.1"))),
                mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.6"))),
                mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.3"))),
                mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.4"))),
                mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.5"))));
        
        provisioner2 = mgmt.getLocationManager().createLocation(
                MutableMap.of("machines", machines),
                FixedListMachineProvisioningLocation.class);

        for (SshMachineLocation expected : machines) {
            assertEquals(provisioner2.obtain(), expected);
        }
    }
    
    private static void assertUserAndHost(SshMachineLocation l, String user, String host) {
        assertEquals(l.getUser(), user);
        assertEquals(l.getAddress().getHostAddress(), host);
    }
}
