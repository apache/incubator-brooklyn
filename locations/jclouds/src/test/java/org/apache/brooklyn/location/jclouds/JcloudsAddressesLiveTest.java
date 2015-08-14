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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.location.basic.Locations;
import org.apache.brooklyn.location.basic.SshMachineLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.stream.Streams;

/**
 * Tests that the correct address is advertised for the VM - for its public/private, 
 * its subnet hostname, etc.
 */
public class JcloudsAddressesLiveTest extends AbstractJcloudsLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsAddressesLiveTest.class);

    public static final String AWS_EC2_REGION_NAME = AWS_EC2_USEAST_REGION_NAME;
    public static final String AWS_EC2_LOCATION_SPEC = "jclouds:" + AWS_EC2_PROVIDER + (AWS_EC2_REGION_NAME == null ? "" : ":" + AWS_EC2_REGION_NAME);
    
    // Image: {id=us-east-1/ami-7d7bfc14, providerId=ami-7d7bfc14, name=RightImage_CentOS_6.3_x64_v5.8.8.5, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.0, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, version=5.8.8.5, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String AWS_EC2_CENTOS_IMAGE_ID = "us-east-1/ami-7d7bfc14";

    // Image: {id=us-east-1/ami-d0f89fb9, providerId=ami-d0f89fb9, name=ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=12.04, description=099720109477/ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, is64Bit=true}, description=099720109477/ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, version=20130411.1, status=AVAILABLE[available], loginUser=ubuntu, userMetadata={owner=099720109477, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String AWS_EC2_UBUNTU_IMAGE_ID = "us-east-1/ami-d0f89fb9";
    
    // Image: {id=us-east-1/ami-5e008437, providerId=ami-5e008437, name=RightImage_Ubuntu_10.04_x64_v5.8.8.3, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=10.04, description=rightscale-us-east/RightImage_Ubuntu_10.04_x64_v5.8.8.3.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_Ubuntu_10.04_x64_v5.8.8.3.manifest.xml, version=5.8.8.3, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
    // Uses "root" as loginUser
    public static final String AWS_EC2_UBUNTU_10_IMAGE_ID = "us-east-1/ami-5e008437";

    public static final String RACKSPACE_LOCATION_SPEC = "jclouds:" + RACKSPACE_PROVIDER;
    
    // Image: {id=LON/c52a0ca6-c1f2-4cd1-b7d6-afbcd1ebda22, providerId=c52a0ca6-c1f2-4cd1-b7d6-afbcd1ebda22, name=CentOS 6.0, location={scope=ZONE, id=LON, description=LON, parent=rackspace-cloudservers-uk, iso3166Codes=[GB-SLG]}, os={family=centos, name=CentOS 6.0, version=6.0, description=CentOS 6.0, is64Bit=true}, description=CentOS 6.0, status=AVAILABLE, loginUser=root, userMetadata={os_distro=centos, com.rackspace__1__visible_core=1, com.rackspace__1__build_rackconnect=1, com.rackspace__1__options=0, image_type=base, cache_in_nova=True, com.rackspace__1__source=kickstart, org.openstack__1__os_distro=org.centos, com.rackspace__1__release_build_date=2013-07-25_18-56-29, auto_disk_config=True, com.rackspace__1__release_version=5, os_type=linux, com.rackspace__1__visible_rackconnect=1, com.rackspace__1__release_id=210, com.rackspace__1__visible_managed=0, com.rackspace__1__build_core=1, org.openstack__1__os_version=6.0, org.openstack__1__architecture=x64, com.rackspace__1__build_managed=0}}
    public static final String RACKSPACE_CENTOS_IMAGE_NAME_REGEX = "CentOS 6.0";
    
    protected JcloudsSshMachineLocation machine;
    
    @Test(groups = {"Live"})
    protected void testAwsEc2Addresses() throws Exception {
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine(ImmutableMap.<String,Object>of());
        assertSshable(machine);

        String locationAddress = machine.getAddress().getHostName();
        InetAddress address = machine.getAddress();
        Set<String> publicAddresses = machine.getPublicAddresses();
        Set<String> privateAddresses = machine.getPrivateAddresses();
        String subnetIp = machine.getSubnetIp();
        String hostname = machine.getHostname();
        String subnetHostname = machine.getSubnetHostname();
        String msg = "locationAddress="+locationAddress+"; address="+address+"; publicAddrs="+publicAddresses+"; privateAddrs="+privateAddresses+"; subnetIp="+subnetIp+"; hostname="+hostname+"; subnetHostname="+subnetHostname;
        LOG.info("node: "+msg);

        // On AWS, machine advertises its FQ hostname that is accessible from inside and outside the region
        assertReachable(machine, locationAddress, msg);
        assertReachableFromMachine(machine, locationAddress, msg);

        assertReachable(machine, address, msg);
        
        assertTrue(publicAddresses.size() > 0, msg);
        for (String publicAddress: publicAddresses) {
            assertReachable(machine, publicAddress, msg);
        }
        
        // On AWS, private address is not reachable from outside.
        // If you ran this test from the same AWS region, it would fail!
        assertTrue(privateAddresses.size() > 0, msg);
        for (String privateAddress: privateAddresses) {
            assertReachableFromMachine(machine, privateAddress, msg);
            assertNotReachable(machine, privateAddress, msg);
        }
        
        assertNotNull(subnetIp, msg);
        assertReachableFromMachine(machine, subnetIp, msg);

        // hostname is reachable from inside; not necessarily reachable from outside
        assertNotNull(hostname, msg);
        assertReachableFromMachine(machine, hostname, msg);
        
        assertNotNull(subnetHostname, msg);
        assertReachableFromMachine(machine, subnetHostname, msg);
    }

    @Test(groups = {"Live"})
    protected void testRackspaceAddresses() throws Exception {
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(RACKSPACE_LOCATION_SPEC);
        
        machine = createRackspaceMachine(ImmutableMap.<String,Object>of());
        assertSshable(machine);

        String locationAddress = machine.getAddress().getHostAddress();
        InetAddress address = machine.getAddress();
        Set<String> publicAddresses = machine.getPublicAddresses();
        Set<String> privateAddresses = machine.getPrivateAddresses();
        String subnetIp = machine.getSubnetIp();
        String hostname = machine.getHostname();
        String subnetHostname = machine.getSubnetHostname();
        String msg = "locationAddress="+locationAddress+"; address="+address+"; publicAddrs="+publicAddresses+"; privateAddrs="+privateAddresses+"; subnetIp="+subnetIp+"; hostname="+hostname+"; subnetHostname="+subnetHostname;
        LOG.info("node: "+msg);

        // On Rackspace, IP is accessible from inside and outside.
        assertReachable(machine, locationAddress, msg);
        assertReachableFromMachine(machine, locationAddress, msg);

        assertReachable(machine, address, msg);
        
        assertTrue(publicAddresses.size() > 0, msg);
        for (String publicAddress: publicAddresses) {
            assertReachable(machine, publicAddress, msg);
        }
        
        // On Rackspace, don't care if no private addresses
        for (String privateAddress: privateAddresses) {
            assertReachableFromMachine(machine, privateAddress, msg);
            assertNotReachable(machine, privateAddress, msg);
        }
        
        assertNotNull(subnetIp, msg);
        assertReachableFromMachine(machine, subnetIp, msg);

        // hostname is reachable from inside; not necessarily reachable from outside
        assertNotNull(hostname, msg);
        assertReachableFromMachine(machine, hostname, msg);
        
        assertNotNull(subnetHostname, msg);
        assertReachableFromMachine(machine, subnetHostname, msg);
    }

    private void assertReachable(SshMachineLocation machine, InetAddress addr, String msg) {
        assertReachable(machine, addr.getHostAddress(), msg);
    }

    private void assertReachable(SshMachineLocation machine, String addr, String msg) {
        assertReachability(true, machine, addr, msg);
    }
    
    private void assertNotReachable(SshMachineLocation machine, String addr, String msg) {
        assertReachability(false, machine, addr, msg);
    }

    private void assertReachability(boolean expectedReachable, SshMachineLocation machine, String addr, String msg) {
        SshMachineLocation tmpMachine = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure(machine.config().getBag().getAllConfig())
                .configure("address", addr));
        try {
            boolean sshable = tmpMachine.isSshable();
            assertEquals(sshable, expectedReachable, addr+" not sshable; "+msg);
        } finally {
            Locations.unmanage(tmpMachine);
        }
    }

    // TODO Assumes that "ping" will work; i.e. that ICMP is not firewall'ed
    private void assertReachableFromMachine(SshMachineLocation machine, String addr, String msg) {
        OutputStream outStream = new ByteArrayOutputStream();
        OutputStream errStream = new ByteArrayOutputStream();
        int result = machine.execScript(MutableMap.of("out", outStream, "err", errStream), "reach "+addr, ImmutableList.of("ping -c 1 "+addr));
        String outString = outStream.toString();
        String errString = errStream.toString();
        assertEquals(result, 0, "result="+0+"; err="+errString+"; out="+outString+"; msg="+msg);
    }

    @Override
    protected void releaseMachine(JcloudsSshMachineLocation machine) {
        jcloudsLocation.release(machine);
    }
    
    private JcloudsSshMachineLocation createEc2Machine(Map<String,? extends Object> conf) throws Exception {
        return obtainMachine(MutableMap.<String,Object>builder()
                .putAll(conf)
                .putIfAbsent("imageId", AWS_EC2_CENTOS_IMAGE_ID)
                .putIfAbsent("hardwareId", AWS_EC2_SMALL_HARDWARE_ID)
                .putIfAbsent("inboundPorts", ImmutableList.of(22))
                .build());
    }
    
    private JcloudsSshMachineLocation createRackspaceMachine(Map<String,? extends Object> conf) throws Exception {
        return obtainMachine(MutableMap.<String,Object>builder()
                .putAll(conf)
                .putIfAbsent("inboundPorts", ImmutableList.of(22))
                .build());
    }
    
    protected void assertSshable(Map<?,?> machineConfig) {
        SshMachineLocation machineWithThatConfig = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure(machineConfig));
        try {
            assertSshable(machineWithThatConfig);
        } finally {
            Streams.closeQuietly(machineWithThatConfig);
        }
    }
}
