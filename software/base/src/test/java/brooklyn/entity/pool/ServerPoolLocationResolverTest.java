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
package brooklyn.entity.pool;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

public class ServerPoolLocationResolverTest {

    private LocalManagementContext managementContext;
    private Entity locationOwner;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests(BrooklynProperties.Factory.newEmpty());
        TestApplication t = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        locationOwner = t.createAndManageChild(EntitySpec.create(ServerPool.class)
                .configure(ServerPool.INITIAL_SIZE, 0)
                .configure(ServerPool.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));
        Location poolLocation = managementContext.getLocationManager()
                .createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        t.start(ImmutableList.of(poolLocation));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testResolve() {
        ServerPoolLocation location = resolve("pool:" + locationOwner.getId());
        assertEquals(location.getOwner().getId(), locationOwner.getId());
    }

    @Test
    public void testSetsDisplayName() {
        ServerPoolLocation location = resolve("pool:" + locationOwner.getId() + ":(displayName=xyz)");
        assertEquals(location.getDisplayName(), "xyz");
    }

    private ServerPoolLocation resolve(String val) {
        Map<String, Object> flags = MutableMap.<String, Object>of(DynamicLocation.OWNER.getName(), locationOwner);
        Location l = managementContext.getLocationRegistry().resolve(val, flags);
        Assert.assertNotNull(l);
        return (ServerPoolLocation) l;
    }

}
