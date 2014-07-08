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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.SingleMachineProvisioningLocation;

public class SingleMachineProvisioningLocationJcloudsLiveTest extends AbstractJcloudsTest {
private static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);
    
    private SingleMachineProvisioningLocation<JcloudsSshMachineLocation> location;

    private static final String JCLOUDS_LOCATION_SPEC = "jclouds:" + AWS_EC2_PROVIDER + ":" + AWS_EC2_USEAST_REGION_NAME;
    
    @Test(groups="Live")
    public void testJcloudsSingle() throws Exception {
        location = resolve("single:(target='"+JCLOUDS_LOCATION_SPEC+"')");
        
        MachineLocation m1 = obtainMachine();
        assertNotNull(m1);

        log.info("GOT "+m1);
    }
    
    @Test(groups="Live")
    public void testJcloudsSingleRelease() throws Exception {
        location = resolve("single:(target='"+JCLOUDS_LOCATION_SPEC+"')");
        
        JcloudsSshMachineLocation m1 = obtainMachine();
        log.info("GOT " + m1);
        JcloudsSshMachineLocation m2 = obtainMachine();
        log.info("GOT " + m2);
        assertSame(m1, m2);
        
        location.release(m1);
        assertTrue(m2.isSshable());

        location.release(m2);
        assertFalse(m2.isSshable());
    }
    
    @Test(groups="Live")
    public void testJcloudsSingleObtainReleaseObtain() throws Exception {
        location = resolve("single:(target='"+JCLOUDS_LOCATION_SPEC+"')");
        
        JcloudsSshMachineLocation m1 = obtainMachine();
        log.info("GOT " + m1);
        
        location.release(m1);
        assertFalse(m1.isSshable());
        
        JcloudsSshMachineLocation m2 = obtainMachine();
        assertTrue(m2.isSshable());
        assertNotEquals(m1, m2);
        
        location.release(m2);
        assertFalse(m2.isSshable());
    }
    
    @Test(groups="Live")
    public void testJCloudsNamedSingle() throws Exception {
        brooklynProperties.put(ConfigKeys.newStringConfigKey("brooklyn.location.named.FooServers"), JCLOUDS_LOCATION_SPEC);
        location = resolve("single:(target='named:FooServers')");
        
        JcloudsSshMachineLocation m1 = obtainMachine();
        assertTrue(m1.isSshable());
        
        location.release(m1);
        assertFalse(m1.isSshable());
    }
    
    @Override
    protected JcloudsSshMachineLocation obtainMachine(Map<?, ?> conf) throws Exception {
        JcloudsSshMachineLocation result = location.obtain(conf);
        machines.add(result);
        return result;
    }
    
    @Override
    protected void releaseMachine(JcloudsSshMachineLocation machine) {
        if (location.getChildren().contains(machine)) {
            machines.remove(machine);
            location.release(machine);
        }
    }

    @SuppressWarnings("unchecked")
    private SingleMachineProvisioningLocation<JcloudsSshMachineLocation> resolve(String spec) {
        SingleMachineProvisioningLocation<JcloudsSshMachineLocation> result = (SingleMachineProvisioningLocation<JcloudsSshMachineLocation>) 
                managementContext.getLocationRegistry().resolve(spec);
        // FIXME Do we really need to setManagementContext?!
        //result.setManagementContext(managementContext);
        return result;
    }
}
