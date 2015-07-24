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
package brooklyn.entity.nosql.hazelcast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class HazelcastClusterEc2LiveTest extends AbstractEc2LiveTest {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(HazelcastClusterEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        HazelcastCluster cluster = app.createAndManageChild(EntitySpec.create(HazelcastCluster.class)
                .configure(HazelcastCluster.INITIAL_SIZE, 3)
                .configure(HazelcastCluster.MEMBER_SPEC, EntitySpec.create(HazelcastNode.class)));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, HazelcastNode.SERVICE_UP, true);

        HazelcastNode first = (HazelcastNode) Iterables.get(cluster.getMembers(), 0);
        HazelcastNode second = (HazelcastNode) Iterables.get(cluster.getMembers(), 1);

        assertNodesUpAndInCluster(first, second);
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_UP, true);
    }
    
    private void assertNodesUpAndInCluster(final HazelcastNode... nodes) {
        for (final HazelcastNode node : nodes) {
            EntityTestUtils.assertAttributeEqualsEventually(node, HazelcastNode.SERVICE_UP, true);
        }
    }

    @Test(enabled = false)
    public void testDummy() {
    } // Convince TestNG IDE integration that this really does have test methods

    
    @Test(groups = {"Live", "Live-sanity"})
    @Override
    public void test_CentOS_6_3() throws Exception {
        super.test_CentOS_6_3();
    }
}
