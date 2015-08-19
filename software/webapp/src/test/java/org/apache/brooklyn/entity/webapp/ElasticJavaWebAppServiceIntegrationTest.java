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
package org.apache.brooklyn.entity.webapp;

import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.core.Entities;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.collect.ImmutableList;

public class ElasticJavaWebAppServiceIntegrationTest {

    private LocalhostMachineProvisioningLocation loc;
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        loc = app.newLocalhostProvisioningLocation();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }

    @SuppressWarnings("deprecation")
    @Test(groups = "Integration")
    // TODO a new approach to what ElasticJavaWebAppService.Factory does, giving a different entity depending on location!
    public void testLegacyFactory() {
        ElasticJavaWebAppService svc =
            new ElasticJavaWebAppService.Factory().newEntity(MutableMap.of("war", getTestWar()), app);
        Entities.manage(svc);
        app.start(ImmutableList.of(loc));
        
        String url = svc.getAttribute(ElasticJavaWebAppService.ROOT_URL);
        Assert.assertNotNull(url);
        HttpTestUtils.assertContentEventuallyContainsText(url, "Hello");
    }
}
