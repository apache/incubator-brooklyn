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
package brooklyn.entity.messaging.zookeeper;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.zookeeper.ZooKeeperNode;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class ZooKeeperEc2LiveTest extends AbstractEc2LiveTest {

    /**
     * Test that can install, start and use a Zookeeper instance.
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        ZooKeeperNode zookeeper = app.createAndManageChild(EntitySpec.create(ZooKeeperNode.class).configure("jmxPort", "31001+"));
        app.start(ImmutableList.of(loc));
        Entities.dumpInfo(zookeeper);
        EntityTestUtils.assertAttributeEqualsEventually(zookeeper, Startable.SERVICE_UP, true);
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods
}
