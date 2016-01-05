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
package org.apache.brooklyn.entity.proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.EntityFactory;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class AbstractControllerTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(AbstractControllerTest.class);
    
    FixedListMachineProvisioningLocation<?> loc;
    Cluster cluster;
    TrackingAbstractController controller;
    
    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        List<SshMachineLocation> machines = new ArrayList<SshMachineLocation>();
        for (int i=1; i<=10; i++) {
            SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                    .configure("address", Inet4Address.getByName("1.1.1."+i)));
            machines.add(machine);
        }
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", machines));
        
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", new ClusteredEntity.Factory()));
        
        controller = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                .configure("serverPool", cluster) 
                .configure("portNumberSensor", ClusteredEntity.HTTP_PORT)
                .configure("domain", "mydomain"));
        
        app.start(ImmutableList.of(loc));
    }
    
    // Fixes bug where entity that wrapped an AS7 entity was never added to nginx because hostname+port
    // was set after service_up. Now we listen to those changes and reset the nginx pool when these
    // values change.
    @Test
    public void testUpdateCalledWhenChildHostnameAndPortChanges() throws Exception {
        TestEntity child = cluster.addChild(EntitySpec.create(TestEntity.class));
        cluster.addMember(child);

        List<Collection<String>> u = Lists.newArrayList(controller.getUpdates());
        assertTrue(u.isEmpty(), "expected no updates, but got "+u);
        
        child.sensors().set(Startable.SERVICE_UP, true);
        
        // TODO Ugly sleep to allow AbstractController to detect node having been added
        Thread.sleep(100);
        
        child.sensors().set(ClusteredEntity.HOSTNAME, "mymachine");
        child.sensors().set(Attributes.SUBNET_HOSTNAME, "mymachine");
        child.sensors().set(ClusteredEntity.HTTP_PORT, 1234);
        assertEventuallyExplicitAddressesMatch(ImmutableList.of("mymachine:1234"));
        
        child.sensors().set(ClusteredEntity.HOSTNAME, "mymachine2");
        child.sensors().set(Attributes.SUBNET_HOSTNAME, "mymachine2");
        assertEventuallyExplicitAddressesMatch(ImmutableList.of("mymachine2:1234"));
        
        child.sensors().set(ClusteredEntity.HTTP_PORT, 1235);
        assertEventuallyExplicitAddressesMatch(ImmutableList.of("mymachine2:1235"));
        
        child.sensors().set(ClusteredEntity.HOSTNAME, null);
        child.sensors().set(Attributes.SUBNET_HOSTNAME, null);
        assertEventuallyExplicitAddressesMatch(ImmutableList.<String>of());
    }

    @Test
    public void testUpdateCalledWithAddressesOfNewChildren() {
        // First child
        cluster.resize(1);
        EntityLocal child = (EntityLocal) Iterables.getOnlyElement(cluster.getMembers());
        
        List<Collection<String>> u = Lists.newArrayList(controller.getUpdates());
        assertTrue(u.isEmpty(), "expected empty list but got "+u);
        
        child.sensors().set(ClusteredEntity.HTTP_PORT, 1234);
        child.sensors().set(Startable.SERVICE_UP, true);
        assertEventuallyAddressesMatchCluster();

        // Second child
        cluster.resize(2);
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(cluster.getMembers().size(), 2);
            }});
        EntityLocal child2 = (EntityLocal) Iterables.getOnlyElement(MutableSet.builder().addAll(cluster.getMembers()).remove(child).build());
        
        child2.sensors().set(ClusteredEntity.HTTP_PORT, 1234);
        child2.sensors().set(Startable.SERVICE_UP, true);
        assertEventuallyAddressesMatchCluster();
        
        // And remove all children; expect all addresses to go away
        cluster.resize(0);
        assertEventuallyAddressesMatchCluster();
    }

    @Test(groups = "Integration", invocationCount=10)
    public void testUpdateCalledWithAddressesOfNewChildrenManyTimes() {
        testUpdateCalledWithAddressesOfNewChildren();
    }
    
    @Test
    public void testUpdateCalledWithAddressesRemovedForStoppedChildren() {
        // Get some children, so we can remove one...
        cluster.resize(2);
        for (Entity it: cluster.getMembers()) { 
            ((EntityLocal)it).sensors().set(ClusteredEntity.HTTP_PORT, 1234);
            ((EntityLocal)it).sensors().set(Startable.SERVICE_UP, true);
        }
        assertEventuallyAddressesMatchCluster();

        // Now remove one child
        cluster.resize(1);
        assertEquals(cluster.getMembers().size(), 1);
        assertEventuallyAddressesMatchCluster();
    }

    @Test
    public void testUpdateCalledWithAddressesRemovedForServiceDownChildrenThatHaveClearedHostnamePort() {
        // Get some children, so we can remove one...
        cluster.resize(2);
        for (Entity it: cluster.getMembers()) { 
            ((EntityLocal)it).sensors().set(ClusteredEntity.HTTP_PORT, 1234);
            ((EntityLocal)it).sensors().set(Startable.SERVICE_UP, true);
        }
        assertEventuallyAddressesMatchCluster();

        // Now unset host/port, and remove children
        // Note the unsetting of hostname is done in SoftwareProcessImpl.stop(), so this is realistic
        for (Entity it : cluster.getMembers()) {
            ((EntityLocal)it).sensors().set(ClusteredEntity.HTTP_PORT, null);
            ((EntityLocal)it).sensors().set(ClusteredEntity.HOSTNAME, null);
            ((EntityLocal)it).sensors().set(Startable.SERVICE_UP, false);
        }
        assertEventuallyAddressesMatch(ImmutableList.<Entity>of());
    }

    @Test
    public void testUsesHostAndPortSensor() throws Exception {
        controller = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                .configure("serverPool", cluster) 
                .configure("hostAndPortSensor", ClusteredEntity.HOST_AND_PORT)
                .configure("domain", "mydomain"));
        controller.start(Arrays.asList(loc));
        
        TestEntity child = cluster.addChild(EntitySpec.create(TestEntity.class));
        cluster.addMember(child);

        List<Collection<String>> u = Lists.newArrayList(controller.getUpdates());
        assertTrue(u.isEmpty(), "expected no updates, but got "+u);
        
        child.sensors().set(Startable.SERVICE_UP, true);
        
        // TODO Ugly sleep to allow AbstractController to detect node having been added
        Thread.sleep(100);
        
        child.sensors().set(ClusteredEntity.HOST_AND_PORT, "mymachine:1234");
        assertEventuallyExplicitAddressesMatch(ImmutableList.of("mymachine:1234"));
    }

    @Test
    public void testFailsIfSetHostAndPortAndHostnameOrPortNumberSensor() throws Exception {
        try {
            TrackingAbstractController controller2 = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                    .configure("serverPool", cluster) 
                    .configure("hostAndPortSensor", ClusteredEntity.HOST_AND_PORT)
                    .configure("hostnameSensor", ClusteredEntity.HOSTNAME)
                    .configure("domain", "mydomain"));
            controller2.start(Arrays.asList(loc));
        } catch (Exception e) {
            IllegalStateException unwrapped = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (unwrapped != null && unwrapped.toString().contains("Must not set Sensor")) {
                // success
            } else {
                throw e;
            }
        }

        try {
            TrackingAbstractController controller3 = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                    .configure("serverPool", cluster) 
                    .configure("hostAndPortSensor", ClusteredEntity.HOST_AND_PORT)
                    .configure("portNumberSensor", ClusteredEntity.HTTP_PORT)
                    .configure("domain", "mydomain"));
            controller3.start(Arrays.asList(loc));
        } catch (Exception e) {
            IllegalStateException unwrapped = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (unwrapped != null && unwrapped.toString().contains("Must not set Sensor")) {
                // success
            } else {
                throw e;
            }
        }
    }

    // Manual visual inspection test. Previously it repeatedly logged:
    //     Unable to construct hostname:port representation for TestEntityImpl{id=jzwSBRQ2} (null:null); skipping in TrackingAbstractControllerImpl{id=tOn4k5BA}
    // every time the service-up was set to true again.
    @Test
    public void testMemberWithoutHostAndPortDoesNotLogErrorRepeatedly() throws Exception {
        controller = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                .configure("serverPool", cluster) 
                .configure("domain", "mydomain"));
        controller.start(ImmutableList.of(loc));
        
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        cluster.addMember(child);

        for (int i = 0; i < 100; i++) {
            child.sensors().set(Attributes.SERVICE_UP, true);
        }
        
        Thread.sleep(100);
        List<Collection<String>> u = Lists.newArrayList(controller.getUpdates());
        assertTrue(u.isEmpty(), "expected no updates, but got "+u);
    }

    private void assertEventuallyAddressesMatchCluster() {
        assertEventuallyAddressesMatch(cluster.getMembers());
    }

    private void assertEventuallyAddressesMatch(final Collection<Entity> expectedMembers) {
        Asserts.succeedsEventually(MutableMap.of("timeout", 15000), new Runnable() {
                @Override public void run() {
                    assertAddressesMatch(locationsToAddresses(1234, expectedMembers));
                }} );
    }

    private void assertEventuallyExplicitAddressesMatch(final Collection<String> expectedAddresses) {
        Asserts.succeedsEventually(MutableMap.of("timeout", 15000), new Runnable() {
            @Override public void run() {
                assertAddressesMatch(expectedAddresses);
            }} );
    }

    private void assertAddressesMatch(final Collection<String> expectedAddresses) {
        List<Collection<String>> u = Lists.newArrayList(controller.getUpdates());
        Collection<String> last = Iterables.getLast(u, null);
        log.debug("test "+u.size()+" updates, expecting "+expectedAddresses+"; actual "+last);
        assertTrue(u.size() > 0);
        assertEquals(ImmutableSet.copyOf(last), ImmutableSet.copyOf(expectedAddresses), "actual="+last+" expected="+expectedAddresses);
        assertEquals(last.size(), expectedAddresses.size(), "actual="+last+" expected="+expectedAddresses);
    }

    private Collection<String> locationsToAddresses(int port, Collection<Entity> entities) {
        Set<String> result = MutableSet.of();
        for (Entity e: entities) {
            result.add( ((SshMachineLocation) e.getLocations().iterator().next()) .getAddress().getHostName()+":"+port);
        }
        return result;
    }

    public static class ClusteredEntity extends TestEntityImpl {
        public static class Factory implements EntityFactory<ClusteredEntity> {
            @Override
            public ClusteredEntity newEntity(Map flags, Entity parent) {
                return new ClusteredEntity(flags, parent);
            }
        }
        public ClusteredEntity(Map flags, Entity parent) { super(flags,parent); }
        public ClusteredEntity(Entity parent) { super(MutableMap.of(),parent); }
        public ClusteredEntity(Map flags) { super(flags,null); }
        public ClusteredEntity() { super(MutableMap.of(),null); }
        
        @SetFromFlag("hostname")
        public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
        
        @SetFromFlag("port")
        public static final AttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT;
        
        @SetFromFlag("hostAndPort")
        public static final AttributeSensor<String> HOST_AND_PORT = Attributes.HOST_AND_PORT;
        
        MachineProvisioningLocation provisioner;
        
        public void start(Collection<? extends Location> locs) {
            provisioner = (MachineProvisioningLocation) locs.iterator().next();
            MachineLocation machine;
            try {
                machine = provisioner.obtain(MutableMap.of());
            } catch (NoMachinesAvailableException e) {
                throw Exceptions.propagate(e);
            }
            addLocations(Arrays.asList(machine));
            sensors().set(HOSTNAME, machine.getAddress().getHostName());
            sensors().set(Attributes.SUBNET_HOSTNAME, machine.getAddress().getHostName());
        }
        public void stop() {
            if (provisioner!=null) provisioner.release((MachineLocation) firstLocation());
        }
    }
}
