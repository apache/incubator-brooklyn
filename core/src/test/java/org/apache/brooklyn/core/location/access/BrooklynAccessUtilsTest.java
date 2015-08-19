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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.SupportsPortForwarding;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.net.Cidr;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;

public class BrooklynAccessUtilsTest extends BrooklynAppUnitTestSupport {

    protected PortForwardManager pfm;
    private TestEntity entity;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        pfm = (PortForwardManager) mgmt.getLocationRegistry().resolve("portForwardManager(scope=global)");
    }
    
    @Test
    public void testBrooklynAccessibleAddressFindsPreexistingMapping() throws Exception {
        final int privatePort = 8080;
        final String publicNatIp = "1.2.3.4";
        final int publicNatPort = 12000;
        
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure(SshMachineLocation.TCP_PORT_MAPPINGS, ImmutableMap.of(privatePort, publicNatIp+":"+publicNatPort)));
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(BrooklynAccessUtils.PORT_FORWARDING_MANAGER, pfm)
                .location(machine));

        assertEquals(BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, privatePort), HostAndPort.fromParts(publicNatIp, publicNatPort));
    }
    
    @Test
    public void testBrooklynAccessibleAddressUsesPrivateHostPortIfNoMapping() throws Exception {
        final String privateIp = "127.1.2.3";
        final int privatePort = 8080;
        
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class));
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(BrooklynAccessUtils.PORT_FORWARDING_MANAGER, pfm)
                .location(machine));
        entity.setAttribute(Attributes.HOSTNAME, privateIp);

        assertEquals(BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, privatePort), HostAndPort.fromParts(privateIp, privatePort));
    }
    
    @Test
    public void testBrooklynAccessibleAddressFailsIfNoMappingAndNoHostname() throws Exception {
        final int privatePort = 8080;
        
        SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class));
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(BrooklynAccessUtils.PORT_FORWARDING_MANAGER, pfm)
                .location(machine));

        try {
            BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, privatePort);
            fail();
        } catch (IllegalStateException e) {
            if (!e.toString().contains("no host.name")) throw e;
            // success
        }
    }
    
    @Test
    public void testBrooklynAccessibleAddressRequestsNewPortForwarding() throws Exception {
        final int privatePort = 8080;
        
        RecordingSupportsPortForwarding machine = mgmt.getLocationManager().createLocation(LocationSpec.create(RecordingSupportsPortForwarding.class));
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(BrooklynAccessUtils.PORT_FORWARDING_MANAGER, pfm)
                .configure(BrooklynAccessUtils.MANAGEMENT_ACCESS_CIDR, Cidr.UNIVERSAL)
                .location(machine));

        HostAndPort result = BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, privatePort);
        HostAndPort portForwarded = machine.mappings.get(privatePort);
        assertNotNull(portForwarded);
        assertEquals(result, portForwarded);
    }
    
    @SuppressWarnings("serial") // TODO location should not be serializable; will be changed in future release
    public static class RecordingSupportsPortForwarding extends SshMachineLocation implements SupportsPortForwarding {
        protected final String publicIp = "1.2.3.4";
        protected final Map<Integer, HostAndPort> mappings = Maps.newConcurrentMap();
        private final AtomicInteger nextPort = new AtomicInteger(12000);
        
        
        @Override
        public HostAndPort getSocketEndpointFor(Cidr accessor, int privatePort) {
            HostAndPort result = mappings.get(privatePort);
            if (result == null) {
                int publicPort = nextPort.getAndIncrement();
                result = HostAndPort.fromParts(publicIp, publicPort);
                mappings.put(privatePort, result);
            }
            return result;
        }
    }
}
