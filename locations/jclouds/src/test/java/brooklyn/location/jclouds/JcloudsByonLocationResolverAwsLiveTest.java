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
package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class JcloudsByonLocationResolverAwsLiveTest extends AbstractJcloudsLiveTest {

    private static final String AWS_REGION = "eu-west-1";
    private static final String AWS_LOCATION_SPEC = "jclouds:aws-ec2:"+AWS_REGION;
    
    private String awsVmUser;
    private String awsVmInstanceId;
    private String awsVmIp;
    private String awsVmHostname;
     
    private LocalManagementContext classManagementContext;
    private JcloudsLocation classEc2Loc;
    private JcloudsSshMachineLocation classEc2Vm;

    @BeforeClass(groups="Live")
    public void setUpClass() throws Exception {
        classManagementContext = newManagementContext();
        classEc2Loc = (JcloudsLocation) classManagementContext.getLocationRegistry().resolve(AWS_LOCATION_SPEC);
        classEc2Vm = classEc2Loc.obtain(MutableMap.<String,Object>builder()
                .put("hardwareId", AWS_EC2_SMALL_HARDWARE_ID)
                .put("inboundPorts", ImmutableList.of(22))
                .build());
        awsVmUser = classEc2Vm.getUser();
        awsVmInstanceId = classEc2Vm.getNode().getProviderId(); // id without region (e.g. "i-6ff96d2f" instead of "eu-west-1/i-6ff96d2f")
        awsVmIp = classEc2Vm.getAddress().getHostAddress();
        awsVmHostname = classEc2Vm.getAddress().getHostName();
    }
    
    @AfterClass(alwaysRun=true)
    public void tearDownClass() throws Exception {
        try {
            if (classEc2Vm != null) {
                classEc2Loc.release(classEc2Vm);
            }
        } finally {
            if (classManagementContext != null) classManagementContext.terminate();
        }
    }

    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live"})
    public void testResolvesJcloudsByonAws() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\""+AWS_REGION+"\",user=\""+awsVmUser+"\",hosts=\""+awsVmInstanceId+"\",anotherprop=myval)";

        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve(spec);
        
        Set<JcloudsSshMachineLocation> machines = loc.getAllMachines();
        JcloudsSshMachineLocation machine = Iterables.getOnlyElement(machines);
        assertEquals(machine.getParent().getProvider(), "aws-ec2");
        assertEquals(machine.getAddress().getHostAddress(), awsVmIp);
        assertEquals(machine.getAddress().getHostName(), awsVmHostname);
        assertEquals(machine.getUser(), awsVmUser);
        assertEquals(machine.getAllConfig(true).get("anotherprop"), "myval");
        
        assertTrue(machine.isSshable());
    }

    @Test(groups={"Live"})
    public void testResolvesNamedJcloudsByon() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\""+AWS_REGION+"\",user=\""+awsVmUser+"\",hosts=\""+awsVmInstanceId+"\")";
        brooklynProperties.put("brooklyn.location.named.mynamed", spec);
        
        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve("named:mynamed");
        assertEquals(loc.obtain().getAddress(), InetAddress.getByName(awsVmHostname));
    }

    @Test(groups={"Live"})
    public void testJcloudsPropertiesPrecedence() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\""+AWS_REGION+"\",user=\""+awsVmUser+"\",hosts=\""+awsVmInstanceId+"\")";
        brooklynProperties.put("brooklyn.location.named.mynamed", spec);
        
        // prefer those in spec string over everything else
        brooklynProperties.put("brooklyn.location.named.mynamed.user", "user-inNamed");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.user", "user-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.user", "user-inProviderSpecificDeprecated");
        brooklynProperties.put("brooklyn.location.jclouds.user", "user-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.user", "user-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.user", "user-inLocationGeneric");

        // prefer those in "named" over everything else (except spec string itself)
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.privateKeyFile", "privateKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyFile", "privateKeyFile-inProviderSpecificDeprecated");
        brooklynProperties.put("brooklyn.location.jclouds.privateKeyFile", "privateKeyFile-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.privateKeyFile", "privateKeyFile-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.privateKeyFile", "privateKeyFile-inLocationGeneric");

        // prefer those in provider-specific over generic
        brooklynProperties.put("brooklyn.location.jclouds.aws-ec2.publicKeyFile", "publicKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.publicKeyFile", "publicKeyFile-inProviderSpecificDeprecated");
        brooklynProperties.put("brooklyn.location.jclouds.publicKeyFile", "publicKeyFile-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.publicKeyFile", "publicKeyFile-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.publicKeyFile", "publicKeyFile-inLocationGeneric");
        
        // prefer those in provider-specific (deprecated scope) over generic
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.securityGroups", "securityGroups-inProviderSpecificDeprecated");
        brooklynProperties.put("brooklyn.location.jclouds.securityGroups", "securityGroups-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.securityGroups", "securityGroups-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.securityGroups", "securityGroups-inLocationGeneric");

        // prefer those in jclouds-generic over location-generic
        brooklynProperties.put("brooklyn.location.jclouds.loginUser", "loginUser-inJcloudsGeneric");
        brooklynProperties.put("brooklyn.jclouds.loginUser", "loginUser-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.loginUser", "loginUser-inLocationGeneric");

        // prefer those in jclouds-generic (deprecated) over location-generic
        brooklynProperties.put("brooklyn.jclouds.imageId", "imageId-inJcloudsGenericDeprecated");
        brooklynProperties.put("brooklyn.location.imageId", "imageId-inLocationGeneric");

        // prefer location-generic if nothing else
        brooklynProperties.put("brooklyn.location.keyPair", "keyPair-inLocationGeneric");

        // prefer deprecated properties in "named" over those less specific
        brooklynProperties.put("brooklyn.location.named.mynamed.private-key-data", "privateKeyData-inNamed");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.privateKeyData", "privateKeyData-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.privateKeyData", "privateKeyData-inJcloudsGeneric");

        // prefer "named" over everything else: confirm deprecated don't get transformed to overwrite it accidentally
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyPassphrase", "privateKeyPassphrase-inNamed");
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.private-key-passphrase", "privateKeyPassphrase-inProviderSpecific");
        brooklynProperties.put("brooklyn.jclouds.private-key-passphrase", "privateKeyPassphrase-inJcloudsGeneric");

        Map<String, Object> conf = resolve("named:mynamed").obtain().getAllConfig(true);
        
        assertEquals(conf.get("user"), awsVmUser);
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("securityGroups"), "securityGroups-inProviderSpecificDeprecated");
        assertEquals(conf.get("loginUser"), "loginUser-inJcloudsGeneric");
        assertEquals(conf.get("imageId"), "imageId-inJcloudsGenericDeprecated");
        assertEquals(conf.get("keyPair"), "keyPair-inLocationGeneric");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inNamed");
        assertEquals(conf.get("privateKeyPassphrase"), "privateKeyPassphrase-inNamed");
    }
    
    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> resolve(String spec) {
        return (FixedListMachineProvisioningLocation<JcloudsSshMachineLocation>) managementContext.getLocationRegistry().resolve(spec);
    }
}
