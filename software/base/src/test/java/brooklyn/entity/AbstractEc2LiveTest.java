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
package brooklyn.entity;

import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.Location;
import brooklyn.location.jclouds.JcloudsLocationConfig;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.collections.MutableMap;

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

    @Test(groups = {"Live"})
    public void test_Debian_6() throws Exception {
        // release codename "squeeze"
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-0740476e", "loginUser", "admin", "hardwareId", SMALL_HARDWARE_ID));
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
        // Image: {id=us-east-1/ami-a96b01c0, providerId=ami-a96b01c0, name=CentOS-6.3-x86_64-GA-EBS-02-85586466-5b6c-4495-b580-14f72b4bcf51-ami-bb9af1d2.1, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.3, description=aws-marketplace/CentOS-6.3-x86_64-GA-EBS-02-85586466-5b6c-4495-b580-14f72b4bcf51-ami-bb9af1d2.1, is64Bit=true}, description=CentOS-6.3-x86_64-GA-EBS-02 on EBS x86_64 20130527:1219, version=bb9af1d2.1, status=AVAILABLE[available], loginUser=root, userMetadata={owner=679593333241, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}})
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-a96b01c0", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_CentOS_5_6() throws Exception {
        // Image: {id=us-east-1/ami-49e32320, providerId=ami-49e32320, name=RightImage_CentOS_5.6_x64_v5.7.14, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=5.6, description=rightscale-us-east/RightImage_CentOS_5.6_x64_v5.7.14.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_5.6_x64_v5.7.14.manifest.xml, version=5.7.14, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-49e32320", "hardwareId", SMALL_HARDWARE_ID));
    }

    @Test(groups = {"Live"})
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        // Image: {id=us-east-1/ami-a35a33ca, providerId=ami-a35a33ca, name=RHEL-6.3_GA-x86_64-5-Hourly2, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=rhel, arch=paravirtual, version=6.0, description=309956199498/RHEL-6.3_GA-x86_64-5-Hourly2, is64Bit=true}, description=309956199498/RHEL-6.3_GA-x86_64-5-Hourly2, version=Hourly2, status=AVAILABLE[available], loginUser=root, userMetadata={owner=309956199498, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-a35a33ca", "hardwareId", SMALL_HARDWARE_ID, JcloudsLocationConfig.OPEN_IPTABLES.getName(), "true"));
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
}
