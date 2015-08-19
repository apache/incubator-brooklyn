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
package org.apache.brooklyn.location.byon;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.core.RecordingMachineLocationCustomizer;
import org.apache.brooklyn.location.core.RecordingMachineLocationCustomizer.Call;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Provisions {@link SshMachineLocation}s in a specific location from a list of known machines
 */
public class FixedListMachineProvisioningLocationTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(FixedListMachineProvisioningLocationTest.class);

    SshMachineLocation machine;
    LocalManagementContext mgmt;
    FixedListMachineProvisioningLocation<SshMachineLocation> provisioner;
    FixedListMachineProvisioningLocation<SshMachineLocation> provisioner2;
    
    @SuppressWarnings("unchecked")
    @BeforeMethod(alwaysRun=true)
    public void createProvisioner() throws UnknownHostException {
        mgmt = LocalManagementContextForTests.newInstance();
        
        machine = mgmt.getLocationManager().createLocation(MutableMap.of("address", Inet4Address.getByName("192.168.144.200")), SshMachineLocation.class);
        provisioner = mgmt.getLocationManager().createLocation(
                MutableMap.of("machines", MutableList.of(machine)),
                FixedListMachineProvisioningLocation.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (provisioner != null) Streams.closeQuietly(provisioner);
        if (provisioner2 != null) Streams.closeQuietly(provisioner2);
        Entities.destroyAll(mgmt);
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

    @SuppressWarnings("unused")
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
        @SuppressWarnings("unused")
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
            SshMachineLocation obtained2 = provisioner.obtain();
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
    
    @Test
    @SuppressWarnings("unchecked")
    public void testMachineChooser() throws Exception {
        List<SshMachineLocation> machines = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            machines.add(mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1."+i))));
        }
        final List<SshMachineLocation> desiredOrder = randomized(machines);
        
        Function<Iterable<? extends MachineLocation>, MachineLocation> chooser = new Function<Iterable<? extends MachineLocation>, MachineLocation>() {
            @Override public MachineLocation apply(Iterable<? extends MachineLocation> input) {
                for (SshMachineLocation contender : desiredOrder) {
                    if (Iterables.contains(input, contender)) {
                        return contender;
                    }
                }
                Assert.fail("No intersection of input="+input+" and desiredOrder="+desiredOrder);
                return null; // unreachable code
            }
        };
        provisioner2 = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", machines)
                .configure(FixedListMachineProvisioningLocation.MACHINE_CHOOSER, chooser));

        List<SshMachineLocation> result = Lists.newArrayList();
        for (int i = 0; i < machines.size(); i++) {
            result.add(provisioner2.obtain());
        }
        assertEquals(result, desiredOrder, "result="+result+"; desired="+desiredOrder);
        LOG.debug("chooser's desiredOrder="+desiredOrder);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testMachineChooserPassedToObtain() throws Exception {
        List<SshMachineLocation> machines = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            machines.add(mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1."+i))));
        }
        final List<SshMachineLocation> desiredOrder = randomized(machines);
        
        Function<Iterable<? extends MachineLocation>, MachineLocation> chooser = new Function<Iterable<? extends MachineLocation>, MachineLocation>() {
            @Override public MachineLocation apply(Iterable<? extends MachineLocation> input) {
                for (SshMachineLocation contender : desiredOrder) {
                    if (Iterables.contains(input, contender)) {
                        return contender;
                    }
                }
                Assert.fail("No intersection of input="+input+" and desiredOrder="+desiredOrder);
                return null; // unreachable code
            }
        };
        provisioner2 = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", machines));

        List<SshMachineLocation> result = Lists.newArrayList();
        for (int i = 0; i < machines.size(); i++) {
            result.add(provisioner2.obtain(ImmutableMap.of(FixedListMachineProvisioningLocation.MACHINE_CHOOSER, chooser)));
        }
        assertEquals(result, desiredOrder, "result="+result+"; desired="+desiredOrder);
        LOG.debug("chooser's desiredOrder="+desiredOrder);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testMachineChooserNotCalledWhenNoMachines() throws Exception {
        List<SshMachineLocation> machines = ImmutableList.of(
                mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.1"))));
        final AtomicInteger callCount = new AtomicInteger();
        
        Function<Iterable<? extends MachineLocation>, MachineLocation> chooser = new Function<Iterable<? extends MachineLocation>, MachineLocation>() {
            @Override public MachineLocation apply(Iterable<? extends MachineLocation> input) {
                callCount.incrementAndGet();
                return Iterables.get(input, 0);
            }
        };
        provisioner2 = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", machines)
                .configure(FixedListMachineProvisioningLocation.MACHINE_CHOOSER, chooser));
        provisioner2.obtain();

        // When no machines available should fail gracefully, without asking the "chooser"
        try {
            provisioner2.obtain();
            fail("Expected "+NoMachinesAvailableException.class.getSimpleName());
        } catch (NoMachinesAvailableException e) {
            // Pass; sensible exception
        }
        assertEquals(callCount.get(), 1);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testFailsWhenMachineChooserReturnsAlreadyAllocatedMachine() throws Exception {
        final SshMachineLocation machine1 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.1")));
        final SshMachineLocation machine2 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.2")));
        List<SshMachineLocation> machines = ImmutableList.of(machine1, machine2);
        
        Function<Iterable<? extends MachineLocation>, MachineLocation> chooser = new Function<Iterable<? extends MachineLocation>, MachineLocation>() {
            @Override public MachineLocation apply(Iterable<? extends MachineLocation> input) {
                return machine1;
            }
        };
        provisioner2 = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", machines)
                .configure(FixedListMachineProvisioningLocation.MACHINE_CHOOSER, chooser));
        provisioner2.obtain();

        // Should fail when tries to return same machine for a second time
        try {
            provisioner2.obtain();
            fail("Expected "+IllegalStateException.class.getSimpleName());
        } catch (IllegalStateException e) {
            if (!e.toString().contains("Machine chooser attempted to choose ")) throw e;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFailsWhenMachineChooserReturnsInvalidMachine() throws Exception {
        final SshMachineLocation machine1 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("1.1.1.1")));
        final SshMachineLocation machineOther = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class).configure("address", Networking.getInetAddressWithFixedName("2.2.2.1")));
        List<SshMachineLocation> machines = ImmutableList.of(machine1);
        
        Function<Iterable<? extends MachineLocation>, MachineLocation> chooser = new Function<Iterable<? extends MachineLocation>, MachineLocation>() {
            @Override public MachineLocation apply(Iterable<? extends MachineLocation> input) {
                return machineOther;
            }
        };
        provisioner2 = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", machines)
                .configure(FixedListMachineProvisioningLocation.MACHINE_CHOOSER, chooser));

        // Call when no machines available should fail gracefully, without asking the "chooser"
        try {
            provisioner2.obtain();
            fail("Expected "+IllegalStateException.class.getSimpleName());
        } catch (IllegalStateException e) {
            if (!e.toString().contains("Machine chooser attempted to choose ")) throw e;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMachineCustomizerSetOnByon() throws Exception {
        machine = mgmt.getLocationManager().createLocation(MutableMap.of("address", Inet4Address.getByName("192.168.144.200")), SshMachineLocation.class);
        RecordingMachineLocationCustomizer customizer = new RecordingMachineLocationCustomizer();
        
        provisioner2 = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", ImmutableList.of(machine))
                .configure(FixedListMachineProvisioningLocation.MACHINE_LOCATION_CUSTOMIZERS.getName(), ImmutableList.of(customizer)));
                
        SshMachineLocation obtained = provisioner2.obtain();
        Assert.assertEquals(Iterables.getOnlyElement(customizer.calls), new RecordingMachineLocationCustomizer.Call("customize", ImmutableList.of(obtained)));
        
        provisioner2.release(obtained);
        assertEquals(customizer.calls.size(), 2);
        Assert.assertEquals(Iterables.get(customizer.calls, 1), new RecordingMachineLocationCustomizer.Call("preRelease", ImmutableList.of(obtained)));
    }

    @Test
    public void testMachineCustomizerSetOnObtainCall() throws Exception {
        RecordingMachineLocationCustomizer customizer = new RecordingMachineLocationCustomizer();
        
        SshMachineLocation obtained = provisioner.obtain(ImmutableMap.of(FixedListMachineProvisioningLocation.MACHINE_LOCATION_CUSTOMIZERS, ImmutableList.of(customizer)));
        Assert.assertEquals(Iterables.getOnlyElement(customizer.calls), new RecordingMachineLocationCustomizer.Call("customize", ImmutableList.of(obtained)));
        
        provisioner.release(obtained);
        assertEquals(customizer.calls.size(), 2);
        Assert.assertEquals(customizer.calls.get(1), new RecordingMachineLocationCustomizer.Call("preRelease", ImmutableList.of(obtained)));
    }

    @Test
    public void testMachineGivenCustomFlagForDurationOfUsage() throws Exception {
        boolean origContains = machine.config().getBag().getAllConfig().containsKey("mykey");
        SshMachineLocation obtained = provisioner.obtain(ImmutableMap.of("mykey", "myNewVal"));
        Object obtainedVal = obtained.config().getBag().getAllConfig().get("mykey");
        provisioner.release(obtained);
        boolean releasedContains = obtained.config().getBag().getAllConfig().containsKey("mykey");
        
        assertEquals(obtained, machine);
        assertFalse(origContains);
        assertEquals(obtainedVal, "myNewVal");
        assertFalse(releasedContains);
    }
    
    @Test
    public void testMachineConfigRestoredToDefaultsOnRelease() throws Exception {
        ConfigKey<String> mykey = ConfigKeys.newStringConfigKey("mykey");
        
        boolean origContains = machine.config().getBag().getAllConfig().containsKey("mykey");
        SshMachineLocation obtained = provisioner.obtain();
        obtained.config().set(mykey, "myNewVal");
        Object obtainedVal = obtained.config().getBag().getAllConfig().get("mykey");
        
        provisioner.release(obtained);
        boolean releasedContains = machine.config().getBag().getAllConfig().containsKey("mykey");
        releasedContains |= (machine.config().get(mykey) != null);
        
        assertEquals(obtained, machine);
        assertFalse(origContains);
        assertEquals(obtainedVal, "myNewVal");
        assertFalse(releasedContains);
    }
    
    @Test
    public void testMachineGivenOverriddenFlagForDurationOfUsage() throws Exception {
        SshMachineLocation machine2 = new SshMachineLocation(
                MutableMap.of("address", Inet4Address.getByName("192.168.144.200"), "mykey", "myval"));
        provisioner2 = new FixedListMachineProvisioningLocation<SshMachineLocation>(
                MutableMap.of("machines", MutableList.of(machine2)));

        Object origVal = machine2.config().getBag().getAllConfig().get("mykey");
        SshMachineLocation obtained = provisioner2.obtain(ImmutableMap.of("mykey", "myNewVal"));
        Object obtainedVal = obtained.config().getBag().getAllConfig().get("mykey");
        provisioner2.release(obtained);
        Object releasedVal = obtained.config().getBag().getAllConfig().get("mykey");
        
        assertEquals(obtained, machine2);
        assertEquals(origVal, "myval");
        assertEquals(obtainedVal, "myNewVal");
        assertEquals(releasedVal, "myval");
    }

    private static <T> List<T> randomized(Iterable<T> list) {
        // TODO inefficient implementation, but don't care for small tests
        Random random = new Random();
        List<T> result = Lists.newLinkedList();
        for (T element : list) {
            int index = (result.isEmpty() ? 0 : random.nextInt(result.size()));
            result.add(index, element);
        }
        return result;
    }
    
    private static void assertUserAndHost(SshMachineLocation l, String user, String host) {
        assertEquals(l.getUser(), user);
        assertEquals(l.getAddress().getHostAddress(), host);
    }
}
