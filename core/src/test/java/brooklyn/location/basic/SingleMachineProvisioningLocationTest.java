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
package brooklyn.location.basic;

import static org.testng.Assert.assertNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.internal.LocalManagementContext;

public class SingleMachineProvisioningLocationTest {
    
    private static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);
    
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) managementContext.terminate();
    }
    
    @SuppressWarnings("unchecked") 
    @Test
    public void testLocalhostSingle() throws Exception {
        SingleMachineProvisioningLocation<SshMachineLocation> l = (SingleMachineProvisioningLocation<SshMachineLocation>) 
            managementContext.getLocationRegistry().resolve("single:(target='localhost')");
        l.setManagementContext(managementContext);
        
        SshMachineLocation m1 = l.obtain();
        
        assertNotNull(m1);

        log.info("GOT "+m1);
        
        l.release(m1);
    }
    
}
