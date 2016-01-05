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
package org.apache.brooklyn.entity.machine.pool;

import static org.testng.Assert.fail;

import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.machine.pool.ServerPool;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public abstract class AbstractServerPoolTest {

    // Note not extending BrooklynAppUnitTestSupport because sub-classes of this are for live and for unit tests.
    // Instead, we have to repeat that logic for setting SKIP_ON_BOX_BASE_DIR_RESOLUTION
    
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
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, shouldSkipOnBoxBaseDirResolution());
        poolApp = ApplicationBuilder.newManagedApp(appSpec, mgmt);

        pool = poolApp.createAndManageChild(EntitySpec.create(ServerPool.class)
                .configure(ServerPool.INITIAL_SIZE, getInitialPoolSize())
                .configure(ServerPool.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));
        poolApp.start(ImmutableList.of(location));
        EntityTestUtils.assertAttributeEqualsEventually(pool, Attributes.SERVICE_UP, true);
        assertAvailableCountEventuallyEquals(getInitialPoolSize());
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
    
    protected boolean shouldSkipOnBoxBaseDirResolution() {
        return true;
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

    protected void assertAvailableCountEventuallyEquals(int count) {
        assertAvailableCountEventuallyEquals(pool, count);
    }

    protected void assertAvailableCountEventuallyEquals(ServerPool pool, int count) {
        EntityTestUtils.assertAttributeEqualsEventually(pool, ServerPool.AVAILABLE_COUNT, count);
    }

    protected void assertClaimedCountEventuallyEquals(int count) {
        assertClaimedCountEventuallyEquals(pool, count);
    }

    protected void assertClaimedCountEventuallyEquals(ServerPool pool, Integer count) {
        EntityTestUtils.assertAttributeEqualsEventually(pool, ServerPool.CLAIMED_COUNT, count);
    }

    protected TestApplication createAppWithChildren(int numChildren) {
        if (numChildren < 0) fail("Invalid number of children for app: " + numChildren);
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, shouldSkipOnBoxBaseDirResolution());
        TestApplication app = ApplicationBuilder.newManagedApp(appSpec, mgmt);
        while (numChildren-- > 0) {
            app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class));
        }
        createdApps.add(app);
        return app;
    }
}
