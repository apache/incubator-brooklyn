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
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.exceptions.Exceptions;

public abstract class AbstractServerPoolTest {

    private static final int DEFAULT_POOL_SIZE = 3;

    protected Location location;
    protected ManagementContext mgmt;
    protected TestApplication poolApp;
    protected ServerPool pool;
    private List<TestApplication> createdApps = Lists.newLinkedList();

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        createdApps.clear();
        mgmt = createManagementContext();
        location = createLocation();
        poolApp = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        pool = poolApp.createAndManageChild(EntitySpec.create(ServerPool.class)
                .configure(ServerPool.INITIAL_SIZE, getInitialPoolSize())
                .configure(ServerPool.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));
        poolApp.start(ImmutableList.of(location));
        assertTrue(pool.getAttribute(Attributes.SERVICE_UP));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        // Kills the apps before terminating the pool
        for (TestApplication app : createdApps) {
            Entities.destroy(app);
        }
        if (mgmt != null) {
            Entities.destroyAll(mgmt);
            mgmt = null;
        }
    }

    protected int getInitialPoolSize() {
        return DEFAULT_POOL_SIZE;
    }

    protected ManagementContext createManagementContext() {
        return new LocalManagementContextForTests();
    }

    /** @return Creates a LocalhostMachineProvisioningLocation */
    protected Location createLocation() {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
    }

    protected void assertNoMachinesAvailableForApp(TestApplication app) {
        try {
            app.start(ImmutableList.of(pool.getDynamicLocation()));
            fail("Expected exception when starting app with too many entities for pool");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, NoMachinesAvailableException.class);
            if (t == null) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void assertAvailableCountEquals(int count) {
        assertAvailableCountEquals(pool, count);
    }

    protected void assertAvailableCountEquals(ServerPool pool, Integer count) {
        assertEquals(pool.getAttribute(ServerPool.AVAILABLE_COUNT), count);
    }

    protected void assertAvailableCountEventuallyEquals(int count) {
        EntityTestUtils.assertAttributeEqualsEventually(pool, ServerPool.AVAILABLE_COUNT, count);
    }

    protected void assertClaimedCountEquals(int count) {
        assertClaimedCountEquals(pool, count);
    }

    protected void assertClaimedCountEquals(ServerPool pool, Integer count) {
        assertEquals(pool.getAttribute(ServerPool.CLAIMED_COUNT), count);
    }

    protected TestApplication createAppWithChildren(int numChildren) {
        if (numChildren < 0) fail("Invalid number of children for app: " + numChildren);
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        while (numChildren-- > 0) {
            app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class));
        }
        createdApps.add(app);
        return app;
    }
}
