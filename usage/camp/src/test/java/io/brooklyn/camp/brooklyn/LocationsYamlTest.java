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
package io.brooklyn.camp.brooklyn;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.StringReader;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.MultiLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class LocationsYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(LocationsYamlTest.class);

    @Test
    public void testLocationString() throws Exception {
        String yaml = 
                "location: localhost\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(app.getLocations());
        assertNotNull(loc);
    }

    @Test
    public void testLocationComplexString() throws Exception {
        String yaml = 
                "location: localhost:(name=myname)\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(app.getLocations());
        assertEquals(loc.getDisplayName(), "myname");
    }

    @Test
    public void testLocationSplitLineWithNoConfig() throws Exception {
        String yaml = 
                "location:\n"+
                "  localhost\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(app.getLocations());
        assertNotNull(loc);
    }

    @Test
    public void testMultiLocations() throws Exception {
        String yaml = 
                "locations:\n"+
                "- localhost:(name=loc1)\n"+
                "- localhost:(name=loc2)\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        List<Location> locs = ImmutableList.copyOf(app.getLocations());
        assertEquals(locs.size(), 2, "locs="+locs);
        LocalhostMachineProvisioningLocation loc1 = (LocalhostMachineProvisioningLocation) locs.get(0);
        LocalhostMachineProvisioningLocation loc2 = (LocalhostMachineProvisioningLocation) locs.get(1);
        assertEquals(loc1.getDisplayName(), "loc1");
        assertEquals(loc2.getDisplayName(), "loc2");
    }

    @Test
    public void testLocationConfig() throws Exception {
        String yaml = 
                "location:\n"+
                "  localhost:\n"+
                "    displayName: myname\n"+
                "    myconfkey: myconfval\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(app.getLocations());
        assertEquals(loc.getDisplayName(), "myname");
        assertEquals(loc.getAllConfig(false).get("myconfkey"), "myconfval");
    }

    @Test
    public void testMultiLocationConfig() throws Exception {
        String yaml = 
                "locations:\n"+
                "- localhost:\n"+
                "    displayName: myname1\n"+
                "    myconfkey: myconfval1\n"+
                "- localhost:\n"+
                "    displayName: myname2\n"+
                "    myconfkey: myconfval2\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        List<Location> locs = ImmutableList.copyOf(app.getLocations());
        assertEquals(locs.size(), 2, "locs="+locs);
        LocalhostMachineProvisioningLocation loc1 = (LocalhostMachineProvisioningLocation) locs.get(0);
        LocalhostMachineProvisioningLocation loc2 = (LocalhostMachineProvisioningLocation) locs.get(1);
        assertEquals(loc1.getDisplayName(), "myname1");
        assertEquals(loc1.getAllConfig(false).get("myconfkey"), "myconfval1");
        assertEquals(loc2.getDisplayName(), "myname2");
        assertEquals(loc2.getAllConfig(false).get("myconfkey"), "myconfval2");
    }

    // TODO Fails because PlanInterpretationContext constructor throws NPE on location's value (using ImmutableMap).
    @Test(groups="WIP")
    public void testLocationBlank() throws Exception {
        String yaml = 
                "location: \n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        assertTrue(app.getLocations().isEmpty(), "locs="+app.getLocations());
    }

    @Test
    public void testInvalidLocationAndLocations() throws Exception {
        String yaml = 
                "location: localhost\n"+
                "locations:\n"+
                "- localhost\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        try {
            createStartWaitAndLogApplication(new StringReader(yaml));
        } catch (IllegalStateException e) {
            if (!e.toString().contains("Conflicting 'location' and 'locations'")) throw e;
        }
    }

    @Test
    public void testInvalidLocationList() throws Exception {
        // should have used "locations:" instead of "location:"
        String yaml = 
                "location:\n"+
                "- localhost\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        try {
            createStartWaitAndLogApplication(new StringReader(yaml));
        } catch (IllegalStateException e) {
            if (!e.toString().contains("must be a string or map")) throw e;
        }
    }
    
    @Test
    public void testRootLocationPassedToChild() throws Exception {
        String yaml = 
                "locations:\n"+
                "- localhost:(name=loc1)\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        Entity child = Iterables.getOnlyElement(app.getChildren());
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(child.getLocations());
        assertEquals(loc.getDisplayName(), "loc1");
    }

    @Test
    public void testByonYamlHosts() throws Exception {
        String yaml = 
                "locations:\n"+
                "- byon:\n"+
                "    user: root\n"+
                "    privateKeyFile: /tmp/key_file\n"+
                "    hosts: \n"+
                "    - 127.0.0.1\n"+
                "    - brooklyn@127.0.0.2\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        Entity child = Iterables.getOnlyElement(app.getChildren());
        FixedListMachineProvisioningLocation<?> loc = (FixedListMachineProvisioningLocation<?>) Iterables.getOnlyElement(child.getLocations());
        Assert.assertEquals(loc.getChildren().size(), 2);
        
        SshMachineLocation l1 = (SshMachineLocation)loc.obtain();
        assertUserAddress(l1, "root", "127.0.0.1");
        assertUserAddress((SshMachineLocation)loc.obtain(), "brooklyn", "127.0.0.2");
        Assert.assertEquals(l1.getConfig(SshMachineLocation.PRIVATE_KEY_FILE), "/tmp/key_file");
    }

    @Test
    public void testByonYamlHostsString() throws Exception {
        String yaml = 
                "locations:\n"+
                "- byon:\n"+
                "    user: root\n"+
                "    hosts: \"{127.0.{0,127}.{1-2},brooklyn@127.0.0.127}\"\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        Entity child = Iterables.getOnlyElement(app.getChildren());
        FixedListMachineProvisioningLocation<?> loc = (FixedListMachineProvisioningLocation<?>) Iterables.getOnlyElement(child.getLocations());
        Assert.assertEquals(loc.getChildren().size(), 5);
        
        assertUserAddress((SshMachineLocation)loc.obtain(), "root", "127.0.0.1");
        assertUserAddress((SshMachineLocation)loc.obtain(), "root", "127.0.0.2");
        assertUserAddress((SshMachineLocation)loc.obtain(), "root", "127.0.127.1");
        assertUserAddress((SshMachineLocation)loc.obtain(), "root", "127.0.127.2");
        assertUserAddress((SshMachineLocation)loc.obtain(), "brooklyn", "127.0.0.127");
    }

    @Test
    public void testMultiByonYaml() throws Exception {
        String yaml = 
                "locations:\n"+
                "- multi:\n"+
                "   targets:\n"+
                "   - byon:\n"+
                "      user: root\n"+
                "      hosts: 127.0.{0,127}.{1-2}\n"+
                "   - byon:\n"+
                "      user: brooklyn\n"+
                "      hosts:\n"+
                "      - 127.0.0.127\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        Entity child = Iterables.getOnlyElement(app.getChildren());
        MultiLocation<?> loc = (MultiLocation<?>) Iterables.getOnlyElement(child.getLocations());
        Assert.assertEquals(loc.getSubLocations().size(), 2);
        
        assertUserAddress((SshMachineLocation)loc.obtain(), "root", "127.0.0.1");
        assertUserAddress((SshMachineLocation)loc.obtain(), "root", "127.0.0.2");
        assertUserAddress((SshMachineLocation)loc.obtain(), "root", "127.0.127.1");
        assertUserAddress((SshMachineLocation)loc.obtain(), "root", "127.0.127.2");
        assertUserAddress((SshMachineLocation)loc.obtain(), "brooklyn", "127.0.0.127");
    }

    public static void assertUserAddress(MachineLocation l, String user, String address) {
        Assert.assertEquals(l.getAddress().getHostAddress(), address);
        if (!Strings.isBlank(user)) Assert.assertEquals(((SshMachineLocation)l).getUser(), user);        
    }
    
    @Override
    protected Logger getLogger() {
        return log;
    }
    
}
