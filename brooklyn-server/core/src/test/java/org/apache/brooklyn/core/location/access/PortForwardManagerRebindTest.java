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
package org.apache.brooklyn.core.location.access;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.io.File;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ha.MementoCopyMode;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.mgmt.persist.BrooklynPersistenceUtils;
import org.apache.brooklyn.core.mgmt.persist.FileBasedObjectStore;
import org.apache.brooklyn.core.mgmt.persist.PersistenceObjectStore;
import org.apache.brooklyn.core.mgmt.rebind.RebindOptions;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;

public class PortForwardManagerRebindTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManagerRebindTest.class);

    private String machineAddress = "1.2.3.4";
    private SshMachineLocation origSimulatedMachine;

    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        origSimulatedMachine = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getInetAddressWithFixedName(machineAddress))
                .configure("port", 1234)
                .configure("user", "myuser"));
    }
    
    @Test
    public void testAssociationPreservedOnRebind() throws Exception {
        String publicIpId = "5.6.7.8";
        String publicAddress = "5.6.7.8";

        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntity.class));
        PortForwardManager origPortForwardManager = origEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);

        // We first wait for persisted, to ensure that it is the PortForwardManager.onChanged that is causing persistence.
        RebindTestUtils.waitForPersisted(origApp);
        origPortForwardManager.associate(publicIpId, HostAndPort.fromParts(publicAddress, 40080), origSimulatedMachine, 80);
     
        newApp = rebind();
        
        // After rebind, confirm that lookups still work
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        Location newSimulatedMachine = newApp.getManagementContext().getLocationManager().getLocation(origSimulatedMachine.getId());
        PortForwardManager newPortForwardManager = newEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);
        
        assertEquals(newPortForwardManager.lookup(newSimulatedMachine, 80), HostAndPort.fromParts(publicAddress, 40080));
        assertEquals(newPortForwardManager.lookup(publicIpId, 80), HostAndPort.fromParts(publicAddress, 40080));
    }
    
    @Test
    public void testAssociationPreservedOnStateExport() throws Exception {
        String publicIpId = "5.6.7.8";
        String publicAddress = "5.6.7.8";

        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntity.class));
        PortForwardManager origPortForwardManager = origEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);

        origPortForwardManager.associate(publicIpId, HostAndPort.fromParts(publicAddress, 40080), origSimulatedMachine, 80);

        String label = origManagementContext.getManagementNodeId()+"-"+Time.makeDateSimpleStampString();
        PersistenceObjectStore targetStore = BrooklynPersistenceUtils.newPersistenceObjectStore(origManagementContext, null, 
            "tmp/web-persistence-"+label+"-"+Identifiers.makeRandomId(4));
        File dir = ((FileBasedObjectStore)targetStore).getBaseDir();
        // only register the parent dir because that will prevent leaks for the random ID
        Os.deleteOnExitEmptyParentsUpTo(dir.getParentFile(), dir.getParentFile());
        BrooklynPersistenceUtils.writeMemento(origManagementContext, targetStore, MementoCopyMode.LOCAL);            

        RebindTestUtils.waitForPersisted(origApp);
        log.info("Using manual export dir "+dir+" for rebind instead of "+mementoDir);
        newApp = rebind(RebindOptions.create().mementoDir(dir));
        
        // After rebind, confirm that lookups still work
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        Location newSimulatedMachine = newApp.getManagementContext().getLocationManager().getLocation(origSimulatedMachine.getId());
        PortForwardManager newPortForwardManager = newEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);
        
        assertEquals(newPortForwardManager.lookup(newSimulatedMachine, 80), HostAndPort.fromParts(publicAddress, 40080));
        assertEquals(newPortForwardManager.lookup(publicIpId, 80), HostAndPort.fromParts(publicAddress, 40080));
        
        // delete the dir here, to be more likely not to leak it on failure
        newManagementContext.getRebindManager().stop();
        Os.deleteRecursively(dir);
    }
    
    @Test
    public void testAssociationPreservedOnRebindLegacy() throws Exception {
        String publicIpId = "5.6.7.8";
        String publicAddress = "5.6.7.8";

        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntity.class));
        PortForwardManager origPortForwardManager = origEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);

        // We first wait for persisted, to ensure that it is the PortForwardManager.onChanged that is causing persistence.
        RebindTestUtils.waitForPersisted(origApp);
        origPortForwardManager.recordPublicIpHostname(publicIpId, publicAddress);
        origPortForwardManager.acquirePublicPortExplicit(publicIpId, 40080);
        origPortForwardManager.associate(publicIpId, 40080, origSimulatedMachine, 80);
     
        newApp = rebind();
        
        // After rebind, confirm that lookups still work
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        Location newSimulatedMachine = newApp.getManagementContext().getLocationManager().getLocation(origSimulatedMachine.getId());
        PortForwardManager newPortForwardManager = newEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);
        
        assertEquals(newPortForwardManager.getPublicIpHostname(publicIpId), publicAddress);
        assertEquals(newPortForwardManager.lookup(newSimulatedMachine, 80), HostAndPort.fromParts(publicAddress, 40080));
        assertEquals(newPortForwardManager.lookup(publicIpId, 80), HostAndPort.fromParts(publicAddress, 40080));
    }
    
    @Test
    public void testAcquirePortCounterPreservedOnRebindLegacy() throws Exception {
        String publicIpId = "5.6.7.8";

        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(MyEntity.class));
        PortForwardManager origPortForwardManager = origEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);

        // We first wait for persisted, to ensure that it is the PortForwardManager.onChanged that is causing persistence.
        RebindTestUtils.waitForPersisted(origApp);
        int acquiredPort = origPortForwardManager.acquirePublicPort(publicIpId);
     
        newApp = rebind();
        
        // After rebind, confirm that lookups still work
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        PortForwardManager newPortForwardManager = newEntity.getConfig(MyEntity.PORT_FORWARD_MANAGER);
        
        int acquiredPort2 = newPortForwardManager.acquirePublicPort(publicIpId);
        assertNotEquals(acquiredPort, acquiredPort2);
    }
    
    public static class MyEntity extends TestEntityImpl {
        public static final ConfigKey<PortForwardManager> PORT_FORWARD_MANAGER = ConfigKeys.newConfigKey(PortForwardManager.class, "myentity.portForwardManager");
        public static final AttributeSensor<PortForwardManager> PORT_FORWARD_MANAGER_LIVE = Sensors.newSensor(PortForwardManager.class, "myentity.portForwardManager.live");

        @Override
        public void init() {
            super.init();
            
            if (getConfig(PORT_FORWARD_MANAGER) == null) {
                PortForwardManager pfm = (PortForwardManager) getManagementContext().getLocationRegistry().resolve("portForwardManager(scope=global)");
                sensors().set(PORT_FORWARD_MANAGER_LIVE, pfm);
                config().set(PORT_FORWARD_MANAGER, pfm);
            }
        }
    }
}
