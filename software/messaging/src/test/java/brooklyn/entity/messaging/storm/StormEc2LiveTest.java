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
package brooklyn.entity.messaging.storm;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.zookeeper.ZooKeeperNode;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class StormEc2LiveTest extends AbstractEc2LiveTest {

    /**
     * Test that can install, start and use a Storm cluster: 1 nimbus, 1 zookeeper, 1 supervisor (worker node).
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        ZooKeeperNode zookeeper = app.createAndManageChild(EntitySpec.create(ZooKeeperNode.class));
        Storm nimbus = app.createAndManageChild(EntitySpec.create(Storm.class).configure("storm.role",
                Storm.Role.NIMBUS));
        Storm supervisor = app.createAndManageChild(EntitySpec.create(Storm.class).configure("storm.role",
                Storm.Role.SUPERVISOR));
        Storm ui = app.createAndManageChild(EntitySpec.create(Storm.class).configure("storm.role",
                Storm.Role.UI));        
        app.start(ImmutableList.of(loc));
        Entities.dumpInfo(app);
        
        EntityTestUtils.assertAttributeEqualsEventually(zookeeper, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(nimbus, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(supervisor, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(ui, Startable.SERVICE_UP, true);
    }

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods
}
