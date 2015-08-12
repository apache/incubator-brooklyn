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
package org.apache.brooklyn.entity.network.bind;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.policy.EnricherSpec;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindOptions;
import brooklyn.entity.rebind.RebindTestFixture;
import brooklyn.test.entity.TestApplication;

public class BindDnsServerIntegrationTest extends RebindTestFixture<TestApplication> {

    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerIntegrationTest.class);
    private BindDnsServer dns;
    private DynamicCluster cluster;

    @Override
    protected TestApplication createApp() {
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
    public void testOneARecordAndNoCnameRecordsWhenEntitiesHaveSameName() {
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, origManagementContext);
        EnricherSpec<?> dnsEnricher = Enrichers.builder().transforming(Attributes.HOSTNAME)
                .computing(Functions.constant("my-name"))
                .publishing(PrefixAndIdEnricher.SENSOR)
                .build();
        EntitySpec<EmptySoftwareProcess> emptySoftwareProcessSpec = EntitySpec.create(EmptySoftwareProcess.class)
                .enricher(dnsEnricher);
        dns = app.createAndManageChild(EntitySpec.create(BindDnsServer.class, TestBindDnsServerImpl.class)
                .configure(BindDnsServer.HOSTNAME_SENSOR, PrefixAndIdEnricher.SENSOR));

        // App DNS will listen to
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, emptySoftwareProcessSpec)
                .configure(DynamicCluster.INITIAL_SIZE, 3));

        app.start(ImmutableList.of(app.newLocalhostProvisioningLocation()));

        assertDnsEntityEventuallyHasActiveMembers(1);
        // All of the entities publish the same domain name so there should be a single DNS entry and no CNAMEs.
        assertMapSizes(1, 1, 0, 1);
    }

    @Test(groups = "Integration")
    public void testDuplicateAAndCnameRecordsAreIgnored() {
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, origManagementContext);
        EnricherSpec<?> enricher1 = Enrichers.builder().transforming(Attributes.HOSTNAME)
                .computing(Functions.constant("my-name-1"))
                .publishing(PrefixAndIdEnricher.SENSOR)
                .build();
        EnricherSpec<?> enricher2 = Enrichers.builder().transforming(Attributes.HOSTNAME)
                .computing(Functions.constant("my-name-2"))
                .publishing(PrefixAndIdEnricher.SENSOR)
                .build();
        dns = app.createAndManageChild(EntitySpec.create(BindDnsServer.class, TestBindDnsServerImpl.class)
                .configure(BindDnsServer.HOSTNAME_SENSOR, PrefixAndIdEnricher.SENSOR));

        // Expect one of my-name-{1,2} to be used as the A record the other to be used as the CNAME.
        // Expect all duplicate records to be ignored.
        app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class).enricher(enricher1));
        app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class).enricher(enricher1));
        app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class).enricher(enricher1));
        app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class).enricher(enricher2));
        app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class).enricher(enricher2));
        app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class).enricher(enricher2));
        app.start(ImmutableList.of(app.newLocalhostProvisioningLocation()));

        assertDnsEntityEventuallyHasActiveMembers(2);
        assertMapSizes(2, 1, 1, 1);
    }

    @Test(groups = "Integration")
    public void testStripsInvalidCharactersFromHostname() {
        origApp.start(ImmutableList.of(origApp.newLocalhostProvisioningLocation()));
        cluster.resize(1);
        assertDnsEntityEventuallyHasActiveMembers(1);
        EntityLocal e = (EntityLocal) Iterables.getOnlyElement(cluster.getMembers());
        e.setAttribute(PrefixAndIdEnricher.SENSOR, " _-pretend.hostname.10.0.0.7.my-cloud.com");
        EntityTestUtils.assertAttributeEqualsEventually(dns, BindDnsServer.A_RECORDS,
                ImmutableMap.of("pretend-hostname-10-0-0-7-my-cloud-com", e.getAttribute(Attributes.ADDRESS)));
    }

    @Test(groups = "Integration")
    public void testHostnameTruncatedTo63Characters() {
        origApp.start(ImmutableList.of(origApp.newLocalhostProvisioningLocation()));
        cluster.resize(1);
        assertDnsEntityEventuallyHasActiveMembers(1);
        EntityLocal e = (EntityLocal) Iterables.getOnlyElement(cluster.getMembers());
        e.setAttribute(PrefixAndIdEnricher.SENSOR, Strings.repeat("a", 171));
        EntityTestUtils.assertAttributeEqualsEventually(dns, BindDnsServer.A_RECORDS,
                ImmutableMap.of(Strings.repeat("a", 63), e.getAttribute(Attributes.ADDRESS)));
    }

    @Test(groups = "Integration")
    public void testCanConfigureToListenToChildrenOfEntityOtherThanParent() {
        // Ignoring origApp
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, origManagementContext);

        EnricherSpec<PrefixAndIdEnricher> dnsEnricher = EnricherSpec.create(PrefixAndIdEnricher.class)
                .configure(PrefixAndIdEnricher.PREFIX, "dns-integration-test-")
                .configure(PrefixAndIdEnricher.MONITOR, Attributes.HOSTNAME);
        EntitySpec<EmptySoftwareProcess> emptySoftwareProcessSpec = EntitySpec.create(EmptySoftwareProcess.class)
                .enricher(dnsEnricher);

        // App DNS will listen to
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, emptySoftwareProcessSpec)
                .configure(DynamicCluster.INITIAL_SIZE, 3));

        // Apps that DNS should not listen to. Appreciate that their having dnsEnricher is unrealistic!
        app.createAndManageChild(emptySoftwareProcessSpec);
        app.createAndManageChild(emptySoftwareProcessSpec);

        Predicate<Entity> entityFilter = Predicates.and(
                Predicates.instanceOf(EmptySoftwareProcess.class),
                EntityPredicates.isChildOf(cluster));
        dns = app.createAndManageChild(EntitySpec.create(BindDnsServer.class, TestBindDnsServerImpl.class)
                .configure(BindDnsServer.ENTITY_FILTER, entityFilter)
                .configure(BindDnsServer.HOSTNAME_SENSOR, PrefixAndIdEnricher.SENSOR));

        app.start(ImmutableList.of(app.newLocalhostProvisioningLocation()));
        logDnsMappings();
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).keySet().size(), 1);
        assertMapSizes(3, 1, 2, 1);

        cluster.resize(4);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicCluster.GROUP_SIZE, 4);
        assertDnsEntityEventuallyHasActiveMembers(4);
        assertMapSizes(4, 1, 3, 1);

    }

    @Test(invocationCount=1, groups = "Integration")
    public void testRebindDns() throws Throwable {
        origApp.start(ImmutableList.of(origApp.newLocalhostProvisioningLocation()));
        logDnsMappings();
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).keySet().size(), 1);
        assertMapSizes(3, 1, 2, 1);

        rebind(RebindOptions.create().mementoDirBackup(mementoDirBackup));
        try {
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
        } catch (Throwable t) {
            // Failing in jenkins occasionally; don't know why and can't reproduce.
            // Therefore dumping out lots more info on failure.
            LOG.error("Test failed; dumping out contents of original persistence dir used for rebind...", t);
            dumpMementoDir(mementoDirBackup);
            throw t;
        }
    }

    @Test(groups = "Integration")
    public void testMapsSeveralEntitiesOnOneMachine() {
        origApp.start(ImmutableList.of(origApp.newLocalhostProvisioningLocation()));
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
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), addresses, "Mismatched address mappings");
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), aRecords, "Mismatched A records");
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), cnameRecords, "Mismatched CNAME records");
        assertEquals(dns.getAttribute(BindDnsServer.PTR_RECORDS).size(), ptrRecords, "Mismatched PTR records");
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
