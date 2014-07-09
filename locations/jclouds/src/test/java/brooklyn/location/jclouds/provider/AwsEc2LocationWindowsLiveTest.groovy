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
package brooklyn.location.jclouds.provider;

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.jclouds.JcloudsLocation
import brooklyn.location.jclouds.JcloudsSshMachineLocation
import brooklyn.management.ManagementContext

import com.google.common.collect.ImmutableMap

public class AwsEc2LocationWindowsLiveTest {
    private static final Logger LOG = LoggerFactory.getLogger(AwsEc2LocationWindowsLiveTest.class)
    
    private static final String PROVIDER = "aws-ec2"
    private static final String EUWEST_REGION_NAME = "eu-west-1" 
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-7f0c260b";//"ami-41d3d635"
    private static final String LOCATION_ID = "jclouds:"+PROVIDER+":"+EUWEST_REGION_NAME;
    
    protected JcloudsLocation loc;
    protected Collection<SshMachineLocation> machines = []
    protected ManagementContext ctx;
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
        ctx = Entities.newManagementContext(ImmutableMap.of("provider", PROVIDER));

        loc = ctx.locationRegistry.resolve LOCATION_ID
    }

    @AfterMethod(groups = "Live")
    public void tearDown() {
        List<Exception> exceptions = []
        machines.each {
            try {
                loc?.release(it)
            } catch (Exception e) {
                LOG.warn("Error releasing machine $it; continuing...", e)
                exceptions.add(e)
            }
        }
        if (exceptions) {
            throw exceptions.get(0)
        }
        machines.clear()
    }
    
    // TODO Note careful choice of image due to jclouds 1.4 issue 886
    // TODO Blocks for long time, waiting for IP:22 to be reachable, before falling back to using public IP
    //      10*2 minutes per attempt in jclouds 1.4 because done sequentially, and done twice by us so test takes 40 minutes!
    @Test(enabled=true, groups = [ "Live" ])
    public void testProvisionWindowsVm() {
        JcloudsSshMachineLocation machine = obtainMachine([ imageId:EUWEST_IMAGE_ID ]);

        LOG.info("Provisioned Windows VM {}; checking if has password", machine)
        assertNotNull(machine.waitForPassword())
    }
    
    // Use this utility method to ensure machines are released on tearDown
    protected SshMachineLocation obtainMachine(Map flags) {
        SshMachineLocation result = loc.obtain(flags)
        machines.add(result)
        return result
    }
    
    protected SshMachineLocation release(SshMachineLocation machine) {
        machines.remove(machine)
        loc.release(machine)
    }
}
