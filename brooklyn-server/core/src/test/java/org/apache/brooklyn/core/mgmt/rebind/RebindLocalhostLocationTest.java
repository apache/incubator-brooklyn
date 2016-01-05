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
package org.apache.brooklyn.core.mgmt.rebind;

import static org.testng.Assert.assertEquals;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoManifest;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class RebindLocalhostLocationTest extends RebindTestFixtureWithApp {

    private static final Logger LOG = LoggerFactory.getLogger(RebindLocalhostLocationTest.class);

    private LocalhostMachineProvisioningLocation origLoc;
    private SshMachineLocation origChildLoc;
    
    @BeforeMethod(alwaysRun=true)

    public void setUp() throws Exception {
        super.setUp();
        origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        origChildLoc = origLoc.obtain();
    }

    /** takes a while, so just doing 10; see comment on {@link RebindSshMachineLocationTest} */
    @Test(groups="Integration", invocationCount=10)
    public void testMachineUsableAfterRebindRepeatedly() throws Exception {
        try {
            testMachineUsableAfterRebind();
        } catch (Exception e) {
            // Ensure exception is reported in log immediately, so can match logging with failed run
            LOG.warn("Test failed", e);
            throw e;
        }
    }

    @Test(groups="Integration")
    public void testMachineUsableAfterRebind() throws Exception {
        // TODO See comment in RebindSshMachineLocationTets.testMachineUsableAfterRebind.
        // With locations not being entities, if you switch the order of these two lines then the test sometimes fails.
        // This is because the 'user' field is set (from the default PROP_USER value) when we first exec something.
        // Until that point, the field will be persisted as null, so will be explicitly set to null when deserializing.
        // There's a race for whether we've set the 'user' field before the loc gets persisted (which happens as a side-effect
        // of persisting the app along with its location tree).

        assertEquals(origChildLoc.execScript(Collections.<String,Object>emptyMap(), "mysummary", ImmutableList.of("true")), 0);
        origApp.start(ImmutableList.of(origLoc));

        newApp = (TestApplication) rebind();
        LocalhostMachineProvisioningLocation newLoc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(newApp.getLocations(), 0);
        SshMachineLocation newChildLoc = (SshMachineLocation) Iterables.get(newLoc.getChildren(), 0);
        assertEquals(newChildLoc.execScript(Collections.<String,Object>emptyMap(), "mysummary", ImmutableList.of("true")), 0);
    }

    @Test(groups="Integration")
    public void testMachineCleansUp() throws Exception {
        testMachineUsableAfterRebind();
        newApp.stop();

        switchOriginalToNewManagementContext();
        
        // TODO how should we automatically unmanage these?
        // (in this test, locations are created manually, so probably should be destroyed manually, 
        // but in most cases we should probably unmanage the location as part of the entity;
        // could keep the entity ID only in the location, then safely reverse-check usages?)
        // see related non-integration test in RebindEntityTest
        origManagementContext.getLocationManager().unmanage(origLoc);
        
        RebindTestUtils.waitForPersisted(origManagementContext);
        
        BrooklynMementoManifest mf = loadMementoManifest();
        Assert.assertTrue(mf.getLocationIdToType().isEmpty(), "Expected no locations; had "+mf.getLocationIdToType());
    }
    
}
