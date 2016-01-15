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
import static org.testng.Assert.assertNull;

import java.io.File;
import java.util.Map;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Tests different login options for ssh keys, passwords, etc.
 */
public class JcloudsLoginLiveTest extends AbstractJcloudsLiveTest {

    // TODO Rackspace failed - possibly image no longer exists?
    // Was using: 
    //     Image: {id=LON/29fe3e2b-f119-4715-927b-763e99ebe23e, providerId=29fe3e2b-f119-4715-927b-763e99ebe23e, name=Debian 6.06 (Squeeze), location={scope=ZONE, id=LON, description=LON, parent=rackspace-cloudservers-uk, iso3166Codes=[GB-SLG]}, os={family=debian, name=Debian 6.06 (Squeeze), version=6.0, description=Debian 6.06 (Squeeze), is64Bit=true}, description=Debian 6.06 (Squeeze), status=AVAILABLE, loginUser=root, userMetadata={os_distro=debian, com.rackspace__1__visible_core=1, com.rackspace__1__build_rackconnect=1, com.rackspace__1__options=0, image_type=base, cache_in_nova=True, com.rackspace__1__source=kickstart, org.openstack__1__os_distro=org.debian, com.rackspace__1__release_build_date=2013-08-06_13-05-36, auto_disk_config=True, com.rackspace__1__release_version=4, os_type=linux, com.rackspace__1__visible_rackconnect=1, com.rackspace__1__release_id=300, com.rackspace__1__visible_managed=0, com.rackspace__1__build_core=1, org.openstack__1__os_version=6.06, org.openstack__1__architecture=x64, com.rackspace__1__build_managed=0}}
    //     public static final String RACKSPACE_DEBIAN_IMAGE_NAME_REGEX = "Debian 6";

    // TODO GCE (in GCE_USCENTRAL_REGION_NAME) fails. We get blocked by the VM! e.g. /var/log/auth.log shows:
    //     Nov  3 14:57:56 ubuntu sshd[1693]: Did not receive identification string from 31.53.199.228
    //     Nov  3 14:57:56 ubuntu sshguard[971]: Blocking 31.53.199.228:4 for >630secs: 40 danger in 4 attacks over 435 seconds (all: 40d in 1 abuses over 435s).

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsLoginLiveTest.class);

    public static final String AWS_EC2_REGION_NAME = AWS_EC2_USEAST_REGION_NAME;
    public static final String AWS_EC2_LOCATION_SPEC = "jclouds:" + AWS_EC2_PROVIDER + (AWS_EC2_REGION_NAME == null ? "" : ":" + AWS_EC2_REGION_NAME);
    
    // Image: {id=us-east-1/ami-7d7bfc14, providerId=ami-7d7bfc14, name=RightImage_CentOS_6.3_x64_v5.8.8.5, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.0, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, version=5.8.8.5, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String AWS_EC2_CENTOS_IMAGE_ID = "us-east-1/ami-7d7bfc14";

    // Image: {id=us-east-1/ami-d0f89fb9, providerId=ami-d0f89fb9, name=ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=12.04, description=099720109477/ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, is64Bit=true}, description=099720109477/ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, version=20130411.1, status=AVAILABLE[available], loginUser=ubuntu, userMetadata={owner=099720109477, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String AWS_EC2_UBUNTU_IMAGE_ID = "us-east-1/ami-d0f89fb9";
    
    // Image: {id=us-east-1/ami-5e008437, providerId=ami-5e008437, name=RightImage_Ubuntu_10.04_x64_v5.8.8.3, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=10.04, description=rightscale-us-east/RightImage_Ubuntu_10.04_x64_v5.8.8.3.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_Ubuntu_10.04_x64_v5.8.8.3.manifest.xml, version=5.8.8.3, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
    // Uses "root" as loginUser
    public static final String AWS_EC2_UBUNTU_10_IMAGE_ID = "us-east-1/ami-5e008437";

    protected JcloudsSshMachineLocation machine;
    
    private File privateRsaFile = new File(Os.tidyPath("~/.ssh/id_rsa"));
    private File privateDsaFile = new File(Os.tidyPath("~/.ssh/id_dsa"));
    private File privateRsaFileTmp = new File(privateRsaFile.getAbsoluteFile()+".tmp");
    private File privateDsaFileTmp = new File(privateDsaFile.getAbsoluteFile()+".tmp");
    private File publicRsaFile = new File(Os.tidyPath("~/.ssh/id_rsa.pub"));
    private File publicDsaFile = new File(Os.tidyPath("~/.ssh/id_dsa.pub"));
    private File publicRsaFileTmp = new File(publicRsaFile.getAbsoluteFile()+".tmp");
    private File publicDsaFileTmp = new File(publicDsaFile.getAbsoluteFile()+".tmp");
    private boolean privateRsaFileMoved;
    private boolean privateDsaFileMoved;
    private boolean publicRsaFileMoved;
    private boolean publicDsaFileMoved;

    @Test(groups = {"Live"})
    @SuppressWarnings("deprecation")
    protected void testAwsEc2SpecifyingJustPrivateSshKeyInDeprecatedForm() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.LEGACY_PRIVATE_KEY_FILE.getName(), "~/.ssh/id_rsa");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine();
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
    }
    
    @Test(groups = {"Live"})
    @SuppressWarnings("deprecation")
    protected void testAwsEc2SpecifyingPrivateAndPublicSshKeyInDeprecatedForm() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.LEGACY_PRIVATE_KEY_FILE.getName(), "~/.ssh/id_rsa");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.LEGACY_PUBLIC_KEY_FILE.getName(), "~/.ssh/id_rsa.pub");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine();
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
    }

    // Uses default key files
    @Test(groups = {"Live"})
    protected void testAwsEc2SpecifyingNoKeyFiles() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine();
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
    }
    
    @Test(groups = {"Live"})
    public void testSpecifyingPasswordAndNoDefaultKeyFilesExist() throws Exception {
        try {
            moveSshKeyFiles();
            
            brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
            brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PASSWORD.getName(), "mypassword");
            jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
            
            machine = createEc2Machine();
            assertSshable(machine);
            
            assertSshable(ImmutableMap.builder()
                    .put("address", machine.getAddress())
                    .put("user", "myname")
                    .put(SshMachineLocation.PASSWORD, "mypassword")
                    .build());
        } finally {
            restoreSshKeyFiles();
        }
    }

    // Generates and uses a random password
    @Test(groups = {"Live"})
    protected void testSpecifyingNothingAndNoDefaultKeyFilesExist() throws Exception {
        try {
            moveSshKeyFiles();
            
            brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
            jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
            
            machine = createEc2Machine();
            assertSshable(machine);
            assertEquals(machine.getUser(), "myname");
        } finally {
            restoreSshKeyFiles();
        }
    }

    @Test(groups = {"Live"})
    public void testSpecifyingPasswordAndSshKeysPrefersKeysAndDisablesPassword() throws Exception {
        runSpecifyingPasswordAndSshKeysPrefersKeys(false);
    }

    @Test(groups = {"Live"})
    public void testSpecifyingPasswordAndSshKeysPrefersKeysAndAllowsPassword() throws Exception {
        runSpecifyingPasswordAndSshKeysPrefersKeys(true);
    }

    protected void runSpecifyingPasswordAndSshKeysPrefersKeys(boolean leavePasswordSsh) throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PRIVATE_KEY_FILE.getName(), "~/.ssh/id_rsa");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PUBLIC_KEY_FILE.getName(), "~/.ssh/id_rsa.pub");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PASSWORD.getName(), "mypassword");
        if (leavePasswordSsh) {
            brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.DISABLE_ROOT_AND_PASSWORD_SSH.getName(), false);
        }
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine();
        assertSshable(machine);
        
        assertNull(machine.config().get(SshMachineLocation.PASSWORD));
        assertNotNull(machine.config().get(SshMachineLocation.PRIVATE_KEY_DATA));
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());

        if (leavePasswordSsh) {
            assertSshable(ImmutableMap.builder()
                    .put("address", machine.getAddress())
                    .put("user", "myname")
                    .put(SshMachineLocation.PASSWORD, "mypassword")
                    .build());
        } else {
            assertNotSshable(ImmutableMap.builder()
                    .put("address", machine.getAddress())
                    .put("user", "myname")
                    .put(SshMachineLocation.PASSWORD, "mypassword")
                    .build());
        }
    }

    @Test(groups = {"Live"})
    protected void testSpecifyingPasswordIgnoresDefaultSshKeys() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PASSWORD.getName(), "mypassword");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine();
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PASSWORD, "mypassword")
                .build());
        
        assertNotSshable(ImmutableMap.builder()
            .put("address", machine.getAddress())
            .put("user", "myname")
            .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
            .build());
    }

    @Test(groups = {"Live"})
    protected void testSpecifyingPasswordIgnoresDefaultSshKeysSkippingJcloudsInit() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PASSWORD.getName(), "mypassword");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USE_JCLOUDS_SSH_INIT.getName(), "false");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine();
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PASSWORD, "mypassword")
                .build());
        
        assertNotSshable(ImmutableMap.builder()
            .put("address", machine.getAddress())
            .put("user", "myname")
            .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
            .build());
    }

    @Test(groups = {"Live"})
    protected void testSpecifyingPasswordWithPublicKeyAllowsKeyAccess() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "myname");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PASSWORD.getName(), "mypassword");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PUBLIC_KEY_FILE.getName(), "~/.ssh/id_rsa.pub");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.DISABLE_ROOT_AND_PASSWORD_SSH.getName(), false);
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine();
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "myname")
                .put(SshMachineLocation.PASSWORD, "mypassword")
                .build());
    }

    // user "root" matches the loginUser=root
    @Test(groups = {"Live"})
    protected void testSpecifyingPasswordWhenNoDefaultKeyFilesExistWithRootUser() throws Exception {
        try {
            moveSshKeyFiles();
            
            brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "root");
            brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PASSWORD.getName(), "mypassword");
            brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.DISABLE_ROOT_AND_PASSWORD_SSH.getName(), false);
            jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
            
            machine = createEc2Machine();
            assertSshable(machine);
            
            assertSshable(ImmutableMap.builder()
                    .put("address", machine.getAddress())
                    .put("user", "root")
                    .put(SshMachineLocation.PASSWORD, "mypassword")
                    .build());
        } finally {
            restoreSshKeyFiles();
        }
    }

    @Test(groups = {"Live"})
    protected void testAwsEc2SpecifyingRootUser() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "root");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PRIVATE_KEY_FILE.getName(), "~/.ssh/id_rsa");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PUBLIC_KEY_FILE.getName(), "~/.ssh/id_rsa.pub");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine(ImmutableMap.<String,Object>of("imageId", AWS_EC2_UBUNTU_10_IMAGE_ID));
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "root")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
    }
    
    @Test(groups = {"Live"})
    protected void testAwsEc2WhenBlankUserSoUsesRootLoginUser() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PRIVATE_KEY_FILE.getName(), "~/.ssh/id_rsa");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PUBLIC_KEY_FILE.getName(), "~/.ssh/id_rsa.pub");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine(ImmutableMap.<String,Object>of("imageId", AWS_EC2_UBUNTU_10_IMAGE_ID));
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "root")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
    }
    
    // In JcloudsLocation.NON_ADDABLE_USERS, "ec2-user" was treated special and was not added!
    // That was very bad for if someone is running brooklyn on a new AWS VM, and just installs brooklyn+runs as the default ec2-user.
    @Test(groups = {"Live"})
    protected void testAwsEc2SpecifyingSpecialUser() throws Exception {
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.USER.getName(), "ec2-user");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PRIVATE_KEY_FILE.getName(), "~/.ssh/id_rsa");
        brooklynProperties.put(BROOKLYN_PROPERTIES_PREFIX+JcloudsLocationConfig.PUBLIC_KEY_FILE.getName(), "~/.ssh/id_rsa.pub");
        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        
        machine = createEc2Machine(ImmutableMap.<String,Object>of("imageId", AWS_EC2_UBUNTU_10_IMAGE_ID));
        assertSshable(machine);
        
        assertSshable(ImmutableMap.builder()
                .put("address", machine.getAddress())
                .put("user", "ec2-user")
                .put(SshMachineLocation.PRIVATE_KEY_FILE, Os.tidyPath("~/.ssh/id_rsa"))
                .build());
    }
    
    @Override
    protected void releaseMachine(JcloudsSshMachineLocation machine) {
        jcloudsLocation.release(machine);
    }

    private JcloudsSshMachineLocation createEc2Machine() throws Exception {
        return createEc2Machine(ImmutableMap.<String, Object>of());
    }
    
    private JcloudsSshMachineLocation createEc2Machine(Map<String,? extends Object> conf) throws Exception {
        return obtainMachine(MutableMap.<String,Object>builder()
                .putAll(conf)
                .putIfAbsent("imageId", AWS_EC2_CENTOS_IMAGE_ID)
                .putIfAbsent("hardwareId", AWS_EC2_MEDIUM_HARDWARE_ID)
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
    
    protected void assertNotSshable(Map<?,?> machineConfig) {
        try {
            assertSshable(machineConfig);
            Assert.fail("ssh should not have succeeded "+machineConfig);
        } catch (Exception e) {
            // expected
            LOG.debug("Exception as expected when testing sshable "+machineConfig);
        }
    }
    
    private void moveSshKeyFiles() throws Exception {
        privateRsaFileMoved = false;
        privateDsaFileMoved = false;
        publicRsaFileMoved = false;
        publicDsaFileMoved = false;

        if (privateRsaFile.exists()) {
            LOG.info("Moving {} to {}", privateRsaFile, privateRsaFileTmp);
            Runtime.getRuntime().exec("mv "+privateRsaFile.getAbsolutePath()+" "+privateRsaFileTmp.getAbsolutePath());
            privateRsaFileMoved = true;
        }
        if (privateDsaFile.exists()) {
            LOG.info("Moving {} to {}", privateDsaFile, privateDsaFileTmp);
            Runtime.getRuntime().exec("mv "+privateDsaFile.getAbsolutePath()+" "+privateDsaFileTmp.getAbsolutePath());
            privateDsaFileMoved = true;
        }
        if (publicRsaFile.exists()) {
            LOG.info("Moving {} to {}", publicRsaFile, publicRsaFileTmp);
            Runtime.getRuntime().exec("mv "+publicRsaFile.getAbsolutePath()+" "+publicRsaFileTmp.getAbsolutePath());
            publicRsaFileMoved = true;
        }
        if (publicDsaFile.exists()) {
            LOG.info("Moving {} to {}", publicDsaFile, publicDsaFileTmp);
            Runtime.getRuntime().exec("mv "+publicDsaFile.getAbsolutePath()+" "+publicDsaFileTmp.getAbsolutePath());
            publicDsaFileMoved = true;
        }
    }
    
    private void restoreSshKeyFiles() throws Exception {
        if (privateRsaFileMoved) {
            LOG.info("Restoring {} form {}", privateRsaFile, privateRsaFileTmp);
            Runtime.getRuntime().exec("mv "+privateRsaFileTmp.getAbsolutePath()+" "+privateRsaFile.getAbsolutePath());
            privateRsaFileMoved = false;
        }
        if (privateDsaFileMoved) {
            LOG.info("Restoring {} form {}", privateDsaFile, privateDsaFileTmp);
            Runtime.getRuntime().exec("mv "+privateDsaFileTmp.getAbsolutePath()+" "+privateDsaFile.getAbsolutePath());
            privateDsaFileMoved = false;
        }
        if (publicRsaFileMoved) {
            LOG.info("Restoring {} form {}", publicRsaFile, publicRsaFileTmp);
            Runtime.getRuntime().exec("mv "+publicRsaFileTmp.getAbsolutePath()+" "+publicRsaFile.getAbsolutePath());
            publicRsaFileMoved = false;
        }
        if (publicDsaFileMoved) {
            LOG.info("Restoring {} form {}", publicDsaFile, publicDsaFileTmp);
            Runtime.getRuntime().exec("mv "+publicDsaFileTmp.getAbsolutePath()+" "+publicDsaFile.getAbsolutePath());
            publicDsaFileMoved = false;
        }
    }
}
