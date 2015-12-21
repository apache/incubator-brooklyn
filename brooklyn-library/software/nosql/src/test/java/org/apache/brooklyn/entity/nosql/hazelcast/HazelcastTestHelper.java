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
package org.apache.brooklyn.entity.nosql.hazelcast;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;

public class HazelcastTestHelper {
    private static final Logger LOG = LoggerFactory.getLogger(HazelcastTestHelper.class);
    private static ClientConfig clientConfig;

    public static final String GROUP_NAME = "brooklyn";
    public static final String GROUP_PASS = "brooklyn";

    public HazelcastTestHelper(String hazelcastAddress, Integer hazelcastPort) {
        clientConfig = new ClientConfig();
        clientConfig.getGroupConfig().setName(GROUP_NAME).setPassword(GROUP_PASS);
        clientConfig.getNetworkConfig().addAddress(String.format("%s:%d", hazelcastAddress, hazelcastPort));
    }

    public HazelcastInstance getClient() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        LOG.info("Hazelcast client {}", client.getName());

        return client;
    }

    public static void testHazelcastCluster(TestApplication app, Location loc) {
        HazelcastCluster cluster = app.createAndManageChild(EntitySpec.create(HazelcastCluster.class)
                .configure(HazelcastCluster.INITIAL_SIZE, 3)
                .configure(HazelcastCluster.MEMBER_SPEC, EntitySpec.create(HazelcastNode.class)));
        app.start(ImmutableList.of(loc));

        EntityAsserts.assertAttributeEqualsEventually(cluster, HazelcastNode.SERVICE_UP, true);

        HazelcastNode first = (HazelcastNode) Iterables.get(cluster.getMembers(), 0);
        HazelcastNode second = (HazelcastNode) Iterables.get(cluster.getMembers(), 1);

        assertNodesUpAndInCluster(first, second);

        EntityAsserts.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_UP, true);
    }

    private static void assertNodesUpAndInCluster(final HazelcastNode... nodes) {
        for (final HazelcastNode node : nodes) {
            EntityAsserts.assertAttributeEqualsEventually(node, HazelcastNode.SERVICE_UP, true);
        }
    }
}
