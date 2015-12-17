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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse;
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
    
    protected List<JcloudsMachineLocation> machines;
    
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
                .put("hardwareId", AbstractJcloudsLiveTest.AWS_EC2_MEDIUM_HARDWARE_ID)
                .put("inboundPorts", ImmutableList.of(22))
                .build();
        runTest(AWS_EC2_LOCATION_SPEC, obtainFlags);
    }
    
    @Test(groups = {"Live"})
    public void testEc2WinrmRebind() throws Exception {
        ImmutableMap<String, Object> obtainFlags = ImmutableMap.<String,Object>builder()
                .put("imageNameRegex", "Windows_Server-2012-R2_RTM-English-64Bit-Base-.*")
                .put("imageOwner", "801119661308")
                .put("hardwareId", AbstractJcloudsLiveTest.AWS_EC2_MEDIUM_HARDWARE_ID)
                .put("useJcloudsSshInit", false)
                .put("inboundPorts", ImmutableList.of(5985, 3389))
                .build();
        runTest(AWS_EC2_LOCATION_SPEC, obtainFlags);
    }

    @Test(groups = {"Live"})
    public void testSoftlayerRebind() throws Exception {
        runTest(SOFTLAYER_LOCATION_SPEC, ImmutableMap.of("inboundPorts", ImmutableList.of(22)));
    }
    
    protected void runTest(String locSpec, Map<String, ?> obtainFlags) throws Exception {
        JcloudsLocation location = (JcloudsLocation) mgmt().getLocationRegistry().resolve(locSpec);
        
        JcloudsMachineLocation origMachine = obtainMachine(location, obtainFlags);
        String origHostname = origMachine.getHostname();
        NodeMetadata origNode = origMachine.getNode();
        if (origMachine instanceof JcloudsSshMachineLocation) {
            Template origTemplate = origMachine.getTemplate(); // WinRM machines don't bother with template!
        }
        assertConnectable(origMachine);

        rebind();
        
        // Check the machine is as before; but won't have persisted node+template.
        // We'll be able to re-create the node object by querying the cloud-provider again though.
        JcloudsMachineLocation newMachine = (JcloudsMachineLocation) newManagementContext.getLocationManager().getLocation(origMachine.getId());
        JcloudsLocation newLocation = newMachine.getParent();
        String newHostname = newMachine.getHostname();
        if (newMachine instanceof JcloudsSshMachineLocation) {
            assertFalse(((JcloudsSshMachineLocation)newMachine).getOptionalTemplate().isPresent());
            assertNull(((JcloudsSshMachineLocation)newMachine).peekNode());
        } else if (newMachine instanceof JcloudsWinRmMachineLocation) {
            assertNull(((JcloudsWinRmMachineLocation)newMachine).peekNode());
        } else {
            fail("Unexpected new machine type: machine="+newMachine+"; type="+(newMachine == null ? null : newMachine.getClass()));
        }
        NodeMetadata newNode = newMachine.getOptionalNode().get();
        assertConnectable(newMachine);
        
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

    protected void assertConnectable(MachineLocation machine) {
        if (machine instanceof SshMachineLocation) {
            assertSshable((SshMachineLocation)machine);
        } else if (machine instanceof WinRmMachineLocation) {
            assertWinrmable((WinRmMachineLocation)machine);
        } else {
            throw new UnsupportedOperationException("Unsupported machine type: machine="+machine+"; type="+(machine == null ? null : machine.getClass()));
        }
    }
    
    protected void assertSshable(SshMachineLocation machine) {
        int result = machine.execScript("simplecommand", ImmutableList.of("true"));
        assertEquals(result, 0);
    }

    protected void assertWinrmable(WinRmMachineLocation machine) {
        WinRmToolResponse result = machine.executePsScript("echo mycmd");
        assertEquals(result.getStatusCode(), 0, "status="+result.getStatusCode()+"; stdout="+result.getStdOut()+"; stderr="+result.getStdErr());
    }

    // Use this utility method to ensure machines are released on tearDown
    protected JcloudsMachineLocation obtainMachine(MachineProvisioningLocation<?> location, Map<?, ?> conf) throws Exception {
        JcloudsMachineLocation result = (JcloudsMachineLocation)location.obtain(conf);
        machines.add(checkNotNull(result, "result"));
        return result;
    }

    protected void releaseMachine(JcloudsMachineLocation machine) {
        if (!Locations.isManaged(machine)) return;
        machines.remove(machine);
        machine.getParent().release(machine);
    }
    
    protected List<Exception> releaseMachineSafely(Iterable<? extends JcloudsMachineLocation> machines) {
        List<Exception> exceptions = Lists.newArrayList();
        List<JcloudsMachineLocation> machinesCopy = ImmutableList.copyOf(machines);
        
        for (JcloudsMachineLocation machine : machinesCopy) {
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
