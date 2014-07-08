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
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;

import com.google.common.collect.Iterables;

public class JcloudsByonLocationResolverTest extends AbstractJcloudsTest {

    private LocationRegistry registry;

    // FIXME Expects this VM to exist; how to write this better? 
    // We should create a VM in @BeforeClass - slower but automated and will work for everyone
    private final String awsVm1User = "aled";
    private final String awsVm1InstanceId = "i-72b1b132";
    private final String awsVmIp = "54.195.164.70";
    private final String awsVmHostname = "ec2-54-195-164-70.eu-west-1.compute.amazonaws.com";

    private final String slVmInstanceId = "4107756";
    private final String slVmIp = "81.95.147.234";
    private final String slVmHostname = "amp";
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        registry = new BasicLocationRegistry(managementContext);
    }
    
    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix:(hosts=\"1.1.1.1\")");
        assertThrowsIllegalArgument("jcloudsByon"); // no hosts
        assertThrowsIllegalArgument("jcloudsByon:()"); // no hosts
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"\")"); // empty hosts
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"1.1.1.1\""); // no closing bracket
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"1.1.1.1\", name)"); // no value for name
        assertThrowsIllegalArgument("jcloudsByon:(hosts=\"1.1.1.1\", name=)"); // no value for name
    }
    
    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live","WIP"})
    public void testResolvesJcloudsByonAws() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\"eu-west-1\",user=\""+awsVm1User+"\",hosts=\""+awsVm1InstanceId+"\")";
        
        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve(spec);
        
        Set<JcloudsSshMachineLocation> machines = loc.getAllMachines();
        JcloudsSshMachineLocation machine = Iterables.getOnlyElement(machines);
        assertEquals(machine.getParent().getProvider(), "aws-ec2");
        assertEquals(machine.getAddress().getHostAddress(), awsVmIp);
        assertEquals(machine.getAddress().getHostName(), awsVmHostname);
        assertEquals(machine.getUser(), awsVm1User);
        
        assertTrue(machine.isSshable());
    }

    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live","WIP"})
    public void testResolvesNamedJcloudsByon() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\"eu-west-1\",user=\""+awsVm1User+"\",hosts=\""+awsVm1InstanceId+"\")";
        brooklynProperties.put("brooklyn.location.named.mynamed", spec);
        registry = new BasicLocationRegistry(managementContext);
        
        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve("named:mynamed");
        assertEquals(loc.obtain().getAddress(), InetAddress.getByName(awsVmHostname));
    }

    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live","WIP"})
    public void testJcloudsPropertiesPrecedence() throws Exception {
        String spec = "jcloudsByon:(provider=\"aws-ec2\",region=\"eu-west-1\",user=\""+awsVm1User+"\",hosts=\""+awsVm1InstanceId+"\")";
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

        registry = new BasicLocationRegistry(managementContext);
        Map<String, Object> conf = resolve("named:mynamed").obtain().getAllConfig(true);
        
        assertEquals(conf.get("user"), awsVm1User);
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("securityGroups"), "securityGroups-inProviderSpecificDeprecated");
        assertEquals(conf.get("loginUser"), "loginUser-inJcloudsGeneric");
        assertEquals(conf.get("imageId"), "imageId-inJcloudsGenericDeprecated");
        assertEquals(conf.get("keyPair"), "keyPair-inLocationGeneric");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inNamed");
        assertEquals(conf.get("privateKeyPassphrase"), "privateKeyPassphrase-inNamed");
    }

    // TODO Requires that a VM already exists; could create that VM first to make test more robust
    @Test(groups={"Live","WIP"})
    public void testResolvesJcloudsByonSoftlayer() throws Exception {
        checkSoftlayer("jcloudsByon:(provider=\"softlayer\",region=\"dal05\",hosts=\""+slVmInstanceId+"\")");
        checkSoftlayer("jcloudsByon:(provider=\"softlayer\",region=\"dal05\",hosts=\""+slVmHostname+"\")");
        checkSoftlayer("jcloudsByon:(provider=\"softlayer\",region=\"dal05\",hosts=\""+slVmIp+"\")");
        checkSoftlayer("jcloudsByon:(provider=\"softlayer\",hosts=\""+slVmIp+"\")");
    }
    
    private void checkSoftlayer(String spec) {
        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve(spec);
        
        Set<JcloudsSshMachineLocation> machines = loc.getAllMachines();
        JcloudsSshMachineLocation machine = Iterables.getOnlyElement(machines);
        assertEquals(machine.getParent().getProvider(), "softlayer");
        assertEquals(machine.getNode().getId(), slVmInstanceId);
        assertEquals(machine.getAddress().getHostAddress(), slVmIp);
        assertTrue(slVmHostname.equals(machine.getAddress().getHostName()) || slVmIp.equals(machine.getAddress().getHostName()), 
            "address hostname is: "+machine.getAddress().getHostName());
        assertTrue(slVmHostname.equals(machine.getNode().getHostname()) || slVmIp.equals(machine.getNode().getHostname()), 
            "node hostname is: "+machine.getNode().getHostname());
        
        // could also assert this, given a user credential, but not currently set up
//        assertTrue(machine.isSshable());
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> resolve(String spec) {
        return (FixedListMachineProvisioningLocation<JcloudsSshMachineLocation>) registry.resolve(spec);
    }
    
    private void assertThrowsNoSuchElement(String val) {
        try {
            resolve(val);
            fail();
        } catch (NoSuchElementException e) {
            // success
        }
    }
    
    private void assertThrowsIllegalArgument(String val) {
        try {
            resolve(val);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }
}
