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
package org.apache.brooklyn.location.jclouds.networking;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.location.access.PortForwardManager;
import org.apache.brooklyn.core.location.access.PortForwardManagerImpl;
import org.apache.brooklyn.location.jclouds.AbstractJcloudsStubbedLiveTest;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.Template;
import org.jclouds.domain.LoginCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.jclouds.JcloudsSshMachineLocation;
import org.apache.brooklyn.util.net.Cidr;
import org.apache.brooklyn.util.net.Protocol;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

/**
 * The VM creation is stubbed out, but it still requires live access (i.e. real account credentials)
 * to generate the template etc.
 * 
 * We supply a ComputeServiceRegistry that delegates to the real instance for everything except
 * VM creation and deletion. For those operations, it delegates to a NodeCreator that 
 * returns a dummy NodeMetadata, recording all calls made to it.
 */
public class JcloudsPortForwardingStubbedLiveTest extends AbstractJcloudsStubbedLiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(JcloudsPortForwardingStubbedLiveTest.class);

    static class RecordingJcloudsPortForwarderExtension implements JcloudsPortForwarderExtension {
        final PortForwardManager pfm;
        final List<List<Object>> opens = Lists.newCopyOnWriteArrayList();
        final List<List<Object>> closes = Lists.newCopyOnWriteArrayList();
        int nextPublicPort = 12345;
        
        RecordingJcloudsPortForwarderExtension(PortForwardManager pfm) {
            this.pfm = pfm;
        }
        @Override public HostAndPort openPortForwarding(NodeMetadata node, int targetPort, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr) {
            opens.add(ImmutableList.of(node, targetPort, optionalPublicPort, protocol, accessingCidr));
            HostAndPort result = HostAndPort.fromParts("1.2.3.4", nextPublicPort++);
            pfm.associate(node.getId(), result, targetPort);
            return result;
        }
        @Override public void closePortForwarding(NodeMetadata node, int targetPort, HostAndPort publicHostAndPort, Protocol protocol) {
            closes.add(ImmutableList.of(node, targetPort, publicHostAndPort, protocol));
            pfm.forgetPortMapping(node.getId(), publicHostAndPort.getPort());
        }
    }

    @Override
    protected NodeCreator newNodeCreator() {
        return new NodeCreator() {
            int nextIpSuffix = 2;
            @Override
            protected NodeMetadata newNode(String group, Template template) {
                int ipSuffix = nextIpSuffix++;
                NodeMetadata result = new NodeMetadataBuilder()
                        .id("myid-"+ipSuffix)
                        .credentials(LoginCredentials.builder().identity("myuser").credential("mypassword").build())
                        .loginPort(22)
                        .status(Status.RUNNING)
                        .publicAddresses(ImmutableList.of("173.194.32."+ipSuffix))
                        .privateAddresses(ImmutableList.of("172.168.10."+ipSuffix))
                        .build();
                return result;
            }
        };
    }

    @Test(groups = {"Live", "Live-sanity"})
    protected void testPortForwardingCallsForwarder() throws Exception {
        PortForwardManager pfm = new PortForwardManagerImpl();
        RecordingJcloudsPortForwarderExtension portForwarder = new RecordingJcloudsPortForwarderExtension(pfm);
        
        JcloudsSshMachineLocation machine = obtainMachine(ImmutableMap.<ConfigKey<?>,Object>of(
                JcloudsLocation.USE_PORT_FORWARDING, true,
                JcloudsLocation.PORT_FORWARDER, portForwarder));
        
        NodeMetadata created = nodeCreator.created.get(0);
        assertEquals(nodeCreator.created.size(), 1, "created="+nodeCreator.created+"; machine="+machine);
        assertEquals(machine.getNode(), created);
        assertEquals(portForwarder.opens.size(), 1, "opens="+portForwarder.opens+"; machine="+machine);
        assertEquals(portForwarder.opens.get(0).get(0), created);
        assertEquals(portForwarder.opens.get(0).get(1), 22);
        assertEquals(portForwarder.opens.get(0).get(3), Protocol.TCP);
        assertEquals(portForwarder.opens.get(0).get(4), Cidr.UNIVERSAL);
        assertEquals(machine.getSshHostAndPort(), HostAndPort.fromParts("1.2.3.4", 12345));
        
        releaseMachine(machine);
        String destroyed = nodeCreator.destroyed.get(0);
        assertEquals(nodeCreator.destroyed.size(), 1, "destroyed="+nodeCreator.destroyed+"; machine="+machine);
        assertEquals(destroyed, created.getId());
        assertEquals(portForwarder.closes.size(), 1, "closes="+portForwarder.closes+"; machine="+machine);
        assertEquals(portForwarder.closes.get(0).get(0), created);
        assertEquals(portForwarder.closes.get(0).get(1), 22);
        assertEquals(portForwarder.closes.get(0).get(2), HostAndPort.fromParts("1.2.3.4", 12345));
        assertEquals(portForwarder.closes.get(0).get(3), Protocol.TCP);
    }
    
    @Test(groups = {"Live", "Live-sanity"})
    protected void testDeregistersWithPortForwardManagerOnRelease() throws Exception {
        PortForwardManager pfm = new PortForwardManagerImpl();
        RecordingJcloudsPortForwarderExtension portForwarder = new RecordingJcloudsPortForwarderExtension(pfm);
        
        JcloudsSshMachineLocation machine = obtainMachine(ImmutableMap.<ConfigKey<?>,Object>of(
                JcloudsLocation.PORT_FORWARDER, portForwarder,
                JcloudsLocation.PORT_FORWARDING_MANAGER, pfm));
        
        // Add an association for this machine - expect that to be deleted when the machine is released.
        HostAndPort publicHostAndPort = HostAndPort.fromParts("1.2.3.4", 1234);
        pfm.associate("mypublicip", publicHostAndPort, machine, 80);
        assertEquals(pfm.lookup(machine, 80), publicHostAndPort);
        assertEquals(pfm.lookup("mypublicip", 80), publicHostAndPort);

        // Release
        releaseMachine(machine);
        
        // Expect to have been cleared from PortForwardManager's records
        assertNull(pfm.lookup(machine, 80));
        assertNull(pfm.lookup("mypublicip", 80));
        
        // And for port-forwarding to have been closed
        assertEquals(portForwarder.closes.size(), 1, "closes="+portForwarder.closes+"; machine="+machine);
        assertEquals(portForwarder.closes.get(0).get(1), 80);
        assertEquals(portForwarder.closes.get(0).get(2), HostAndPort.fromParts("1.2.3.4", 1234));
        assertEquals(portForwarder.closes.get(0).get(3), Protocol.TCP);
    }
    
    @Test(groups = {"Live", "Live-sanity"})
    protected void testReleaseVmDoesNotImpactOtherVms() throws Exception {
        PortForwardManager pfm = new PortForwardManagerImpl();
        RecordingJcloudsPortForwarderExtension portForwarder = new RecordingJcloudsPortForwarderExtension(pfm);
        
        JcloudsSshMachineLocation machine1 = obtainMachine(ImmutableMap.<ConfigKey<?>,Object>of(
                JcloudsLocation.USE_PORT_FORWARDING, true,
                JcloudsLocation.PORT_FORWARDER, portForwarder,
                JcloudsLocation.PORT_FORWARDING_MANAGER, pfm));
        
        JcloudsSshMachineLocation machine2 = obtainMachine(ImmutableMap.<ConfigKey<?>,Object>of(
                JcloudsLocation.USE_PORT_FORWARDING, true,
                JcloudsLocation.PORT_FORWARDER, portForwarder,
                JcloudsLocation.PORT_FORWARDING_MANAGER, pfm));
        
        NodeMetadata node1 = nodeCreator.created.get(0);

        // Add an association for machine2 - expect that not to be touched when machine1 is released.
        HostAndPort publicHostAndPort = HostAndPort.fromParts("1.2.3.4", 1234);
        pfm.associate("mypublicip", publicHostAndPort, machine2, 80);

        // Release machine1
        releaseMachine(machine1);
        
        // Expect machine2 to still be registered
        assertEquals(pfm.lookup(machine2, 80), publicHostAndPort);
        assertEquals(pfm.lookup("mypublicip", 80), publicHostAndPort);
        
        // And no calls to "close" for machine2; just for machine1's port 22
        assertEquals(portForwarder.closes.size(), 1, "closes="+portForwarder.closes+"; machine1="+machine1);
        assertEquals(portForwarder.closes.get(0).get(0), node1);
        assertEquals(portForwarder.closes.get(0).get(1), 22);
    }
}
