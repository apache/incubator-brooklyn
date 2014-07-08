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

import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;

import org.jclouds.domain.LoginCredentials;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;

import com.google.common.net.HostAndPort;

public class RebindJcloudsLocationTest extends RebindTestFixtureWithApp {

    public static final String LOC_SPEC = "jclouds:aws-ec2:us-east-1";

    private JcloudsLocation origLoc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        origLoc = (JcloudsLocation) origManagementContext.getLocationRegistry().resolve(LOC_SPEC);
    }

    // Previously, the rebound config contained "id" which was then passed to createTemporarySshMachineLocation, causing
    // that to fail (because the LocationSpec should not have had "id" in its config)
    @Test
    public void testReboundConfigDoesNotContainId() throws Exception {
        rebind();
        
        JcloudsLocation newLoc = (JcloudsLocation) newManagementContext.getLocationManager().getLocation(origLoc.getId());
        
        ConfigBag newLocConfig = newLoc.getAllConfigBag();
        ConfigBag config = ConfigBag.newInstanceCopying(newLocConfig);
        
        assertNull(newLocConfig.getStringKey(("id")));
        
        SshMachineLocation tempMachine = newLoc.createTemporarySshMachineLocation(
                HostAndPort.fromParts("localhost", 1234), 
                LoginCredentials.builder().identity("myuser").password("mypass").noPrivateKey().build(), 
                config);
        assertNotEquals(tempMachine.getId(), newLoc.getId());
    }
}
