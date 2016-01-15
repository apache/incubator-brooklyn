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
package org.apache.brooklyn.entity;

import static org.testng.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsLocationConfig;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Runs a test with many different distros and versions.
 */
public abstract class AbstractEc2LiveTest extends BrooklynAppLiveTestSupport {
    
    // FIXME Currently have just focused on test_Debian_6; need to test the others as well!

    // TODO No nice fedora VMs
    
    // TODO Instead of this sub-classing approach, we could use testng's "provides" mechanism
    // to say what combo of provider/region/flags should be used. The problem with that is the
    // IDE integration: one can't just select a single test to run.
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEc2LiveTest.class);

    public static final String PROVIDER = "aws-ec2";
    public static final String REGION_NAME = "us-east-1";
    public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String TINY_HARDWARE_ID = "t1.micro";
    public static final String SMALL_HARDWARE_ID = "m1.small";
    
    protected BrooklynProperties brooklynProperties;
    
    protected Location jcloudsLocation;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-description-regex");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-name-regex");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".image-id");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".inboundPorts");
        brooklynProperties.remove("brooklyn.jclouds."+PROVIDER+".hardware-id");

        // Also removes scriptHeader (e.g. if doing `. ~/.bashrc` and `. ~/.profile`, then that can cause "stdin: is not a tty")
        brooklynProperties.remove("brooklyn.ssh.config.scriptHeader");
        
        mgmt = new LocalManagementContextForTests(brooklynProperties);
        
        super.setUp();
    }

    // Image ids for Debian: https://wiki.debian.org/Cloud/AmazonEC2Image/Squeeze
    @Test(groups = {"Live"})
    public void test_Debian_6() throws Exception {
        // release codename "squeeze"
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-5e12dc36", "loginUser", "admin", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Debian_7_2() throws Exception {
        // release codename "wheezy"
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-5586a43c", "loginUser", "admin", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_10_0() throws Exception {
        // Image: {id=us-east-1/ami-5e008437, providerId=ami-5e008437, name=RightImage_Ubuntu_10.04_x64_v5.8.8.3, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=10.04, description=rightscale-us-east/RightImage_Ubuntu_10.04_x64_v5.8.8.3.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_Ubuntu_10.04_x64_v5.8.8.3.manifest.xml, version=5.8.8.3, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-5e008437", "loginUser", "root", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_12_0() throws Exception {
        // Image: {id=us-east-1/ami-d0f89fb9, providerId=ami-d0f89fb9, name=ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=12.04, description=099720109477/ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, is64Bit=true}, description=099720109477/ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, version=20130411.1, status=AVAILABLE[available], loginUser=ubuntu, userMetadata={owner=099720109477, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-d0f89fb9", "loginUser", "ubuntu", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_CentOS_6_3() throws Exception {
        // TODO Should openIptables=true be the default?!
        // Image: {id=us-east-1/ami-a96b01c0, providerId=ami-a96b01c0, name=CentOS-6.3-x86_64-GA-EBS-02-85586466-5b6c-4495-b580-14f72b4bcf51-ami-bb9af1d2.1, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.3, description=aws-marketplace/CentOS-6.3-x86_64-GA-EBS-02-85586466-5b6c-4495-b580-14f72b4bcf51-ami-bb9af1d2.1, is64Bit=true}, description=CentOS-6.3-x86_64-GA-EBS-02 on EBS x86_64 20130527:1219, version=bb9af1d2.1, status=AVAILABLE[available], loginUser=root, userMetadata={owner=679593333241, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}})
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-a96b01c0", "hardwareId", SMALL_HARDWARE_ID, JcloudsLocation.OPEN_IPTABLES.getName(), true));
    }

    @Test(groups = {"Live"})
    public void test_CentOS_5() throws Exception {
        // Image: {id=us-east-1/ami-e4bffe8d, providerId=ami-e4bffe8d, name=RightImage_CentOS_5.9_x64_v12.11.4_EBS, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=5.0, description=411009282317/RightImage_CentOS_5.9_x64_v12.11.4_EBS, is64Bit=true}, description=RightImage_CentOS_5.9_x64_v12.11.4_EBS, version=12.11.4_EBS, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-e4bffe8d", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        // Image: {id=us-east-1/ami-a35a33ca, providerId=ami-a35a33ca, name=RHEL-6.3_GA-x86_64-5-Hourly2, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=rhel, arch=paravirtual, version=6.0, description=309956199498/RHEL-6.3_GA-x86_64-5-Hourly2, is64Bit=true}, description=309956199498/RHEL-6.3_GA-x86_64-5-Hourly2, version=Hourly2, status=AVAILABLE[available], loginUser=root, userMetadata={owner=309956199498, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-a35a33ca", "hardwareId", SMALL_HARDWARE_ID, JcloudsLocationConfig.OPEN_IPTABLES.getName(), "true"));
    }
    
    @Test(groups = {"Live"})
    public void test_Suse_11sp3() throws Exception {
        // Image: {id=us-east-1/ami-c08fcba8, providerId=ami-c08fcba8, name=suse-sles-11-sp3-v20150127-pv-ssd-x86_64, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=suse, arch=paravirtual, version=, description=amazon/suse-sles-11-sp3-v20150127-pv-ssd-x86_64, is64Bit=true}, description=SUSE Linux Enterprise Server 11 Service Pack 3 (PV, 64-bit, SSD-Backed), version=x86_64, status=AVAILABLE[available], loginUser=root, userMetadata={owner=013907871322, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-c08fcba8", "hardwareId", SMALL_HARDWARE_ID, "loginUser", "ec2-user"));//, JcloudsLocationConfig.OPEN_IPTABLES.getName(), "true"));
    }
    
    protected void runTest(Map<String,?> flags) throws Exception {
        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(getClass().getName()))
                .putAll(flags)
                .build();
        jcloudsLocation = mgmt.getLocationRegistry().resolve(LOCATION_SPEC, allFlags);

        doTest(jcloudsLocation);
    }
    
    protected abstract void doTest(Location loc) throws Exception;
    
    protected void assertExecSsh(SoftwareProcess entity, List<String> commands) {
        SshMachineLocation machine = Machines.findUniqueMachineLocation(entity.getLocations(), SshMachineLocation.class).get();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        int result = machine.execScript(ImmutableMap.of("out", outStream, "err", errStream), "url-reachable", commands);
        String out = new String(outStream.toByteArray());
        String err = new String(errStream.toByteArray());
        if (result == 0) {
            LOG.debug("Successfully executed: cmds="+commands+"; stderr="+err+"; out="+out);
        } else {
            fail("Failed to execute: result="+result+"; cmds="+commands+"; stderr="+err+"; out="+out);
        }
    }
    
    protected void assertViaSshLocalPortListeningEventually(final SoftwareProcess server, final int port) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", Duration.FIVE_MINUTES), new Runnable() {
            public void run() {
                assertExecSsh(server, ImmutableList.of("netstat -antp", "netstat -antp | grep LISTEN | grep "+port));
            }});
    }
    
    protected void assertViaSshLocalUrlListeningEventually(final SoftwareProcess server, final String url) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", Duration.FIVE_MINUTES), new Runnable() {
            public void run() {
                assertExecSsh(server, ImmutableList.of(BashCommands.installPackage("curl"), "netstat -antp", "curl -k --retry 3 "+url));
            }});
    }
}
