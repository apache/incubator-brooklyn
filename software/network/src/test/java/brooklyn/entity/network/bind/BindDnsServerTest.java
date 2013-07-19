/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.network.bind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

public class BindDnsServerTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerTest.class);

    protected TestApplication app;
    protected Location testLocation;
    protected BindDnsServer dns;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
    }

    // TODO this needs to be run on a slave VM where we can edit the resolver configuration
    @Test(groups = { "WIP", "Integration" })
    protected void testDnsEntity() throws Exception {
        BindDnsServer dns = app.createAndManageChild(EntitySpecs.spec(BindDnsServer.class));

        app.start(ImmutableList.<Location>of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(dns, BindDnsServer.SERVICE_UP, true);
    }

}
