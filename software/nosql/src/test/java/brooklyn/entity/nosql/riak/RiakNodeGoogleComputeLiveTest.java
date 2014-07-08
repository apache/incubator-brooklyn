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
package brooklyn.entity.nosql.riak;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import brooklyn.entity.AbstractGoogleComputeLiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

public class RiakNodeGoogleComputeLiveTest extends AbstractGoogleComputeLiveTest {
    @Override
    protected void doTest(Location loc) throws Exception {
        RiakCluster cluster = app.createAndManageChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, 2)
                .configure(RiakCluster.MEMBER_SPEC, EntitySpec.create(RiakNode.class)));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, RiakCluster.SERVICE_UP, true);

        RiakNode first = (RiakNode) Iterables.get(cluster.getMembers(), 0);
        RiakNode second = (RiakNode) Iterables.get(cluster.getMembers(), 1);

        EntityTestUtils.assertAttributeEqualsEventually(first, RiakNode.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, RiakNode.SERVICE_UP, true);

        EntityTestUtils.assertAttributeEqualsEventually(first, RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true);

    }

    @Test(groups = {"Live"})
    @Override
    public void test_DefaultImage() throws Exception {
        super.test_DefaultImage();
    }

    @Test(enabled = false)
    public void testDummy() {
    } // Convince testng IDE integration that this really does have test methods

}
