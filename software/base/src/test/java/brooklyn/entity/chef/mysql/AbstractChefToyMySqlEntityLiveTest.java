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
package brooklyn.entity.chef.mysql;

import org.testng.annotations.Test;

import brooklyn.entity.chef.ChefLiveTestSupport;
import brooklyn.entity.software.mysql.AbstractToyMySqlEntityTest;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;

public abstract class AbstractChefToyMySqlEntityLiveTest extends AbstractToyMySqlEntityTest {

    @Override
    // mark as live here
    @Test(groups = "Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }
    
    protected MachineProvisioningLocation<? extends SshMachineLocation> createLocation() {
        return ChefLiveTestSupport.createLocation(mgmt);
    }
    
}
