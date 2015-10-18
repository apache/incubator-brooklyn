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

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.exceptions.CompoundRuntimeException;
import org.apache.brooklyn.util.stream.Streams;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Tests rebind (i.e. restarting Brooklyn server) when there are live JcloudsSshMachineLocation object(s),
 * created using the JcloudsByonLocationResolver.
 */
public class JcloudsByonRebindLiveTest extends RebindTestFixtureWithApp {

    // TODO Duplication of AbstractJcloudsLiveTest, because we're subclassing RebindTestFixture instead.

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsByonRebindLiveTest.class);

    public static final String SOFTLAYER_PROVIDER = AbstractJcloudsLiveTest.SOFTLAYER_PROVIDER;
    public static final String SOFTLAYER_REGION = AbstractJcloudsLiveTest.SOFTLAYER_AMS01_REGION_NAME;
    public static final String SOFTLAYER_LOCATION_SPEC = "jclouds:" + SOFTLAYER_PROVIDER + ":" + SOFTLAYER_REGION;
    public static final String SOFTLAYER_IMAGE_ID = "UBUNTU_14_64";

    private LocalManagementContext provisioningManagementContext;
    private JcloudsSshMachineLocation provisionedMachine;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        
        // For provisioning VMs (to subsequently be used for BYON)
        provisioningManagementContext = LocalManagementContextForTests.builder(true).useDefaultProperties().build();
        AbstractJcloudsLiveTest.stripBrooklynProperties(provisioningManagementContext.getBrooklynProperties());
        
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        AbstractJcloudsLiveTest.stripBrooklynProperties(origManagementContext.getBrooklynProperties());
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        List<Exception> exceptions = Lists.newArrayList();
        try {
            if (provisioningManagementContext != null && provisionedMachine != null) {
                provisionedMachine.getParent().release(provisionedMachine);
            }
        } finally {
            Entities.destroyAll(provisioningManagementContext);
            provisioningManagementContext = null;
            provisionedMachine = null;
            
            super.tearDown();
        }
        
        if (exceptions.size() > 0) {
            throw new CompoundRuntimeException("Error in tearDown of "+getClass(), exceptions);
        }
    }

    @Override
    protected boolean useLiveManagementContext() {
        return true;
    }

    @Test(groups = {"Live"})
    public void testRebind() throws Exception {
        ImmutableMap<String, Object> obtainFlags = ImmutableMap.<String,Object>builder()
                .put("imageId", SOFTLAYER_IMAGE_ID)
                .put("inboundPorts", ImmutableList.of(22))
                .build();
        JcloudsLocation provisioningLoc = (JcloudsLocation) provisioningManagementContext.getLocationRegistry().resolve(SOFTLAYER_LOCATION_SPEC);
        provisionedMachine = (JcloudsSshMachineLocation) provisioningLoc.obtain(obtainFlags);
        
        // Test with a jclouds-byon
        String locSpec = "jcloudsByon:(provider=\""+SOFTLAYER_PROVIDER+"\",region=\""+SOFTLAYER_REGION+"\",user=\""+provisionedMachine.getUser()+"\",hosts=\""+provisionedMachine.getNode().getProviderId()+"\")";
        
        FixedListMachineProvisioningLocation<?> origByon = (FixedListMachineProvisioningLocation<?>) mgmt().getLocationRegistry().resolve(locSpec);
        
        JcloudsSshMachineLocation origMachine = (JcloudsSshMachineLocation)origByon.obtain(ImmutableMap.<String,Object>of());
        JcloudsLocation origJcloudsLocation = origMachine.getParent();
        String origHostname = origMachine.getHostname();
        NodeMetadata origNode = origMachine.getNode();
        Template origTemplate = origMachine.getTemplate();
        assertSshable(origMachine);

        rebind();
        
        // Check the machine is as before
        JcloudsSshMachineLocation newMachine = (JcloudsSshMachineLocation) newManagementContext.getLocationManager().getLocation(origMachine.getId());
        FixedListMachineProvisioningLocation<?> newByon = (FixedListMachineProvisioningLocation<?>) newManagementContext.getLocationManager().getLocation(origByon.getId());
        JcloudsLocation newJcloudsLocation = newMachine.getParent();
        String newHostname = newMachine.getHostname();
        NodeMetadata newNode = newMachine.getNode();
        Template newTemplate = newMachine.getTemplate();
        assertSshable(newMachine);
        
        assertEquals(newHostname, origHostname);
        assertEquals(origNode.getId(), newNode.getId());
        
        assertEquals(newJcloudsLocation.getProvider(), origJcloudsLocation.getProvider());
    }
    
    protected void assertSshable(Map<?,?> machineConfig) {
        SshMachineLocation machineWithThatConfig = mgmt().getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure(machineConfig));
        try {
            assertSshable(machineWithThatConfig);
        } finally {
            Streams.closeQuietly(machineWithThatConfig);
        }
    }

    protected void assertNotSshable(Map<?,?> machineConfig) {
        try {
            assertSshable(machineConfig);
            Assert.fail("ssh should not have succeeded "+machineConfig);
        } catch (Exception e) {
            // expected
            LOG.debug("Exception as expected when testing sshable "+machineConfig);
        }
    }

    protected void assertSshable(SshMachineLocation machine) {
        int result = machine.execScript("simplecommand", ImmutableList.of("true"));
        assertEquals(result, 0);
    }
}
