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

import java.io.StringReader;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.LocationPredicates;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.WinRmMachineLocation;
import brooklyn.util.net.UserAndHostAndPort;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class
        ByonLocationsYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(ByonLocationsYamlTest.class);

    @Test
    @SuppressWarnings("unchecked")
    public void testByonSpec() throws Exception {
        String yaml = Joiner.on("\n").join(
                "location: byon(user=myuser,mykey=myval,hosts=\"1.1.1.1\")",
                "services:",
                "- serviceType: brooklyn.entity.basic.BasicApplication");
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = (FixedListMachineProvisioningLocation<SshMachineLocation>) Iterables.get(app.getLocations(), 0);
        
        Set<SshMachineLocation> machines = loc.getAvailable();
        SshMachineLocation machine = Iterables.getOnlyElement(machines);
        assertMachine(machine, UserAndHostAndPort.fromParts("myuser", "1.1.1.1",  22), ImmutableMap.of("mykey", "myval"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testByonMachine() throws Exception {
        String yaml = Joiner.on("\n").join(
                "location:",
                "  byon:",
                "    hosts:",
                "    - ssh: 1.1.1.1:8022",
                "      privateAddresses: [10.0.0.1]",
                "      password: mypassword",
                "      user: myuser",
                "      mykey: myval",
                "services:",
                "- serviceType: brooklyn.entity.basic.BasicApplication");
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = (FixedListMachineProvisioningLocation<SshMachineLocation>) Iterables.get(app.getLocations(), 0);
        
        Set<SshMachineLocation> machines = loc.getAvailable();
        SshMachineLocation machine = Iterables.getOnlyElement(machines);
        assertMachine(machine, UserAndHostAndPort.fromParts("myuser", "1.1.1.1",  8022), ImmutableMap.of(
                SshMachineLocation.PASSWORD.getName(), "mypassword",
                "mykey", "myval"));
        assertEquals(machine.getPrivateAddresses(), ImmutableSet.of("10.0.0.1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testByonWindowsMachine() throws Exception {
        String yaml = Joiner.on("\n").join(
                "location:",
                "  byon:",
                "    hosts:",
                "    - winrm: 1.1.1.1:8985",
                "      privateAddresses: [10.0.0.1]",
                "      password: mypassword",
                "      user: myuser",
                "      mykey: myval",
                "      osfamily: windows",
                "services:",
                "- serviceType: brooklyn.entity.basic.BasicApplication");
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        FixedListMachineProvisioningLocation<WinRmMachineLocation> loc = (FixedListMachineProvisioningLocation<WinRmMachineLocation>) Iterables.get(app.getLocations(), 0);
        
        Set<WinRmMachineLocation> machines = loc.getAvailable();
        WinRmMachineLocation machine = Iterables.getOnlyElement(machines);
        assertMachine(machine, UserAndHostAndPort.fromParts("myuser", "1.1.1.1",  8985), ImmutableMap.of(
                SshMachineLocation.PASSWORD.getName(), "mypassword",
                "mykey", "myval"));
        assertEquals(machine.getPrivateAddresses(), ImmutableSet.of("10.0.0.1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testByonMultiMachine() throws Exception {
        String yaml = Joiner.on("\n").join(
                "location:",
                "  byon:",
                "    hosts:",
                "    - ssh: 1.1.1.1:8022",
                "      privateAddresses: [10.0.0.1]",
                "      password: mypassword",
                "      user: myuser",
                "      mykey: myval1",
                "    - ssh: 1.1.1.2:8022",
                "      privateAddresses: [10.0.0.2]",
                "      password: mypassword",
                "      user: myuser",
                "      mykey: myval2",
                "    - winrm: 1.1.1.3:8985",
                "      privateAddresses: [10.0.0.3]",
                "      password: mypassword",
                "      user: myuser",
                "      mykey: myval3",
                "      osfamily: windows",
                "services:",
                "- serviceType: brooklyn.entity.basic.BasicApplication");
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        FixedListMachineProvisioningLocation<MachineLocation> loc = (FixedListMachineProvisioningLocation<MachineLocation>) Iterables.get(app.getLocations(), 0);
        
        Set<MachineLocation> machines = loc.getAvailable();
        assertEquals(machines.size(), 3, "machines="+machines);
        SshMachineLocation machine1 = (SshMachineLocation) Iterables.find(machines, LocationPredicates.configEqualTo(ConfigKeys.newStringConfigKey("mykey"), "myval1"));
        SshMachineLocation machine2 = (SshMachineLocation) Iterables.find(machines, LocationPredicates.configEqualTo(ConfigKeys.newStringConfigKey("mykey"), "myval2"));
        WinRmMachineLocation machine3 = (WinRmMachineLocation) Iterables.find(machines, Predicates.instanceOf(WinRmMachineLocation.class));

        assertMachine(machine1, UserAndHostAndPort.fromParts("myuser", "1.1.1.1",  8022), ImmutableMap.of(
                SshMachineLocation.PASSWORD.getName(), "mypassword",
                "mykey", "myval1"));
        assertEquals(machine1.getPrivateAddresses(), ImmutableSet.of("10.0.0.1"));

        assertMachine(machine2, UserAndHostAndPort.fromParts("myuser", "1.1.1.2",  8022), ImmutableMap.of(
                SshMachineLocation.PASSWORD.getName(), "mypassword",
                "mykey", "myval2"));
        assertEquals(machine2.getPrivateAddresses(), ImmutableSet.of("10.0.0.2"));

        assertMachine(machine3, UserAndHostAndPort.fromParts("myuser", "1.1.1.3",  8985), ImmutableMap.of(
                SshMachineLocation.PASSWORD.getName(), "mypassword",
                "mykey", "myval3"));
        assertEquals(machine3.getPrivateAddresses(), ImmutableSet.of("10.0.0.3"));
    }
    
    private void assertMachine(SshMachineLocation machine, UserAndHostAndPort conn, Map<String, ?> config) {
        assertEquals(machine.getAddress().getHostAddress(), conn.getHostAndPort().getHostText());
        assertEquals(machine.getPort(), conn.getHostAndPort().getPort());
        assertEquals(machine.getUser(), conn.getUser());
        for (Map.Entry<String, ?> entry : config.entrySet()) {
            Object actualVal = machine.getConfig(ConfigKeys.newConfigKey(Object.class, entry.getKey()));
            assertEquals(actualVal, entry.getValue());
        }
    }
    
    private void assertMachine(WinRmMachineLocation machine, UserAndHostAndPort conn, Map<String, ?> config) {
        assertEquals(machine.getAddress().getHostAddress(), conn.getHostAndPort().getHostText());
        assertEquals(machine.getConfig(WinRmMachineLocation.WINRM_PORT), (Integer) conn.getHostAndPort().getPort());
        assertEquals(machine.getUser(), conn.getUser());
        for (Map.Entry<String, ?> entry : config.entrySet()) {
            Object actualVal = machine.getConfig(ConfigKeys.newConfigKey(Object.class, entry.getKey()));
            assertEquals(actualVal, entry.getValue());
        }
    }
    
    @Override
    protected Logger getLogger() {
        return log;
    }
}
