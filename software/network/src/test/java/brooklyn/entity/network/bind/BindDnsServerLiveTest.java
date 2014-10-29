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
package brooklyn.entity.network.bind;

import static brooklyn.test.EntityTestUtils.assertAttributeEqualsEventually;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SameServerEntity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.policy.EnricherSpec;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

public class BindDnsServerLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerLiveTest.class);

    protected TestApplication app;
    protected Location testLocation;
    protected DynamicCluster cluster;
    protected BindDnsServer dns;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() throws Exception {
        if (app != null) Entities.destroyAllCatching(app.getManagementContext());
    }

    @DataProvider(name = "virtualMachineData")
    public Object[][] provideVirtualMachineData() {
        return new Object[][]{
                // CentOS 6.3
                new Object[]{"us-east-1/ami-7d7bfc14", "aws-ec2:us-east-1"},
                // Ubuntu 14.04
                new Object[]{"us-east-1/ami-c89e2ea0", "aws-ec2:us-east-1"},
        };
    }

    @Test(groups = "Live", dataProvider = "virtualMachineData")
    protected void testOperatingSystemProvider(String imageId, String provider) throws Exception {
        LOG.info("Testing BIND on {} using {}", provider, imageId);

        Map<String, String> properties = MutableMap.of("imageId", imageId);
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider, properties);

        BindDnsServer dns = app.createAndManageChild(EntitySpec.create(BindDnsServer.class)
                .enricher(EnricherSpec.create(PrefixAndIdEnricher.class)
                        .configure(PrefixAndIdEnricher.PREFIX, "dns-live-test-")
                        .configure(PrefixAndIdEnricher.MONITOR, Attributes.HOSTNAME)));
        dns.start(ImmutableList.of(testLocation));

        assertAttributeEqualsEventually(dns, BindDnsServer.SERVICE_UP, true);

        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), 0);
        assertEquals(dns.getAttribute(BindDnsServer.PTR_RECORDS).size(), 1);

        Entities.dumpInfo(app);
    }

    @Test(groups = "Live", dataProvider = "virtualMachineData")
    public void testUpdateWhenNewEntities(String imageId, String provider) {
        Map<String, String> properties = MutableMap.of("imageId", imageId);
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider, properties);

        EntitySpec<EmptySoftwareProcess> memberSpec = EntitySpec.create(EmptySoftwareProcess.class)
                .enricher(EnricherSpec.create(PrefixAndIdEnricher.class)
                        .configure(PrefixAndIdEnricher.PREFIX, "dns-live-test-")
                        .configure(PrefixAndIdEnricher.MONITOR, Attributes.HOSTNAME));
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, memberSpec)
                .configure(DynamicCluster.INITIAL_SIZE, 1));
        dns = app.createAndManageChild(EntitySpec.create(BindDnsServer.class)
                .configure(BindDnsServer.ENTITY_FILTER, Predicates.instanceOf(EmptySoftwareProcess.class))
                .configure(BindDnsServer.HOSTNAME_SENSOR, PrefixAndIdEnricher.SENSOR));

        app.start(ImmutableList.of(testLocation));
        assertAttributeEqualsEventually(dns, Attributes.SERVICE_UP, true);

        logDnsMappings();
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), 0);
        // Harder to make assertions on PTR because the entity servers might not be in the right CIDR

        cluster.resize(2);
        logDnsMappings();
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), 2);
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), 2);
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), 0);

        cluster.resize(1);
        logDnsMappings();
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), 0);
    }

    private void logDnsMappings() {
        LOG.info("A:     " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.A_RECORDS)));
        LOG.info("CNAME: " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.CNAME_RECORDS).asMap()));
        LOG.info("PTR:   " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.PTR_RECORDS)));
    }
}
