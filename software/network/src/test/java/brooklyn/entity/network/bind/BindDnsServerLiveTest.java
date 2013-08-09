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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

public class BindDnsServerLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerLiveTest.class);

    protected TestApplication app;
    protected Location testLocation;
    protected BindDnsServer dns;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() throws Exception {
        Entities.destroyAll(app.getManagementContext());
        // Thread.sleep(TimeUnit.MINUTES.toMillis(30));
    }

    @DataProvider(name = "virtualMachineData")
    public Object[][] provideVirtualMachineData() {
        return new Object[][] { // ImageId, Provider, Region
            new Object[] { "", "named:cloudera" },
            new Object[] { "eu-west-1/ami-029f9476", "aws-ec2:eu-west-1" },
        };
    }

    @Test(groups = "Live", dataProvider = "virtualMachineData")
    protected void testOperatingSystemProvider(String imageId, String provider) throws Exception {
        LOG.info("Testing BIND on {} using {}", provider, imageId);

        Map<String, String> properties = MutableMap.of("image-id", imageId);
        if (provider.contains("ec2")) properties.put("user", "ec2-user");
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider, properties);

        BindDnsServer dns = app.createAndManageChild(EntitySpec.create(BindDnsServer.class));
        dns.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(dns, BindDnsServer.SERVICE_UP, true);
        Entities.dumpInfo(app);
    }

}
