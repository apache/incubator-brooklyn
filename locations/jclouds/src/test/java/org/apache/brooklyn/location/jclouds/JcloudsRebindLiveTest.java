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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
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
 * Tests rebind (i.e. restarting Brooklyn server) when there are live JcloudsSshMachineLocation object(s).
 */
public class JcloudsRebindLiveTest extends RebindTestFixtureWithApp {

    // TODO Duplication of AbstractJcloudsLiveTest, because we're subcalling RebindTestFixture instead.

    // TODO The mgmts tracking was added when I tried to combine JcloudsRebindLiveTest and JcloudsByonRebindLiveTest, 
    // but turns out that is not worth the effort!
    
    private static final Logger LOG = LoggerFactory.getLogger(JcloudsRebindLiveTest.class);

    public static final String AWS_EC2_REGION_NAME = AbstractJcloudsLiveTest.AWS_EC2_USEAST_REGION_NAME;
    public static final String AWS_EC2_LOCATION_SPEC = "jclouds:" + AbstractJcloudsLiveTest.AWS_EC2_PROVIDER + (AWS_EC2_REGION_NAME == null ? "" : ":" + AWS_EC2_REGION_NAME);
    
    // Image: {id=us-east-1/ami-7d7bfc14, providerId=ami-7d7bfc14, name=RightImage_CentOS_6.3_x64_v5.8.8.5, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=centos, arch=paravirtual, version=6.0, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, is64Bit=true}, description=rightscale-us-east/RightImage_CentOS_6.3_x64_v5.8.8.5.manifest.xml, version=5.8.8.5, status=AVAILABLE[available], loginUser=root, userMetadata={owner=411009282317, rootDeviceType=instance-store, virtualizationType=paravirtual, hypervisor=xen}}
    public static final String AWS_EC2_CENTOS_IMAGE_ID = "us-east-1/ami-7d7bfc14";

    public static final String SOFTLAYER_LOCATION_SPEC = "jclouds:" + AbstractJcloudsLiveTest.SOFTLAYER_PROVIDER;
    
    protected List<JcloudsSshMachineLocation> machines;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        machines = Lists.newCopyOnWriteArrayList();
        
        // Don't let any defaults from brooklyn.properties (except credentials) interfere with test
        AbstractJcloudsLiveTest.stripBrooklynProperties(origManagementContext.getBrooklynProperties());
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        List<Exception> exceptions = Lists.newArrayList();
        try {
            exceptions.addAll(releaseMachineSafely(machines));
            machines.clear();
        } finally {
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
    public void testEc2Rebind() throws Exception {
        ImmutableMap<String, Object> obtainFlags = ImmutableMap.<String,Object>builder()
                .put("imageId", AWS_EC2_CENTOS_IMAGE_ID)
                .put("hardwareId", AbstractJcloudsLiveTest.AWS_EC2_SMALL_HARDWARE_ID)
                .put("inboundPorts", ImmutableList.of(22))
                .build();
        runTest(AWS_EC2_LOCATION_SPEC, obtainFlags);
    }
    
    @Test(groups = {"Live"})
    public void testSoftlayerRebind() throws Exception {
        runTest(SOFTLAYER_LOCATION_SPEC, ImmutableMap.of("inboundPorts", ImmutableList.of(22)));
    }
    
    protected void runTest(String locSpec, Map<String, ?> obtainFlags) throws Exception {
        JcloudsLocation location = (JcloudsLocation) mgmt().getLocationRegistry().resolve(locSpec);
        
        JcloudsSshMachineLocation origMachine = obtainMachine(location, obtainFlags);
        String origHostname = origMachine.getHostname();
        NodeMetadata origNode = origMachine.getNode();
        Template origTemplate = origMachine.getTemplate();
        assertSshable(origMachine);

        rebind();
        
        // Check the machine is as before
        JcloudsSshMachineLocation newMachine = (JcloudsSshMachineLocation) newManagementContext.getLocationManager().getLocation(origMachine.getId());
        JcloudsLocation newLocation = newMachine.getParent();
        String newHostname = newMachine.getHostname();
        NodeMetadata newNode = newMachine.getNode();
        Template newTemplate = newMachine.getTemplate();
        assertSshable(newMachine);
        
        assertEquals(newHostname, origHostname);
        assertEquals(origNode.getId(), newNode.getId());
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

    // Use this utility method to ensure machines are released on tearDown
    protected JcloudsSshMachineLocation obtainMachine(MachineProvisioningLocation<?> location, Map<?, ?> conf) throws Exception {
        JcloudsSshMachineLocation result = (JcloudsSshMachineLocation)location.obtain(conf);
        machines.add(checkNotNull(result, "result"));
        return result;
    }

    protected void releaseMachine(JcloudsSshMachineLocation machine) {
        if (!Locations.isManaged(machine)) return;
        machines.remove(machine);
        machine.getParent().release(machine);
    }
    
    protected List<Exception> releaseMachineSafely(Iterable<? extends JcloudsSshMachineLocation> machines) {
        List<Exception> exceptions = Lists.newArrayList();
        List<JcloudsSshMachineLocation> machinesCopy = ImmutableList.copyOf(machines);
        
        for (JcloudsSshMachineLocation machine : machinesCopy) {
            try {
                releaseMachine(machine);
            } catch (Exception e) {
                LOG.warn("Error releasing machine "+machine+"; continuing...", e);
                exceptions.add(e);
            }
        }
        return exceptions;
    }
}
