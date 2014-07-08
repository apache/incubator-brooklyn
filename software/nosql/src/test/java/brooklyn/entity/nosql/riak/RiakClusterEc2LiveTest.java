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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

public class RiakClusterEc2LiveTest extends AbstractEc2LiveTest {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        RiakCluster cluster = app.createAndManageChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, 3)
                .configure(RiakCluster.MEMBER_SPEC, EntitySpec.create(RiakNode.class)));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, RiakNode.SERVICE_UP, true);

        RiakNode first = (RiakNode) Iterables.get(cluster.getMembers(), 0);
        RiakNode second = (RiakNode) Iterables.get(cluster.getMembers(), 1);

        EntityTestUtils.assertAttributeEqualsEventually(first, RiakNode.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, RiakNode.SERVICE_UP, true);

        EntityTestUtils.assertAttributeEqualsEventually(first, RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true);
    }

    @Test(enabled = false)
    public void testDummy() {
    } // Convince TestNG IDE integration that this really does have test methods


    @Override
    public void test_Ubuntu_12_0() throws Exception {
        //Override to add the custom securityGroup for opening Riak ports.
        // Image: {id=us-east-1/ami-d0f89fb9, providerId=ami-d0f89fb9, name=ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, location={scope=REGION, id=us-east-1, description=us-east-1, parent=aws-ec2, iso3166Codes=[US-VA]}, os={family=ubuntu, arch=paravirtual, version=12.04, description=099720109477/ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, is64Bit=true}, description=099720109477/ubuntu/images/ebs/ubuntu-precise-12.04-amd64-server-20130411.1, version=20130411.1, status=AVAILABLE[available], loginUser=ubuntu, userMetadata={owner=099720109477, rootDeviceType=ebs, virtualizationType=paravirtual, hypervisor=xen}}
        runTest(ImmutableMap.of("imageId", "us-east-1/ami-d0f89fb9", "loginUser", "ubuntu", "hardwareId", SMALL_HARDWARE_ID, "securityGroups", "RiakSecurityGroup"));
    }
}
