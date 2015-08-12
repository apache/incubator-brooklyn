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

import static org.apache.brooklyn.test.EntityTestUtils.assertAttributeEqualsEventually;
import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.policy.EnricherSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SameServerEntity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.time.Duration;

public class BindDnsServerLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerLiveTest.class);

    public static void testBindStartsAndUpdates(TestApplication app, Location testLocation) throws Exception {
        DynamicCluster cluster;
        BindDnsServer dns;

        SameServerEntity sse = app.createAndManageChild(EntitySpec.create(SameServerEntity.class));
        EntitySpec<EmptySoftwareProcess> memberSpec = EntitySpec.create(EmptySoftwareProcess.class)
                .enricher(EnricherSpec.create(PrefixAndIdEnricher.class)
                        .configure(PrefixAndIdEnricher.PREFIX, "dns-live-test-")
                        .configure(PrefixAndIdEnricher.MONITOR, Attributes.HOSTNAME));
        cluster = sse.addChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, memberSpec)
                .configure(DynamicCluster.INITIAL_SIZE, 1));
        dns = sse.addChild((EntitySpec.create(BindDnsServer.class)
                .configure(BindDnsServer.ENTITY_FILTER, Predicates.instanceOf(EmptySoftwareProcess.class))
                .configure(BindDnsServer.HOSTNAME_SENSOR, PrefixAndIdEnricher.SENSOR)));
        Entities.manage(cluster);
        Entities.manage(dns);

        app.start(ImmutableList.of(testLocation));
        assertAttributeEqualsEventually(dns, Attributes.SERVICE_UP, true);

        logDnsMappings(dns);
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), 0);
        // Harder to make assertions on PTR because the entity servers might not be in the right CIDR

        cluster.resize(2);
        waitForNumberOfAddressMappings(dns, 2);
        logDnsMappings(dns);
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), 2);
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), 1);

        cluster.resize(1);
        waitForNumberOfAddressMappings(dns, 1);
        logDnsMappings(dns);
        assertEquals(dns.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).entries().size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.A_RECORDS).size(), 1);
        assertEquals(dns.getAttribute(BindDnsServer.CNAME_RECORDS).size(), 0);
    }

    private static void logDnsMappings(BindDnsServer dns) {
        LOG.info("A:     " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.A_RECORDS)));
        LOG.info("CNAME: " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.CNAME_RECORDS).asMap()));
        LOG.info("PTR:   " + Joiner.on(", ").withKeyValueSeparator("=").join(
                dns.getAttribute(BindDnsServer.PTR_RECORDS)));
    }

    /**
     * Waits for the Bind entity to have the expected number of mappings or for thirty seconds to have elapsed.
     */
    private static void waitForNumberOfAddressMappings(final BindDnsServer dns, final int expectedMappings) {
        Repeater.create()
                .every(Duration.seconds(1))
                .until(dns, new Predicate<BindDnsServer>() {
                    @Override
                    public boolean apply(BindDnsServer bindDnsServer) {
                        return bindDnsServer.getAttribute(BindDnsServer.ADDRESS_MAPPINGS).size() == expectedMappings;
                    }
                })
                .limitTimeTo(Duration.seconds(30))
                .run();
    }
}
