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
package brooklyn.entity.effector;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

public class EffectorBasicTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(EffectorBasicTest.class);
    
    // NB: more tests of effectors in EffectorSayHiTest and EffectorConcatenateTest
    // as well as EntityConfigMapUsageTest and others

    private List<SimulatedLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        locs = ImmutableList.of(new SimulatedLocation());
    }
    
    @Test
    public void testInvokeEffectorStart() {
        app.start(locs);
        TestUtils.assertSetsEqual(locs, app.getLocations());
        // TODO above does not get registered as a task
    }

    @Test
    public void testInvokeEffectorStartWithMap() {
        app.invoke(Startable.START, MutableMap.of("locations", locs)).getUnchecked();
        TestUtils.assertSetsEqual(locs, app.getLocations());
    }

    @Test
    public void testInvokeEffectorStartWithArgs() {
        Entities.invokeEffectorWithArgs((EntityLocal)app, app, Startable.START, locs).getUnchecked();
        TestUtils.assertSetsEqual(locs, app.getLocations());
    }


    @Test
    public void testInvokeEffectorStartWithTwoEntities() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(locs);
        TestUtils.assertSetsEqual(locs, app.getLocations());
        TestUtils.assertSetsEqual(locs, entity.getLocations());
        TestUtils.assertSetsEqual(locs, entity2.getLocations());
    }
    
    @Test
    public void testInvokeEffectorTaskHasTag() {
        Task<Void> starting = app.invoke(Startable.START, MutableMap.of("locations", locs));
//        log.info("TAGS: "+starting.getTags());
        Assert.assertTrue(starting.getTags().contains(ManagementContextInternal.EFFECTOR_TAG));
    }
    
}
