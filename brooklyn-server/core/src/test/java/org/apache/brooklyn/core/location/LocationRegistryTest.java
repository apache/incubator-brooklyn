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
package org.apache.brooklyn.core.location;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class LocationRegistryTest {
    
    private static final Logger log = LoggerFactory.getLogger(LocationRegistryTest.class);
    
    private LocalManagementContext mgmt;
    private LocationDefinition locdef;

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    @Test
    public void testNamedLocationsPropertyDefinedLocations() {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("brooklyn.location.named.foo", "byon:(hosts=\"root@192.168.1.{1,2,3,4}\")");
        properties.put("brooklyn.location.named.foo.privateKeyFile", "~/.ssh/foo.id_rsa");
        mgmt = LocalManagementContextForTests.newInstance(properties);
        log.info("foo properties gave defined locations: "+mgmt.getLocationRegistry().getDefinedLocations());
        locdef = mgmt.getLocationRegistry().getDefinedLocationByName("foo");
        Assert.assertNotNull(locdef, "Expected 'foo' location; but had "+mgmt.getLocationRegistry().getDefinedLocations());
        Assert.assertEquals(locdef.getConfig().get("privateKeyFile"), "~/.ssh/foo.id_rsa");
    }
    
    @Test(dependsOnMethods="testNamedLocationsPropertyDefinedLocations")
    public void testResolvesByNamedAndId() {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("brooklyn.location.named.foo", "byon:(hosts=\"root@192.168.1.{1,2,3,4}\")");
        properties.put("brooklyn.location.named.foo.privateKeyFile", "~/.ssh/foo.id_rsa");
        mgmt = LocalManagementContextForTests.newInstance(properties);

        locdef = mgmt.getLocationRegistry().getDefinedLocationByName("foo");
        log.info("testResovlesBy has defined locations: "+mgmt.getLocationRegistry().getDefinedLocations());
        
        Location l = mgmt.getLocationRegistry().resolve("named:foo");
        Assert.assertNotNull(l);
        Assert.assertEquals(l.getConfig(LocationConfigKeys.PRIVATE_KEY_FILE), "~/.ssh/foo.id_rsa");
        
        l = mgmt.getLocationRegistry().resolve("foo");
        Assert.assertNotNull(l);
        Assert.assertEquals(l.getConfig(LocationConfigKeys.PRIVATE_KEY_FILE), "~/.ssh/foo.id_rsa");
        
        l = mgmt.getLocationRegistry().resolve("id:"+locdef.getId());
        Assert.assertNotNull(l);
        Assert.assertEquals(l.getConfig(LocationConfigKeys.PRIVATE_KEY_FILE), "~/.ssh/foo.id_rsa");
        
        l = mgmt.getLocationRegistry().resolve(locdef.getId());
        Assert.assertNotNull(l);
        Assert.assertEquals(l.getConfig(LocationConfigKeys.PRIVATE_KEY_FILE), "~/.ssh/foo.id_rsa");
    }

    @Test
    public void testLocationGetsDisplayName() {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("brooklyn.location.named.foo", "byon:(hosts=\"root@192.168.1.{1,2,3,4}\")");
        properties.put("brooklyn.location.named.foo.displayName", "My Foo");
        mgmt = LocalManagementContextForTests.newInstance(properties);
        Location l = mgmt.getLocationRegistry().resolve("foo");
        Assert.assertEquals(l.getDisplayName(), "My Foo");
    }
    
    @Test
    public void testLocationGetsDefaultDisplayName() {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("brooklyn.location.named.foo", "byon:(hosts=\"root@192.168.1.{1,2,3,4}\")");
        mgmt = LocalManagementContextForTests.newInstance(properties);
        Location l = mgmt.getLocationRegistry().resolve("foo");
        Assert.assertNotNull(l.getDisplayName());
        Assert.assertTrue(l.getDisplayName().startsWith(FixedListMachineProvisioningLocation.class.getSimpleName()), "name="+l.getDisplayName());
        // TODO currently it gives default name; it would be nice to use 'foo', 
        // or at least to have access to the spec (and use it e.g. in places such as DynamicFabric)
        // Assert.assertEquals(l.getDisplayName(), "foo");
    }
    
    @Test
    public void testSetupForTesting() {
        mgmt = LocalManagementContextForTests.newInstance();
        BasicLocationRegistry.setupLocationRegistryForTesting(mgmt);
        Assert.assertNotNull(mgmt.getLocationRegistry().getDefinedLocationByName("localhost"));
    }

    @Test
    public void testCircularReference() {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("brooklyn.location.named.bar", "named:bar");
        mgmt = LocalManagementContextForTests.newInstance(properties);
        log.info("bar properties gave defined locations: "+mgmt.getLocationRegistry().getDefinedLocations());
        boolean resolved = false;
        try {
            mgmt.getLocationRegistry().resolve("bar");
            resolved = true;
        } catch (IllegalStateException e) {
            //expected
            log.info("bar properties correctly caught circular reference: "+e);
        }
        if (resolved)
            // probably won't happen, if test fails will loop endlessly above
            Assert.fail("Circular reference resolved location");
    }

    protected boolean findLocationMatching(String regex) {
        for (LocationDefinition d: mgmt.getLocationRegistry().getDefinedLocations().values()) {
            if (d.getName()!=null && d.getName().matches(regex)) return true;
        }
        return false;
    }
    
    @Test
    public void testLocalhostEnabled() {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("brooklyn.location.localhost.enabled", true);
        mgmt = LocalManagementContextForTests.newInstance(properties);
        Assert.assertTrue( findLocationMatching("localhost") );
    }

    @Test
    public void testLocalhostDisabled() {
        BrooklynProperties properties = BrooklynProperties.Factory.newEmpty();
        properties.put("brooklyn.location.localhost.enabled", false);
        mgmt = LocalManagementContextForTests.newInstance(properties);
        log.info("RESOLVERS: "+mgmt.getLocationRegistry().getDefinedLocations());
        log.info("DEFINED LOCATIONS: "+mgmt.getLocationRegistry().getDefinedLocations());
        Assert.assertFalse( findLocationMatching("localhost") );
    }
    
}
