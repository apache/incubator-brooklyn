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
package brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.OsDetails;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindJcloudsLocationLiveTest extends AbstractJcloudsLiveTest {

    public static final String AWS_EC2_REGION_NAME = AWS_EC2_USEAST_REGION_NAME;
    public static final String AWS_EC2_LOCATION_SPEC = "jclouds:" + AWS_EC2_PROVIDER + ":" + AWS_EC2_REGION_NAME;

    private ClassLoader classLoader = getClass().getClassLoader();
    private TestApplication origApp;
    private LiveTestEntity origEntity;
    private File mementoDir;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        origApp = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), managementContext);
        origEntity = origApp.createAndManageChild(EntitySpec.create(LiveTestEntity.class));

        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        jcloudsLocation.setConfig(JcloudsLocation.HARDWARE_ID, AWS_EC2_SMALL_HARDWARE_ID);
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (origApp != null) Entities.destroyAll(origApp.getManagementContext());
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Override
    protected LocalManagementContext newManagementContext() {
        mementoDir = Files.createTempDir();
        return RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
    }
    
    @Test(groups="Live")
    public void testRebindsToJcloudsMachine() throws Exception {
        origApp.start(ImmutableList.of(jcloudsLocation));
        JcloudsLocation origJcloudsLocation = jcloudsLocation;
        System.out.println("orig locations: " + origEntity.getLocations());
        JcloudsSshMachineLocation origMachine = (JcloudsSshMachineLocation) Iterables.find(origEntity.getLocations(), Predicates.instanceOf(JcloudsSshMachineLocation.class));

        TestApplication newApp = rebind();
        LiveTestEntity newEntity = (LiveTestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(LiveTestEntity.class));
        JcloudsSshMachineLocation newMachine = (JcloudsSshMachineLocation) Iterables.find(newEntity.getLocations(), Predicates.instanceOf(JcloudsSshMachineLocation.class));

        assertMachineEquals(newMachine, origMachine);
        assertTrue(newMachine.isSshable());

        JcloudsLocation newJcloudsLoction = newMachine.getParent();
        assertJcloudsLocationEquals(newJcloudsLoction, origJcloudsLocation);
    }

    private void assertMachineEquals(JcloudsSshMachineLocation actual, JcloudsSshMachineLocation expected) {
        String errmsg = "actual="+actual.toVerboseString()+"; expected="+expected.toVerboseString();
        assertEquals(actual.getId(), expected.getId(), errmsg);
        assertEquals(actual.getJcloudsId(), expected.getJcloudsId(), errmsg);
        assertOsDetailEquals(actual.getOsDetails(), expected.getOsDetails());
        assertEquals(actual.getSshHostAndPort(), expected.getSshHostAndPort());
        assertEquals(actual.getPrivateAddress(), expected.getPrivateAddress());
        assertConfigBagEquals(actual.getAllConfigBag(), expected.getAllConfigBag(), errmsg);
    }

    private void assertOsDetailEquals(OsDetails actual, OsDetails expected) {
        String errmsg = "actual="+actual+"; expected="+expected;
        if (actual == null) assertNull(expected, errmsg);
        assertEquals(actual.isWindows(), expected.isWindows());
        assertEquals(actual.isLinux(), expected.isLinux());
        assertEquals(actual.isMac(), expected.isMac());
        assertEquals(actual.getName(), expected.getName());
        assertEquals(actual.getArch(), expected.getArch());
        assertEquals(actual.getVersion(), expected.getVersion());
        assertEquals(actual.is64bit(), expected.is64bit());
    }

    private void assertJcloudsLocationEquals(JcloudsLocation actual, JcloudsLocation expected) {
        String errmsg = "actual="+actual.toVerboseString()+"; expected="+expected.toVerboseString();
        assertEquals(actual.getId(), expected.getId(), errmsg);
        assertEquals(actual.getProvider(), expected.getProvider(), errmsg);
        assertEquals(actual.getRegion(), expected.getRegion(), errmsg);
        assertEquals(actual.getIdentity(), expected.getIdentity(), errmsg);
        assertEquals(actual.getCredential(), expected.getCredential(), errmsg);
        assertEquals(actual.getHostGeoInfo(), expected.getHostGeoInfo(), errmsg);
        assertConfigBagEquals(actual.getAllConfigBag(), expected.getAllConfigBag(), errmsg);
    }

    private void assertConfigBagEquals(ConfigBag actual, ConfigBag expected, String errmsg) {
        // TODO revisit the strong assertion that configBags are equal
        
//        // TODO Can we include all of these things (e.g. when locations are entities, so flagged fields not treated special)?
//        List<String> configToIgnore = ImmutableList.of("id", "template", "usedPorts", "machineCreationSemaphore", "config");
//        MutableMap<Object, Object> actualMap = MutableMap.builder().putAll(actual.getAllConfig())
//                .removeAll(configToIgnore)
//                .build();
//        MutableMap<Object, Object> expectedMap = MutableMap.builder().putAll(expected.getAllConfig())
//                .removeAll(configToIgnore)
//                .build();
//        
//        assertEquals(actualMap, expectedMap, errmsg+"; actualBag="+actualMap+"; expectedBag="+expectedMap);
    }
    
    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}
