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
package org.apache.brooklyn.location.byon;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.os.Os;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class FixedListMachineProvisioningLocationRebindTest {

    private FixedListMachineProvisioningLocation<SshMachineLocation> origLoc;
    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext origManagementContext;
    private TestApplication origApp;
    private TestApplication newApp;
    private File mementoDir;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Os.newTempDir(getClass());
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        
        origLoc = new FixedListMachineProvisioningLocation.Builder(origManagementContext.getLocationManager())
                .addAddresses("localhost", "127.0.0.1")
                .user("myuser")
                .keyFile("/path/to/myPrivateKeyFile")
                .keyData("myKeyData")
                .keyPassphrase("myKeyPassphrase")
                .build();
        origApp = ApplicationBuilder.newManagedApp(TestApplication.class, origManagementContext);
        origApp.start(ImmutableList.of(origLoc));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (origManagementContext != null) Entities.destroyAll(origManagementContext);
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Test
    public void testRebindPreservesConfig() throws Exception {
        newApp = rebind();
        FixedListMachineProvisioningLocation<SshMachineLocation> newLoc = (FixedListMachineProvisioningLocation<SshMachineLocation>) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getId(), origLoc.getId());
        assertEquals(newLoc.getDisplayName(), origLoc.getDisplayName());
        assertEquals(newLoc.getHostGeoInfo(), origLoc.getHostGeoInfo());
        assertEquals(newLoc.getConfig(LocationConfigKeys.USER), origLoc.getConfig(LocationConfigKeys.USER));
        assertEquals(newLoc.getConfig(LocationConfigKeys.PRIVATE_KEY_PASSPHRASE), origLoc.getConfig(LocationConfigKeys.PRIVATE_KEY_PASSPHRASE));
        assertEquals(newLoc.getConfig(LocationConfigKeys.PRIVATE_KEY_FILE), origLoc.getConfig(LocationConfigKeys.PRIVATE_KEY_FILE));
        assertEquals(newLoc.getConfig(LocationConfigKeys.PRIVATE_KEY_DATA), origLoc.getConfig(LocationConfigKeys.PRIVATE_KEY_DATA));
    }

    @Test
    public void testRebindParentRelationship() throws Exception {
        newApp = rebind();
        FixedListMachineProvisioningLocation<SshMachineLocation> newLoc = (FixedListMachineProvisioningLocation<SshMachineLocation>) Iterables.get(newApp.getLocations(), 0);
        
        assertLocationIdsEqual(newLoc.getChildren(), origLoc.getChildren());
        assertEquals(Iterables.get(newLoc.getChildren(), 0).getParent(), newLoc);
        assertEquals(Iterables.get(newLoc.getChildren(), 1).getParent(), newLoc);
    }

    @Test
    public void testRebindPreservesInUseMachines() throws Exception {
        SshMachineLocation inuseMachine = origLoc.obtain();
        origApp.sensors().set(TestApplication.SERVICE_UP, true); // to force persist, and thus avoid race
        
        newApp = rebind();
        FixedListMachineProvisioningLocation<SshMachineLocation> newLoc = (FixedListMachineProvisioningLocation<SshMachineLocation>) Iterables.get(newApp.getLocations(), 0);
        
        assertLocationIdsEqual(newLoc.getInUse(), origLoc.getInUse());
        assertLocationIdsEqual(newLoc.getAvailable(), origLoc.getAvailable());
    }

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
    
    private void assertLocationIdsEqual(Iterable<? extends Location> actual, Iterable<? extends Location> expected) {
        Function<Location, String> locationIdFunction = new Function<Location, String>() {
            @Override public String apply(@Nullable Location input) {
                return (input != null) ? input.getId() : null;
            }
        };
        Set<String> actualIds = MutableSet.copyOf(Iterables.transform(actual, locationIdFunction));
        Set<String> expectedIds = MutableSet.copyOf(Iterables.transform(expected, locationIdFunction));
        
        assertEquals(actualIds, expectedIds);
    }
}
