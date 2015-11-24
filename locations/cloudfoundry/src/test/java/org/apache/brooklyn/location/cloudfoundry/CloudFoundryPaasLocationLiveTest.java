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
package org.apache.brooklyn.location.cloudfoundry;

import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class CloudFoundryPaasLocationLiveTest {

    protected final String LOCATION_SPEC_NAME = "cloudfoundry-instance";
    protected CloudFoundryPaasLocation cloudFoundryPaasLocation;
    protected LocalManagementContext managementContext;
    protected BrooklynProperties brooklynProperties;

    @AfterMethod
    public void tearDown() throws Exception {
        if (managementContext != null){
            Entities.destroyAll(managementContext);
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        managementContext = newLocalManagementContext();
        brooklynProperties = new LocalManagementContext().getBrooklynProperties();
        cloudFoundryPaasLocation = newSampleCloudFoundryLocationForTesting(LOCATION_SPEC_NAME);
    }

    @Test(groups = {"Live"})
    public void testClientSetUp() {
        cloudFoundryPaasLocation.setUpClient();
        assertNotNull(cloudFoundryPaasLocation.getCloudFoundryClient());
    }

    @Test(groups = {"Live"})
    public void testClientSetUpPerLocationInstance() {
        cloudFoundryPaasLocation.setUpClient();
        CloudFoundryClient client1 = cloudFoundryPaasLocation.getCloudFoundryClient();
        cloudFoundryPaasLocation.setUpClient();
        CloudFoundryClient client2 = cloudFoundryPaasLocation.getCloudFoundryClient();
        assertEquals(client1, client2);
    }

    protected CloudFoundryPaasLocation newSampleCloudFoundryLocationForTesting(String spec) {
        return (CloudFoundryPaasLocation) managementContext.getLocationRegistry().resolve(spec);
    }

    protected LocalManagementContext newLocalManagementContext() {
        return new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
    }

}
