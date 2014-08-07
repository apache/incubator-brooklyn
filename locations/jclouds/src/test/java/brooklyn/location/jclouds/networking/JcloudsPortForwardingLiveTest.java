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
package brooklyn.location.jclouds.networking;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.jclouds.compute.domain.NodeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.location.jclouds.AbstractJcloudsLiveTest;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.location.jclouds.networking.JcloudsPortForwarderExtension;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Protocol;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

/**
 * Tests different login options for ssh keys, passwords, etc.
 */
public class JcloudsPortForwardingLiveTest extends AbstractJcloudsLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsPortForwardingLiveTest.class);

    public static final String AWS_EC2_REGION_NAME = AWS_EC2_USEAST_REGION_NAME;
    public static final String AWS_EC2_LOCATION_SPEC = "jclouds:" + AWS_EC2_PROVIDER + ":" + AWS_EC2_REGION_NAME;
    
    // Image: {id=us-east-1/ami-7d7bfc14, providerId=ami-7d7bfc14, name=RightImage_CentOS_6.3_x64_v5.8.8.5, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.0, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, version=5.8.8.5, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String AWS_EC2_CENTOS_IMAGE_ID = "us-east-1/ami-7d7bfc14";

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
    }

    @Test(groups = {"Live"})
    protected void testPortForwardingCallsForwarder() throws Exception {
        final List<HostAndPort> forwards = Lists.newCopyOnWriteArrayList();
        
        JcloudsSshMachineLocation machine = obtainEc2Machine(ImmutableMap.<ConfigKey<?>,Object>of(
                JcloudsLocation.USE_PORT_FORWARDING, true,
                JcloudsLocation.PORT_FORWARDER, new JcloudsPortForwarderExtension() {
                    @Override public HostAndPort openPortForwarding(NodeMetadata node, int targetPort, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr) {
                        String vmIp = Iterables.get(Iterables.concat(node.getPublicAddresses(), node.getPrivateAddresses()), 0);
                        HostAndPort result = HostAndPort.fromParts(vmIp, targetPort);
                        forwards.add(result);
                        return result;
                    }
                }));
        
        assertEquals(forwards.size(), 1, "forwards="+forwards+"; machine="+machine);
        assertEquals(machine.getAddress().getHostAddress(), forwards.get(0).getHostText(), "actual="+forwards+"; machine="+machine);
        assertEquals(machine.getPort(), forwards.get(0).getPort(), "forwards="+forwards+"; machine="+machine);
        assertSshable(machine);
    }
    
    @Test(groups = {"Live"})
    protected void testPortForwardingUsesGivenPort() throws Exception {
        final List<HostAndPort> forwards = Lists.newCopyOnWriteArrayList();
        
        JcloudsSshMachineLocation machine = obtainEc2Machine(ImmutableMap.<ConfigKey<?>,Object>of(
                JcloudsLocation.WAIT_FOR_SSHABLE, false,
                JcloudsLocation.USE_PORT_FORWARDING, true,
                JcloudsLocation.PORT_FORWARDER, new JcloudsPortForwarderExtension() {
                    @Override public HostAndPort openPortForwarding(NodeMetadata node, int targetPort, Optional<Integer> optionalPublicPort, Protocol protocol, Cidr accessingCidr) {
                        HostAndPort result = HostAndPort.fromParts("1.2.3.4", 12345);
                        forwards.add(result);
                        return result;
                    }
                }));
        
        assertEquals(forwards.size(), 1, "forwards="+forwards+"; machine="+machine);
        assertEquals(machine.getAddress().getHostAddress(), "1.2.3.4", "forwards="+forwards+"; machine="+machine);
        assertEquals(machine.getPort(), 12345, "forwards="+forwards+"; machine="+machine);
    }
    
    private JcloudsSshMachineLocation obtainEc2Machine(Map<?,?> conf) throws Exception {
        return obtainMachine(MutableMap.<Object,Object>builder()
                .putAll(conf)
                .putIfAbsent("imageId", AWS_EC2_CENTOS_IMAGE_ID)
                .putIfAbsent("hardwareId", AWS_EC2_SMALL_HARDWARE_ID)
                .putIfAbsent("inboundPorts", ImmutableList.of(22))
                .build());
    }
}
