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

import static org.testng.Assert.assertEquals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestFixture;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.policy.EnricherSpec;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

public class BindDnsServerIntegrationTest extends RebindTestFixture {

    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerIntegrationTest.class);
    private BindDnsServer dns;
    private DynamicCluster cluster;

    @Override
    protected StartableApplication createApp() {
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, origManagementContext);
        dns = app.createAndManageChild(EntitySpec.create(BindDnsServer.class, TestBindDnsServerImpl.class)
                .configure(BindDnsServer.ENTITY_FILTER, Predicates.instanceOf(EmptySoftwareProcess.class))
                .configure(BindDnsServer.HOSTNAME_SENSOR, PrefixAndIdEnricher.SENSOR));
        EntitySpec<EmptySoftwareProcess> memberSpec = EntitySpec.create(EmptySoftwareProcess.class)
                .enricher(EnricherSpec.create(PrefixAndIdEnricher.class)
                        .configure(PrefixAndIdEnricher.PREFIX, "dns-integration-test-")
                        .configure(PrefixAndIdEnricher.MONITOR, Attributes.HOSTNAME));
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, memberSpec)
                .configure(DynamicCluster.INITIAL_SIZE, 3));
        return app;
    }

    @Test(groups = "Integration")
    public void testRebindDns() throws Exception {
        LocationSpec.create(LocalhostMachineProvisioningLocation.class);
        origApp.start(ImmutableList.of(new LocalhostMachineProvisioningLocation()));
        logDnsMappings();
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).keySet().size(), 1);
        assertMapSizes(3, 1, 2, 1);

        rebind();
        dns = (BindDnsServer) Iterables.getOnlyElement(Iterables.filter(newApp.getChildren(), Predicates.instanceOf(BindDnsServer.class)));
        cluster = (DynamicCluster) Iterables.getOnlyElement(Iterables.filter(newApp.getChildren(), Predicates.instanceOf(DynamicCluster.class)));

        // assert original attributes restored and the server can be updated.
        logDnsMappings();
        assertMapSizes(3, 1, 2, 1);
        cluster.resize(1);
        assertDnsEntityEventuallyHasActiveMembers(1);
        logDnsMappings();
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicCluster.GROUP_SIZE, 1);
        assertMapSizes(1, 1, 0, 1);
        cluster.resize(5);
        assertDnsEntityEventuallyHasActiveMembers(5);
        logDnsMappings();
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicCluster.GROUP_SIZE, 5);
        assertMapSizes(5, 1, 4, 1);
    }

    @Test(groups = "Integration")
    public void testMapsSeveralEntitiesOnOneMachine() {
        origApp.start(ImmutableList.of(new LocalhostMachineProvisioningLocation()));
        EntityTestUtils.assertAttributeEqualsEventually(dns, Attributes.SERVICE_UP, true);
        logDnsMappings();

        // One host with one A, two CNAME and one PTR record
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).keySet().size(), 1);
        assertMapSizes(3, 1, 2, 1);
        String key = Iterables.getOnlyElement(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).keySet());
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).get(key).size(), 3);

        Entities.dumpInfo(dns);
    }

    private void assertMapSizes(int addresses, int aRecords, int cnameRecords, int ptrRecords) {
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), addresses);
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), aRecords);
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), cnameRecords);
        assertEquals(dns.getAttribute(BindDnsServer.PTR_RECORDS).size(), ptrRecords);
    }

    private void logDnsMappings() {
        LOG.info("A:     " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.A_RECORDS)));
        LOG.info("CNAME: " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.CNAME_RECORDS).asMap()));
        LOG.info("PTR:   " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.PTR_RECORDS)));
    }

    private void assertDnsEntityEventuallyHasActiveMembers(final int size) {
        EntityTestUtils.assertPredicateEventuallyTrue(dns, new Predicate<BindDnsServer>() {
            @Override
            public boolean apply(BindDnsServer input) {
                return input.getAddressMappings().size() == size;
            }
        });
    }

}
