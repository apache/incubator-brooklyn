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
package brooklyn.location.access;

import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;

public class PortForwardManagerLocationResolverTest {

    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testReturnsSameInstanceBasedOnScope() {
        Location global1 = resolve("portForwardManager()"); // defaults to global
        Location global2 = resolve("portForwardManager()");
        Location global3 = resolve("portForwardManager(scope=global)");
        assertSame(global1, global2);
        assertSame(global1, global3);
        
        Location a1 = resolve("portForwardManager(scope=a)");
        Location a2 = resolve("portForwardManager(scope=a)");
        assertSame(a1, a2);
        assertNotSame(global1, a1);
        
        Location b1 = resolve("portForwardManager(scope=b)");
        assertNotSame(global1, b1);
        assertNotSame(a1, b1);
    }

    private Location resolve(String val) {
        Location l = managementContext.getLocationRegistry().resolve(val);
        Assert.assertNotNull(l);
        return l;
    }
    
    private void assertSame(Location loc1, Location loc2) {
        Assert.assertNotNull(loc1);
        Assert.assertTrue(loc1 instanceof PortForwardManager, "loc1="+loc1);
        Assert.assertSame(loc1, loc2);
    }
    
    private void assertNotSame(Location loc1, Location loc2) {
        Assert.assertNotNull(loc1);
        Assert.assertNotNull(loc2);
        Assert.assertTrue(loc1 instanceof PortForwardManager, "loc1="+loc1);
        Assert.assertTrue(loc2 instanceof PortForwardManager, "loc2="+loc2);
        Assert.assertNotSame(loc1, loc2);
        Assert.assertNotEquals(((PortForwardManager)loc1).getId(), ((PortForwardManager)loc2).getId());
    }
}
