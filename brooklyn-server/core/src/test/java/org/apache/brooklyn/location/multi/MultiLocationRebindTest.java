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
package org.apache.brooklyn.location.multi;

import java.io.File;
import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.location.cloud.AvailabilityZoneExtension;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.multi.MultiLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MultiLocationRebindTest {

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext origManagementContext;
    private ManagementContext newManagementContext;
    private File mementoDir;
    
    private TestApplication origApp;
    private TestApplication newApp;
    private SshMachineLocation mac1a;
    private SshMachineLocation mac2a;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc1;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc2;
    private MultiLocation<SshMachineLocation> multiLoc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Os.newTempDir(getClass());
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), origManagementContext);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (origManagementContext != null) Entities.destroyAll(origManagementContext);
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        if (newManagementContext != null) Entities.destroyAll(newManagementContext);
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRebindsMultiLocation() throws Exception {
        mac1a = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .displayName("mac1a")
                .configure("address", Networking.getInetAddressWithFixedName("1.1.1.1")));
        mac2a = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .displayName("mac2a")
                .configure("address", Networking.getInetAddressWithFixedName("1.1.1.3")));
        loc1 = origManagementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .displayName("loc1")
                .configure("machines", MutableSet.of(mac1a)));
        loc2 = origManagementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .displayName("loc2")
                .configure("machines", MutableSet.of(mac2a)));
        multiLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(MultiLocation.class)
                        .displayName("multiLoc")
                        .configure("subLocations", ImmutableList.of(loc1, loc2)));
        
        newApp = rebind();
        newManagementContext = newApp.getManagementContext();
        
        MultiLocation newMultiLoc = (MultiLocation) Iterables.find(newManagementContext.getLocationManager().getLocations(), Predicates.instanceOf(MultiLocation.class));
        AvailabilityZoneExtension azExtension = newMultiLoc.getExtension(AvailabilityZoneExtension.class);
        List<Location> newSublLocs = azExtension.getAllSubLocations();
        Iterable<String> newSubLocNames = Iterables.transform(newSublLocs, new Function<Location, String>() {
            @Override public String apply(Location input) {
                return (input == null) ? null : input.getDisplayName();
            }});
        Asserts.assertEqualsIgnoringOrder(newSubLocNames, ImmutableList.of("loc1", "loc2"));
    }
    
    private TestApplication rebind() throws Exception {
        return rebind(true);
    }
    
    private TestApplication rebind(boolean checkSerializable) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        if (checkSerializable) {
            RebindTestUtils.checkCurrentMementoSerializable(origApp);
        }
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}
