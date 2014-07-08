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
package brooklyn.entity.chef;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.util.ResourceUtils;
import brooklyn.util.stream.InputStreamSupplier;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public class ChefLiveTestSupport extends BrooklynAppLiveTestSupport {

    private static final Logger log = LoggerFactory.getLogger(ChefLiveTestSupport.class);
    
    protected MachineProvisioningLocation<? extends SshMachineLocation> targetLocation;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        targetLocation = createLocation();
    }

    protected MachineProvisioningLocation<? extends SshMachineLocation> createLocation() {
        return createLocation(mgmt);
    }
    
    /** convenience for setting up a pre-built / fixed IP machine
     * (because you might not want to set up Chef on localhost) 
     * and ensuring tests against Chef use the same configured location 
     **/
    @SuppressWarnings("unchecked")
    public static MachineProvisioningLocation<? extends SshMachineLocation> createLocation(ManagementContext mgmt) {
        Location bestLocation = mgmt.getLocationRegistry().resolveIfPossible("named:ChefTests");
        if (bestLocation==null) {
            log.info("using AWS for chef tests because named:ChefTests does not exist");
            bestLocation = mgmt.getLocationRegistry().resolveIfPossible("jclouds:aws-ec2:us-east-1");
        }
        if (bestLocation==null) {
            throw new IllegalStateException("Need a location called named:ChefTests or AWS configured for these tests");
        }
        return (MachineProvisioningLocation<? extends SshMachineLocation>)bestLocation; 
    }
    
    private static String defaultConfigFile = null; 
    public synchronized static String installBrooklynChefHostedConfig() {
        if (defaultConfigFile!=null) return defaultConfigFile;
        File tempDir = Files.createTempDir();
        ResourceUtils r = ResourceUtils.create(ChefServerTasksIntegrationTest.class);
        try {
            for (String f: new String[] { "knife.rb", "brooklyn-tests.pem", "brooklyn-validator.pem" }) {
                Files.copy(InputStreamSupplier.fromString(r.getResourceAsString(
                        "classpath:///brooklyn/entity/chef/hosted-chef-brooklyn-credentials/"+f)),
                        new File(tempDir, f));
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        File knifeConfig = new File(tempDir, "knife.rb");
        defaultConfigFile = knifeConfig.getPath();
        return defaultConfigFile;
    }

    public static void installBrooklynChefHostedConfig(Entity entity) {
        ((EntityInternal)entity).setConfig(ChefConfig.KNIFE_CONFIG_FILE, ChefLiveTestSupport.installBrooklynChefHostedConfig());
    }

}
