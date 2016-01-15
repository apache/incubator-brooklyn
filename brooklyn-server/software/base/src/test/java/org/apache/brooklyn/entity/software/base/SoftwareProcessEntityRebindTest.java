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
package org.apache.brooklyn.entity.software.base;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceProblemsLogic;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.SoftwareProcessEntityTest.MyService;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class SoftwareProcessEntityRebindTest extends BrooklynAppUnitTestSupport {

    private ClassLoader classLoader = getClass().getClassLoader();
    private TestApplication newApp;
    private ManagementContext newManagementContext;
    private MyService origE;
    private File mementoDir;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        mgmt = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
        super.setUp();
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        if (newManagementContext != null) Entities.destroyAll(newManagementContext);
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Test
    public void testReleasesLocationOnStopAfterRebinding() throws Exception {
        origE = app.createAndManageChild(EntitySpec.create(MyService.class));
        
        MyProvisioningLocation origLoc = mgmt.getLocationManager().createLocation(LocationSpec.create(MyProvisioningLocation.class)
                .displayName("mylocname"));
        app.start(ImmutableList.of(origLoc));
        assertEquals(origLoc.inUseCount.get(), 1);
        
        newApp = (TestApplication) rebind();
        MyProvisioningLocation newLoc = (MyProvisioningLocation) Iterables.getOnlyElement(newApp.getLocations());
        assertEquals(newLoc.inUseCount.get(), 1);
        
        newApp.stop();
        assertEquals(newLoc.inUseCount.get(), 0);
    }

    @Test
    public void testCreatesDriverAfterRebind() throws Exception {
        origE = app.createAndManageChild(EntitySpec.create(MyService.class));
        //the entity skips enricher initialization, do it explicitly
        origE.enrichers().add(ServiceStateLogic.newEnricherForServiceStateFromProblemsAndUp());

        MyProvisioningLocation origLoc = mgmt.getLocationManager().createLocation(LocationSpec.create(MyProvisioningLocation.class)
                .displayName("mylocname"));
        app.start(ImmutableList.of(origLoc));
        assertEquals(origE.getAttribute(Attributes.SERVICE_STATE_EXPECTED).getState(), Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(origE, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);

        ServiceProblemsLogic.updateProblemsIndicator((EntityLocal)origE, "test", "fire");
        EntityTestUtils.assertAttributeEqualsEventually(origE, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);

        newApp = (TestApplication) rebind();
        MyService newE = (MyService) Iterables.getOnlyElement(newApp.getChildren());
        assertTrue(newE.getDriver() != null, "driver should be initialized");
    }

    @Test
    public void testDoesNotCreateDriverAfterRebind() throws Exception {
        origE = app.createAndManageChild(EntitySpec.create(MyService.class));
        //the entity skips enricher initialization, do it explicitly
        origE.enrichers().add(ServiceStateLogic.newEnricherForServiceStateFromProblemsAndUp());
        
        MyProvisioningLocation origLoc = mgmt.getLocationManager().createLocation(LocationSpec.create(MyProvisioningLocation.class)
                .displayName("mylocname"));
        app.start(ImmutableList.of(origLoc));
        assertEquals(origE.getAttribute(Attributes.SERVICE_STATE_EXPECTED).getState(), Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(origE, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);

        ServiceStateLogic.setExpectedState(origE, Lifecycle.ON_FIRE);
        EntityTestUtils.assertAttributeEqualsEventually(origE, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);

        newApp = (TestApplication) rebind();
        MyService newE = (MyService) Iterables.getOnlyElement(newApp.getChildren());
        assertNull(newE.getDriver(), "driver should not be initialized because entity is in a permanent failure");
    }

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(app);
        TestApplication result = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        newManagementContext = result.getManagementContext();
        return result;
    }
    
    public static class MyProvisioningLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {
        @SetFromFlag(defaultVal="0")
        AtomicInteger inUseCount;

        public MyProvisioningLocation() {
        }
        
        @Override
        public MachineProvisioningLocation<SshMachineLocation> newSubLocation(Map<?, ?> newFlags) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public SshMachineLocation obtain(Map flags) throws NoMachinesAvailableException {
            inUseCount.incrementAndGet();
            return getManagementContext().getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                    .parent(this)
                    .configure("address","localhost"));
        }

        @Override
        public void release(SshMachineLocation machine) {
            inUseCount.decrementAndGet();
        }

        @Override
        public Map getProvisioningFlags(Collection tags) {
            return Collections.emptyMap();
        }
    }
}
