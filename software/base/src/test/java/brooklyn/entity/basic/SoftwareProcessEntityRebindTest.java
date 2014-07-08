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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.SoftwareProcessEntityTest.MyService;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.flags.SetFromFlag;

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

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(app);
        TestApplication result = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        newManagementContext = result.getManagementContext();
        return result;
    }
    
    public static class MyProvisioningLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {
        private static final long serialVersionUID = 1L;
        
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
