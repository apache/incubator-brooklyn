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
package org.apache.brooklyn.location.access;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.net.Networking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Entities;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Predicate;
import com.google.common.net.HostAndPort;

public class PortForwardManagerTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(PortForwardManagerTest.class);

    private Map<HostAndPort, HostAndPort> portMapping;
    private SshMachineLocation machine1;
    private SshMachineLocation machine2;
    private PortForwardManager pfm;
    
    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        pfm = (PortForwardManager) mgmt.getLocationRegistry().resolve("portForwardManager(scope=global)");

        machine1 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getInetAddressWithFixedName("1.2.3.4"))
                .configure("port", 1234)
                .configure("user", "myuser"));
        machine2 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", Networking.getInetAddressWithFixedName("1.2.3.5"))
                .configure("port", 1234)
                .configure("user", "myuser"));
    }
    
    @Test
    public void testAssociateWithLocation() throws Exception {
        String publicIpId = "myipid";
        String publicAddress = "5.6.7.8";

        pfm.associate(publicIpId, HostAndPort.fromParts(publicAddress, 40080), machine1, 80);
     
        assertEquals(pfm.lookup(machine1, 80), HostAndPort.fromParts(publicAddress, 40080));
        assertEquals(pfm.lookup(publicIpId, 80), HostAndPort.fromParts(publicAddress, 40080));
    }
    
    @Test
    public void testAssociateWithoutLocation() throws Exception {
        String publicIpId = "myipid";
        String publicAddress = "5.6.7.8";

        pfm.associate(publicIpId, HostAndPort.fromParts(publicAddress, 40080), 80);
     
        assertEquals(pfm.lookup(publicIpId, 80), HostAndPort.fromParts(publicAddress, 40080));
        assertNull(pfm.lookup(machine1, 80));
    }
    
    @Test
    public void testAcquirePortDoesNotReturnDuplicate() throws Exception {
        String publicIpId = "myipid";

        int port1 = pfm.acquirePublicPort(publicIpId);
        int port2 = pfm.acquirePublicPort(publicIpId);
        assertNotEquals(port1, port2);
    }
    
    @Test
    public void testAcquirePortRespectsStartingPortNumber() throws Exception {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
        props.put(PortForwardManager.PORT_FORWARD_MANAGER_STARTING_PORT, 1234);
        LocalManagementContextForTests mgmt2 = new LocalManagementContextForTests(props);
        try {
            PortForwardManager pfm2 = (PortForwardManager) mgmt2.getLocationRegistry().resolve("portForwardManager(scope=global)");
            int port = pfm2.acquirePublicPort("myipid");
            assertEquals(port, 1234);
        } finally {
            Entities.destroyAll(mgmt2);
        }
    }
    
    @Test
    public void testForgetPortMapping() throws Exception {
        String publicIpId = "myipid";
        String publicAddress = "5.6.7.8";

        pfm.associate(publicIpId, HostAndPort.fromParts(publicAddress, 40080), machine1, 80);
        pfm.associate(publicIpId, HostAndPort.fromParts(publicAddress, 40081), machine1, 81);
        pfm.forgetPortMapping(publicIpId, 40080);
        
        assertNull(pfm.lookup(publicIpId, 80));
        assertNull(pfm.lookup(machine1, 80));
        assertEquals(pfm.lookup(publicIpId, 81), HostAndPort.fromParts(publicAddress, 40081));
        assertEquals(pfm.lookup(publicIpId, 81), HostAndPort.fromParts(publicAddress, 40081));
    }
    
    @Test
    public void testForgetPortMappingsOfMachine() throws Exception {
        String publicIpId = "myipid";
        String publicIpId2 = "myipid2";
        String publicAddress = "5.6.7.8";

        pfm.associate(publicIpId, HostAndPort.fromParts(publicAddress, 40080), machine1, 80);
        pfm.associate(publicIpId, HostAndPort.fromParts(publicAddress, 40081), machine1, 81);
        pfm.associate(publicIpId2, HostAndPort.fromParts(publicAddress, 40082), machine2, 80);
        pfm.forgetPortMappings(machine1);
        
        assertNull(pfm.lookup(machine1, 80));
        assertNull(pfm.lookup(machine1, 81));
        assertNull(pfm.lookup(publicIpId, 80));
        assertEquals(pfm.lookup(machine2, 80), HostAndPort.fromParts(publicAddress, 40082));
    }
    
    @Test
    public void testAssociateLegacy() throws Exception {
        String publicIpId = "myipid";
        String publicAddress = "5.6.7.8";

        pfm.acquirePublicPortExplicit(publicIpId, 40080);
        pfm.recordPublicIpHostname(publicIpId, publicAddress);
        pfm.associate(publicIpId, 40080, machine1, 80);
        
        assertEquals(pfm.lookup(publicIpId, 80), HostAndPort.fromParts(publicAddress, 40080));
        assertEquals(pfm.lookup(machine1, 80), HostAndPort.fromParts(publicAddress, 40080));
    }

    @Test
    public void testAssociationListeners() throws Exception {
        final AtomicInteger associationCreatedCount = new AtomicInteger(0);
        final AtomicInteger associationDeletedCount = new AtomicInteger(0);

        final String publicIpId = "myipid";
        final String anotherIpId = "anotherIpId";

        pfm.addAssociationListener(new PortForwardManager.AssociationListener() {
            @Override
            public void onAssociationCreated(PortForwardManager.AssociationMetadata metadata) {
                associationCreatedCount.incrementAndGet();
            }

            @Override
            public void onAssociationDeleted(PortForwardManager.AssociationMetadata metadata) {
                associationDeletedCount.incrementAndGet();
            }
        }, new Predicate<PortForwardManager.AssociationMetadata>() {
            @Override
            public boolean apply(PortForwardManager.AssociationMetadata metadata) {
                return publicIpId.equals(metadata.getPublicIpId());
            }
        });

        pfm.associate(publicIpId, HostAndPort.fromParts(publicIpId, 40080), machine1, 80);
        pfm.associate(anotherIpId, HostAndPort.fromParts(anotherIpId, 40081), machine1, 80);
        pfm.forgetPortMapping(publicIpId, 40080);
        pfm.forgetPortMapping(anotherIpId, 40081);

        assertEquals(associationCreatedCount.get(), 1);
        assertEquals(associationDeletedCount.get(), 1);
    }
}
