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
import static org.testng.Assert.assertTrue;

import java.util.Set;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class JcloudsByonLocationResolverSoftlayerLiveTest extends AbstractJcloudsLiveTest {

    private static final String SOFTLAYER_REGION = "dal05";
    private static final String SOFTLAYER_LOCATION_SPEC = "jclouds:softlayer:"+SOFTLAYER_REGION;
    
    private String slVmUser;
    private String slVmInstanceId;
    private String slVmIp;
    private String slVmHostname;
    
    private LocalManagementContext classManagementContext;
    private JcloudsLocation classEc2Loc;
    private JcloudsSshMachineLocation classVm;

    @BeforeClass(groups="Live")
    public void setUpClass() throws Exception {
        classManagementContext = newManagementContext();
        classEc2Loc = (JcloudsLocation) classManagementContext.getLocationRegistry().resolve(SOFTLAYER_LOCATION_SPEC);
        classVm = classEc2Loc.obtain(MutableMap.<String,Object>builder()
                .put("inboundPorts", ImmutableList.of(22))
                .build());
        slVmUser = classVm.getUser();
        slVmInstanceId = classVm.getJcloudsId();
        slVmIp = classVm.getAddress().getHostAddress();
        slVmHostname = classVm.getNode().getHostname();
    }
    
    @AfterClass(alwaysRun=true)
    public void tearDownClass() throws Exception {
        try {
            if (classVm != null) {
                classEc2Loc.release(classVm);
            }
        } finally {
            if (classManagementContext != null) classManagementContext.terminate();
        }
    }

    @Test(groups={"Live"})
    public void testResolvesJcloudsByonSoftlayer() throws Exception {
        checkSoftlayer("jcloudsByon:(provider=\"softlayer\",region=\""+SOFTLAYER_REGION+"\",hosts=\""+slVmInstanceId+"\",user=\""+slVmUser+"\")");
        checkSoftlayer("jcloudsByon:(provider=\"softlayer\",region=\""+SOFTLAYER_REGION+"\",hosts=\""+slVmHostname+"\")");
        checkSoftlayer("jcloudsByon:(provider=\"softlayer\",region=\""+SOFTLAYER_REGION+"\",hosts=\""+slVmIp+"\")");
        checkSoftlayer("jcloudsByon:(provider=\"softlayer\",hosts=\""+slVmIp+"\")");
    }
    
    private void checkSoftlayer(String spec) {
        FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> loc = resolve(spec);
        
        Set<JcloudsSshMachineLocation> machines = loc.getAllMachines();
        JcloudsSshMachineLocation machine = Iterables.getOnlyElement(machines);
        assertEquals(machine.getParent().getProvider(), "softlayer");
        assertEquals(machine.getNode().getId(), slVmInstanceId);
        assertEquals(machine.getAddress().getHostAddress(), slVmIp);
        assertTrue(slVmHostname.equals(machine.getAddress().getHostName()) || slVmIp.equals(machine.getAddress().getHostName()), 
            "address hostname is: "+machine.getAddress().getHostName());
        assertTrue(slVmHostname.equals(machine.getNode().getHostname()) || slVmIp.equals(machine.getNode().getHostname()), 
            "node hostname is: "+machine.getNode().getHostname());
        
        // could also assert this, given a user credential, but not currently set up
//        assertTrue(machine.isSshable());
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> resolve(String spec) {
        return (FixedListMachineProvisioningLocation<JcloudsSshMachineLocation>) managementContext.getLocationRegistry().resolve(spec);
    }
    
}