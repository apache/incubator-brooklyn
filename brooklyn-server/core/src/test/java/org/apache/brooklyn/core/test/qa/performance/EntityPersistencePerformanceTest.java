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
package org.apache.brooklyn.core.test.qa.performance;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.policy.TestPolicy;
import org.apache.brooklyn.test.PerformanceTestUtils;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;

public class EntityPersistencePerformanceTest extends RebindTestFixtureWithApp {

    // TODO Not measuring performance per cycle; just looking at CPU usage during test
    
    protected int getPersistPeriodMillis() {
        return 1000;
    }

    @Test(groups="Integration")
    public void testManyEntities() throws Exception {
        final int NUM_ENTITIES = 100;
        final Duration TEST_LENGTH = Duration.of(60, TimeUnit.SECONDS);
        final Duration REPEAT_EVERY = Duration.of(500, TimeUnit.MILLISECONDS);
        run(NUM_ENTITIES, TEST_LENGTH, REPEAT_EVERY, "manyEntities");
    }
    
    @Test(groups="Integration")
    public void testRapidChanges() throws Exception {
        final int NUM_ENTITIES = 10;
        final Duration TEST_LENGTH = Duration.of(60, TimeUnit.SECONDS);
        final Duration REPEAT_EVERY = Duration.of(10, TimeUnit.MILLISECONDS);
        run(NUM_ENTITIES, TEST_LENGTH, REPEAT_EVERY, "rapidChanges");
    }
    
    protected void run(int numEntities, Duration testLength, Duration repeatEvery, String loggingContext) throws Exception {
        final List<TestEntity> entities = Lists.newArrayList();
        final List<SimulatedLocation> locs = Lists.newArrayList();
        
        for (int i = 0; i < numEntities; i++) {
            TestEntity entity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
            entity.policies().add(PolicySpec.create(TestPolicy.class));
            SimulatedLocation loc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
            entities.add(entity);
            locs.add(loc);
        }
        
        Future<?> future = PerformanceTestUtils.sampleProcessCpuTime(Duration.ONE_SECOND, "during "+loggingContext);
        try {
            Repeater.create()
                    .every(repeatEvery)
                    .repeat(new Runnable() {
                            int i = 0;
                            public void run() {
                                for (TestEntity entity : entities) {
                                    entity.sensors().set(TestEntity.SEQUENCE, i++);
                                    Policy policy = Iterables.find(entity.policies(), Predicates.instanceOf(TestPolicy.class));
                                    policy.config().set(TestPolicy.CONF_NAME, "name-"+i);
                                }
                            }})
                    .limitTimeTo(testLength)
                    .until(Callables.returning(false))
                    .run();
        } finally {
            future.cancel(true);
        }
    }
}
