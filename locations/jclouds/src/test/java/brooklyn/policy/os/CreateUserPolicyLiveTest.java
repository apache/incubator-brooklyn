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
package brooklyn.policy.os;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.entity.TestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;

import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.basic.SshMachineLocation;

import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class CreateUserPolicyLiveTest extends BrooklynAppLiveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CreateUserPolicyLiveTest.class);

    // TODO Fails on OS X
    @Test(groups={"Integration", "WIP"})
    public void testIntegrationCreatesUser() throws Exception {
        LocalhostMachineProvisioningLocation loc = app.newLocalhostProvisioningLocation();
        runTestCreatesUser(loc);
    }
    
    @Test(groups="Live")
    @SuppressWarnings("unchecked")
    public void testLiveCreatesUser() throws Exception {
        String locSpec = "jclouds:softlayer:ams01";
        ImmutableMap<String, String> locArgs = ImmutableMap.of("imageId", "CENTOS_6_64");
        Location loc = mgmt.getLocationRegistry().resolve(locSpec, locArgs);
        runTestCreatesUser((MachineProvisioningLocation<SshMachineLocation>) loc);
    }
    
    public void runTestCreatesUser(MachineProvisioningLocation<SshMachineLocation> loc) throws Exception {
        String newUsername = Identifiers.makeRandomId(16);
        SshMachineLocation machine = loc.obtain(ImmutableMap.of());
        
        try {
            app.createAndManageChild(EntitySpec.create(TestEntity.class)
                    .policy(PolicySpec.create(CreateUserPolicy.class)
                            .configure(CreateUserPolicy.GRANT_SUDO, true)
                            .configure(CreateUserPolicy.VM_USERNAME, newUsername)));
            TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());

            app.start(ImmutableList.of(machine));
            
            String creds = EntityTestUtils.assertAttributeEventuallyNonNull(entity, CreateUserPolicy.VM_USER_CREDENTIALS);
            Pattern pattern = Pattern.compile("(.*) : (.*) @ (.*):(.*)");
            Matcher matcher = pattern.matcher(creds);
            assertTrue(matcher.matches());
            String username = matcher.group(1).trim();
            String password = matcher.group(2).trim();
            String hostname = matcher.group(3).trim();
            String port = matcher.group(4).trim();
            
            assertEquals(newUsername, username);
            assertEquals(hostname, machine.getAddress().getHostName());
            assertEquals(port, ""+machine.getPort());
    
            SshMachineLocation machine2 = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                    .configure(SshTool.PROP_USER, newUsername)
                    .configure(SshMachineLocation.PASSWORD, password)
                    .configure("address", hostname)
                    .configure(SshMachineLocation.SSH_PORT, Integer.parseInt(port)));
            
            LOG.info("Checking ssh'able for auto-generated user: machine="+machine+"; creds="+creds);
            assertTrue(machine2.isSshable(), "machine="+machine+"; creds="+creds);
            
        } catch (Exception e) {
            throw e;
        } finally {
            LOG.info("Deleting auto-generated user "+newUsername);
            machine.execScript("delete-user-"+newUsername, ImmutableList.of("sudo userdel -f "+newUsername));
            
            loc.release(machine);
        }
    }
}
