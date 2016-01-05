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
package org.apache.brooklyn.location.localhost;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.internal.LocationInternal;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.text.StringEscapes.JavaStringEscapes;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class LocalhostLocationResolverTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
        brooklynProperties = managementContext.getBrooklynProperties();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testTakesLocalhostScopedProperties() {
        brooklynProperties.put("brooklyn.location.localhost.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.location.localhost.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.location.localhost.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.location.localhost.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.location.localhost.privateKeyPassphrase", "myprivateKeyPassphrase");

        Map<String, Object> conf = resolve("localhost").config().getBag().getAllConfig();
        
        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testTakesLocalhostDeprecatedScopedProperties() {
        brooklynProperties.put("brooklyn.localhost.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.localhost.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.localhost.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.localhost.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.localhost.privateKeyPassphrase", "myprivateKeyPassphrase");

        Map<String, Object> conf = resolve("localhost").config().getBag().getAllConfig();
        
        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testTakesDeprecatedProperties() {
        brooklynProperties.put("brooklyn.localhost.private-key-file", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.localhost.public-key-file", "mypublickeyfile");
        brooklynProperties.put("brooklyn.localhost.private-key-data", "myprivateKeyData");
        brooklynProperties.put("brooklyn.localhost.public-key-data", "myPublicKeyData");
        brooklynProperties.put("brooklyn.localhost.private-key-passphrase", "myprivateKeyPassphrase");
        Map<String, Object> conf = resolve("localhost").config().getBag().getAllConfig();
        
        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }
    
    @Test
    public void testPropertyScopePrescedence() {
        brooklynProperties.put("brooklyn.location.named.mynamed", "localhost");
        
        // prefer those in "named" over everything else
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.location.localhost.privateKeyFile", "privateKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.localhost.privateKeyFile", "privateKeyFile-inGeneric");

        // prefer those in provider-specific over generic
        brooklynProperties.put("brooklyn.location.localhost.publicKeyFile", "publicKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.location.publicKeyFile", "publicKeyFile-inGeneric");

        // prefer location-generic if nothing else
        brooklynProperties.put("brooklyn.location.privateKeyData", "privateKeyData-inGeneric");

        Map<String, Object> conf = resolve("named:mynamed").config().getBag().getAllConfig();
        
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inGeneric");
    }

    @Test
    public void testLocalhostLoads() {
        Assert.assertTrue(resolve("localhost") instanceof LocalhostMachineProvisioningLocation);
    }

    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix");
        assertThrowsIllegalArgument("localhost(name=abc"); // no closing bracket
        assertThrowsIllegalArgument("localhost(name)"); // no value for name
        assertThrowsIllegalArgument("localhost(name=)"); // no value for name
    }
    

    @Test
    public void testAcceptsList() {
        List<Location> l = getLocationResolver().resolve(ImmutableList.of("localhost"));
        assertEquals(l.size(), 1, "l="+l);
        assertTrue(l.get(0) instanceof LocalhostMachineProvisioningLocation, "l="+l);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRegistryCommaResolution() throws NoMachinesAvailableException {
        List<Location> l;
        l = getLocationResolver().resolve(JavaStringEscapes.unwrapJsonishListIfPossible("localhost,localhost,localhost"));
        assertEquals(l.size(), 3, "l="+l);
        assertTrue(l.get(0) instanceof LocalhostMachineProvisioningLocation, "l="+l);
        assertTrue(l.get(1) instanceof LocalhostMachineProvisioningLocation, "l="+l);
        assertTrue(l.get(2) instanceof LocalhostMachineProvisioningLocation, "l="+l);

        // And check works if comma in brackets
        l = getLocationResolver().resolve(JavaStringEscapes.unwrapJsonishListIfPossible(
            "[ \"byon:(hosts=\\\"192.168.0.1\\\",user=bob)\", \"byon:(hosts=\\\"192.168.0.2\\\",user=bob2)\" ]"));
        assertEquals(l.size(), 2, "l="+l);
        assertTrue(l.get(0) instanceof FixedListMachineProvisioningLocation, "l="+l);
        assertTrue(l.get(1) instanceof FixedListMachineProvisioningLocation, "l="+l);
        assertEquals(((FixedListMachineProvisioningLocation<SshMachineLocation>)l.get(0)).obtain().getUser(), "bob");
        assertEquals(((FixedListMachineProvisioningLocation<SshMachineLocation>)l.get(1)).obtain().getUser(), "bob2");
    }

    @Test(expectedExceptions={NoSuchElementException.class})
    public void testRegistryCommaResolutionInListNotAllowed1() throws NoMachinesAvailableException {
        // disallowed since 0.7.0
        getLocationResolver().resolve(ImmutableList.of("localhost,localhost,localhost"));
    }

    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testRegistryCommaResolutionInListNotAllowed2() throws NoMachinesAvailableException {
        // disallowed since 0.7.0
        // fails because it interprets the entire string as a single spec, which does not parse
        getLocationResolver().resolve(ImmutableList.of("localhost(),localhost()"));
    }

    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testRegistryCommaResolutionInListNotAllowed3() throws NoMachinesAvailableException {
        // disallowed since 0.7.0
        // fails because it interprets the entire string as a single spec, which does not parse
        getLocationResolver().resolve(ImmutableList.of("localhost(name=a),localhost(name=b)"));
    }

    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testDoesNotAcceptsListOLists() {
        ((BasicLocationRegistry)managementContext.getLocationRegistry()).resolve(ImmutableList.of(ImmutableList.of("localhost")));
    }

    @Test
    public void testResolvesExplicitName() throws Exception {
        Location location = resolve("localhost(name=myname)");
        assertTrue(location instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location.getDisplayName(), "myname");
    }
    
    @Test
    public void testWithOldStyleColon() throws Exception {
        Location location = resolve("localhost:(name=myname)");
        assertTrue(location instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location.getDisplayName(), "myname");
    }
    
    @Test
    public void testResolvesPropertiesInSpec() throws Exception {
        LocationInternal location = resolve("localhost(privateKeyFile=myprivatekeyfile,name=myname)");
        assertTrue(location instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location.getDisplayName(), "myname");
        assertEquals(location.config().getBag().getStringKey("privateKeyFile"), "myprivatekeyfile");
    }
    
    @Test
    public void testResolvesDefaultName() throws Exception {
        Location location = resolve("localhost");
        assertTrue(location instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location.getDisplayName(), "localhost");

        Location location2 = resolve("localhost()");
        assertTrue(location2 instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location2.getDisplayName(), "localhost");
    }
    
    private BasicLocationRegistry getLocationResolver() {
        return (BasicLocationRegistry) managementContext.getLocationRegistry();
    }
    
    private LocationInternal resolve(String val) {
        Location l = managementContext.getLocationRegistry().resolve(val);
        Assert.assertNotNull(l);
        return (LocationInternal) l;
    }
    
    private void assertThrowsNoSuchElement(String val) {
        try {
            resolve(val);
            fail();
        } catch (NoSuchElementException e) {
            // success
        }

        // and check the long form returns an Absent (not throwing)
        Assert.assertTrue(managementContext.getLocationRegistry().resolve(val, false, null).isAbsent());
    }
    
    private void assertThrowsIllegalArgument(String val) {
        try {
            resolve(val);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
        
        // and check the long form returns an Absent (not throwing)
        Assert.assertTrue(managementContext.getLocationRegistry().resolve(val, false, null).isAbsent());
    }
}
