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
package org.apache.brooklyn.location.jclouds;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.rebind.RebindOptions;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;

public class RebindJcloudsLocationLiveTest extends AbstractJcloudsLiveTest {

    public static final String AWS_EC2_REGION_NAME = AWS_EC2_USEAST_REGION_NAME;
    public static final String AWS_EC2_LOCATION_SPEC = "jclouds:" + AWS_EC2_PROVIDER + ":" + AWS_EC2_REGION_NAME;

    private ClassLoader classLoader = getClass().getClassLoader();
    private File mementoDir;
    private TestApplication origApp;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();

        jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve(AWS_EC2_LOCATION_SPEC);
        jcloudsLocation.config().set(JcloudsLocation.HARDWARE_ID, AWS_EC2_SMALL_HARDWARE_ID);
        
        origApp = TestApplication.Factory.newManagedInstanceForTests(managementContext);
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
        }
    }
    
    @Override
    protected LocalManagementContext newManagementContext() {
        mementoDir = Files.createTempDir();
        return RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
    }
    
    @Test(groups="Live")
    public void testRebindsToJcloudsMachine() throws Exception {
        LiveTestEntity origEntity = origApp.addChild(EntitySpec.create(LiveTestEntity.class));

        origApp.start(ImmutableList.of(jcloudsLocation));
        JcloudsLocation origJcloudsLocation = jcloudsLocation;
        System.out.println("orig locations: " + origEntity.getLocations());
        JcloudsSshMachineLocation origMachine = (JcloudsSshMachineLocation) Iterables.find(origEntity.getLocations(), Predicates.instanceOf(JcloudsSshMachineLocation.class));

        TestApplication newApp = rebind();
        LiveTestEntity newEntity = (LiveTestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(LiveTestEntity.class));
        JcloudsSshMachineLocation newMachine = (JcloudsSshMachineLocation) Iterables.find(newEntity.getLocations(), Predicates.instanceOf(JcloudsSshMachineLocation.class));

        assertMachineEquals(newMachine, origMachine, true); // Don't expect OsDetails, because that is not persisted.
        assertTrue(newMachine.isSshable());

        JcloudsLocation newJcloudsLoction = newMachine.getParent();
        assertJcloudsLocationEquals(newJcloudsLoction, origJcloudsLocation);
    }

    // TODO In jclouds-azure, the AzureComputeTemplateOptions fields changed, which meant old 
    // persisted state could not be deserialized. These files are examples of the old format.
    @Test(groups={"Live", "WIP"}, enabled=false)
    public void testRebindsToJcloudsMachineWithInvalidTemplate() throws Exception {
        ResourceUtils resourceUtils = ResourceUtils.create(this);
        FileUtils.write(
                new File(mementoDir, "locations/briByOel"),
                resourceUtils.getResourceAsString("classpath://org/apache/brooklyn/location/jclouds/persisted-azure-parent-briByOel"));
        FileUtils.write(
                new File(mementoDir, "locations/VNapYjwp"),
                resourceUtils.getResourceAsString("classpath://org/apache/brooklyn/location/jclouds/persisted-azure-machine-VNapYjwp"));
        
        TestApplication newApp = rebind();
        
        JcloudsLocation loc = (JcloudsLocation) newApp.getManagementContext().getLocationManager().getLocation("briByOel");
        JcloudsSshMachineLocation machine = (JcloudsSshMachineLocation) newApp.getManagementContext().getLocationManager().getLocation("VNapYjwp");
        assertEquals(ImmutableSet.of(loc.getChildren()), ImmutableSet.of(machine));
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testRebindsToJcloudsSshMachineWithTemplateAndNode() throws Exception {
        // Populate the mementoDir with some old-style peristed state
        ResourceUtils resourceUtils = ResourceUtils.create(this);
        String origParentXml = resourceUtils.getResourceAsString("classpath://org/apache/brooklyn/location/jclouds/persisted-aws-parent-lCYB3mTb");
        String origMachineXml = resourceUtils.getResourceAsString("classpath://org/apache/brooklyn/location/jclouds/persisted-aws-machine-aKEcbxKN");
        File persistedParentFile = new File(mementoDir, "locations/lCYB3mTb");
        File persistedMachineFile = new File(mementoDir, "locations/aKEcbxKN");
        FileUtils.write(
                persistedParentFile,
                origParentXml);
        FileUtils.write(
                persistedMachineFile,
                origMachineXml);

        assertTrue(origMachineXml.contains("AWSEC2TemplateOptions"), origMachineXml);
        assertTrue(origMachineXml.contains("NodeMetadataImpl"), origMachineXml);

        // Rebind to the old-style persisted state, which includes the NodeMetadata and the Template objects.
        // Expect to parse that ok.
        TestApplication app2 = rebind();
        
        JcloudsLocation loc2 = (JcloudsLocation) app2.getManagementContext().getLocationManager().getLocation("lCYB3mTb");
        JcloudsSshMachineLocation machine2 = (JcloudsSshMachineLocation) app2.getManagementContext().getLocationManager().getLocation("aKEcbxKN");
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), ImmutableSet.of(machine2));
        
        String errmsg = "loc="+loc2.toVerboseString()+"; machine="+machine2.toVerboseString();
        assertEquals(machine2.getId(), "aKEcbxKN", errmsg);
        assertEquals(machine2.getJcloudsId(), "ap-southeast-1/i-56fd53f2", errmsg);
        assertEquals(machine2.getSshHostAndPort(), HostAndPort.fromParts("ec2-54-254-23-53.ap-southeast-1.compute.amazonaws.com", 22), errmsg);
        assertEquals(machine2.getPrivateAddresses(), ImmutableSet.of("10.144.66.5"), errmsg);
        assertEquals(machine2.getPublicAddresses(), ImmutableSet.of("54.254.23.53"), errmsg);
        assertEquals(machine2.getPrivateAddress(), Optional.of("10.144.66.5"), errmsg);
        assertEquals(machine2.getHostname(), "ip-10-144-66-5", errmsg); // TODO would prefer the hostname that works inside and out
        assertEquals(machine2.getOsDetails().isWindows(), false, errmsg);
        assertEquals(machine2.getOsDetails().isLinux(), true, errmsg);
        assertEquals(machine2.getOsDetails().isMac(), false, errmsg);
        assertEquals(machine2.getOsDetails().getName(), "centos", errmsg);
        assertEquals(machine2.getOsDetails().getArch(), "x86_64", errmsg);
        assertEquals(machine2.getOsDetails().getVersion(), "6.5", errmsg);
        assertEquals(machine2.getOsDetails().is64bit(), true, errmsg);

        // Force it to be persisted again. Expect to pesist without the NodeMetadata and Template.
        app2.getManagementContext().getRebindManager().getChangeListener().onChanged(loc2);
        app2.getManagementContext().getRebindManager().getChangeListener().onChanged(machine2);
        RebindTestUtils.waitForPersisted(app2);
        
        String newMachineXml = new String(java.nio.file.Files.readAllBytes(persistedMachineFile.toPath()));
        assertFalse(newMachineXml.contains("AWSEC2TemplateOptions"), newMachineXml);
        assertFalse(newMachineXml.contains("NodeMetadataImpl"), newMachineXml);
        
        // Rebind again, with the re-written persisted state.
        TestApplication app3 = rebind();
        
        JcloudsLocation loc3 = (JcloudsLocation) app3.getManagementContext().getLocationManager().getLocation("lCYB3mTb");
        JcloudsSshMachineLocation machine3 = (JcloudsSshMachineLocation) app3.getManagementContext().getLocationManager().getLocation("aKEcbxKN");
        assertEquals(ImmutableSet.copyOf(loc3.getChildren()), ImmutableSet.of(machine3));
        
        errmsg = "loc="+loc3.toVerboseString()+"; machine="+machine3.toVerboseString();
        assertEquals(machine3.getId(), "aKEcbxKN", errmsg);
        assertEquals(machine3.getJcloudsId(), "ap-southeast-1/i-56fd53f2", errmsg);
        assertEquals(machine3.getSshHostAndPort(), HostAndPort.fromParts("ec2-54-254-23-53.ap-southeast-1.compute.amazonaws.com", 22), errmsg);
        assertEquals(machine3.getPrivateAddresses(), ImmutableSet.of("10.144.66.5"), errmsg);
        assertEquals(machine3.getPublicAddresses(), ImmutableSet.of("54.254.23.53"), errmsg);
        assertEquals(machine3.getPrivateAddress(), Optional.of("10.144.66.5"), errmsg);
        assertEquals(machine3.getHostname(), "ip-10-144-66-5", errmsg); // TODO would prefer the hostname that works inside and out
        
        // The VM is no longer running, so won't be able to infer OS Details.
        assertFalse(machine3.getOptionalOsDetails().isPresent(), errmsg);
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testRebindsToJcloudsWinrmMachineWithTemplateAndNode() throws Exception {
        // Populate the mementoDir with some old-style peristed state
        ResourceUtils resourceUtils = ResourceUtils.create(this);
        String origParentXml = resourceUtils.getResourceAsString("classpath://org/apache/brooklyn/location/jclouds/persisted-aws-winrm-parent-fKc0Ofyn");
        String origMachineXml = resourceUtils.getResourceAsString("classpath://org/apache/brooklyn/location/jclouds/persisted-aws-winrm-machine-KYSryzW8");
        File persistedParentFile = new File(mementoDir, "locations/fKc0Ofyn");
        File persistedMachineFile = new File(mementoDir, "locations/KYSryzW8");
        FileUtils.write(
                persistedParentFile,
                origParentXml);
        FileUtils.write(
                persistedMachineFile,
                origMachineXml);

        assertTrue(origMachineXml.contains("NodeMetadataImpl"), origMachineXml);

        // Rebind to the old-style persisted state, which includes the NodeMetadata and the Template objects.
        // Expect to parse that ok.
        TestApplication app2 = rebind();
        
        JcloudsLocation loc2 = (JcloudsLocation) app2.getManagementContext().getLocationManager().getLocation("fKc0Ofyn");
        JcloudsWinRmMachineLocation machine2 = (JcloudsWinRmMachineLocation) app2.getManagementContext().getLocationManager().getLocation("KYSryzW8");
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), ImmutableSet.of(machine2));
        
        String errmsg = "loc="+loc2.toVerboseString()+"; machine="+machine2.toVerboseString();
        assertEquals(machine2.getId(), "KYSryzW8", errmsg);
        assertEquals(machine2.getJcloudsId(), "eu-central-1/i-372eda8a", errmsg);
        assertEquals(machine2.getAddress().getHostAddress(), "52.28.153.46", errmsg);
        assertEquals(machine2.getPort(), 5985, errmsg);
        // FIXME assertEquals(machine2.getAddress().getHostAddress(), HostAndPort.fromParts("ec2-52-28-153-46.eu-central-1.compute.amazonaws.com", 22), errmsg);
        assertEquals(machine2.getPrivateAddresses(), ImmutableSet.of("172.31.18.175"), errmsg);
        assertEquals(machine2.getPublicAddresses(), ImmutableSet.of("52.28.153.46"), errmsg);
        assertEquals(machine2.getPrivateAddress(), Optional.of("172.31.18.175"), errmsg);
        assertEquals(machine2.getHostname(), "ip-172-31-18-175", errmsg); // TODO would prefer the hostname that works inside and out
        assertNull(machine2.getOsDetails(), errmsg); // JcloudsWinRmMachineLocation never had OsDetails

        // Force it to be persisted again. Expect to pesist without the NodeMetadata and Template.
        app2.getManagementContext().getRebindManager().getChangeListener().onChanged(loc2);
        app2.getManagementContext().getRebindManager().getChangeListener().onChanged(machine2);
        RebindTestUtils.waitForPersisted(app2);
        
        String newMachineXml = new String(java.nio.file.Files.readAllBytes(persistedMachineFile.toPath()));
        assertFalse(newMachineXml.contains("NodeMetadataImpl"), newMachineXml);
        
        // Rebind again, with the re-written persisted state.
        TestApplication app3 = rebind();
        
        JcloudsLocation loc3 = (JcloudsLocation) app3.getManagementContext().getLocationManager().getLocation("fKc0Ofyn");
        JcloudsWinRmMachineLocation machine3 = (JcloudsWinRmMachineLocation) app3.getManagementContext().getLocationManager().getLocation("KYSryzW8");
        assertEquals(ImmutableSet.copyOf(loc3.getChildren()), ImmutableSet.of(machine3));
        
        errmsg = "loc="+loc3.toVerboseString()+"; machine="+machine3.toVerboseString();
        assertEquals(machine3.getId(), "KYSryzW8", errmsg);
        assertEquals(machine3.getJcloudsId(), "eu-central-1/i-372eda8a", errmsg);
        assertEquals(machine3.getAddress().getHostAddress(), "52.28.153.46", errmsg);
        assertEquals(machine3.getPort(), 5985, errmsg);
        assertEquals(machine3.getPrivateAddresses(), ImmutableSet.of("172.31.18.175"), errmsg);
        assertEquals(machine3.getPublicAddresses(), ImmutableSet.of("52.28.153.46"), errmsg);
        assertEquals(machine3.getPrivateAddress(), Optional.of("172.31.18.175"), errmsg);
        assertEquals(machine3.getHostname(), "ip-172-31-18-175", errmsg); // TODO would prefer the hostname that works inside and out
        assertNull(machine2.getOsDetails(), errmsg); // JcloudsWinRmMachineLocation never had OsDetails
    }

    private void assertMachineEquals(JcloudsSshMachineLocation actual, JcloudsSshMachineLocation expected, boolean expectNoOsDetails) {
        String errmsg = "actual="+actual.toVerboseString()+"; expected="+expected.toVerboseString();
        assertEquals(actual.getId(), expected.getId(), errmsg);
        assertEquals(actual.getJcloudsId(), expected.getJcloudsId(), errmsg);
        if (expectNoOsDetails) {
            assertOsDetailEquals(actual.getOptionalOsDetails(), Optional.<OsDetails>absent());
        } else {
            assertOsDetailEquals(actual.getOptionalOsDetails(), expected.getOptionalOsDetails());
        }
        assertEquals(actual.getSshHostAndPort(), expected.getSshHostAndPort());
        assertEquals(actual.getPrivateAddress(), expected.getPrivateAddress());
        assertConfigBagEquals(actual.config().getBag(), expected.config().getBag(), errmsg);
    }

    private void assertOsDetailEquals(Optional<OsDetails> actual, Optional<OsDetails> expected) {
        String errmsg = "actual="+actual+"; expected="+expected;
        if (actual.isPresent()) {
            assertEquals(actual.get().isWindows(), expected.get().isWindows());
            assertEquals(actual.get().isLinux(), expected.get().isLinux());
            assertEquals(actual.get().isMac(), expected.get().isMac());
            assertEquals(actual.get().getName(), expected.get().getName());
            assertEquals(actual.get().getArch(), expected.get().getArch());
            assertEquals(actual.get().getVersion(), expected.get().getVersion());
            assertEquals(actual.get().is64bit(), expected.get().is64bit());
        } else {
            assertFalse(expected.isPresent(), errmsg);
        }
    }

    private void assertJcloudsLocationEquals(JcloudsLocation actual, JcloudsLocation expected) {
        String errmsg = "actual="+actual.toVerboseString()+"; expected="+expected.toVerboseString();
        assertEquals(actual.getId(), expected.getId(), errmsg);
        assertEquals(actual.getProvider(), expected.getProvider(), errmsg);
        assertEquals(actual.getRegion(), expected.getRegion(), errmsg);
        assertEquals(actual.getIdentity(), expected.getIdentity(), errmsg);
        assertEquals(actual.getCredential(), expected.getCredential(), errmsg);
        assertEquals(actual.getHostGeoInfo(), expected.getHostGeoInfo(), errmsg);
        assertConfigBagEquals(actual.config().getBag(), expected.config().getBag(), errmsg);
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
        return rebind(RebindOptions.create()
                .mementoDir(mementoDir)
                .classLoader(classLoader));
    }
    
    private TestApplication rebind(RebindOptions options) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(options);
    }
}
