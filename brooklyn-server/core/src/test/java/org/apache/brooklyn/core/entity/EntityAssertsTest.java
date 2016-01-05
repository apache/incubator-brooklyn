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
package org.apache.brooklyn.core.entity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Tests on {@link EntityAsserts}.
 */
public class EntityAssertsTest extends BrooklynAppUnitTestSupport {

    private static final String STOOGE = "stooge";

    private SimulatedLocation loc;
    private TestEntity entity;
    private ScheduledExecutorService executor;
    private DynamicGroup stooges;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.getManagementContext().getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        stooges = app.createAndManageChild(EntitySpec.create(DynamicGroup.class));
        final EntitySpec<TestEntity> stooge =
                EntitySpec.create(TestEntity.class).configure(TestEntity.CONF_NAME, STOOGE);
        app.createAndManageChild(stooge);
        app.createAndManageChild(stooge);
        app.createAndManageChild(stooge);
        app.start(ImmutableList.of(loc));
        executor = Executors.newScheduledThreadPool(3);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        if (app != null) Entities.destroyAll(app.getManagementContext());
        super.tearDown();
    }


    @Test
    public void shouldAssertAttributeEquals() {
        final String myName = "myname";
        entity.sensors().set(TestEntity.NAME, myName);
        EntityAsserts.assertAttributeEquals(entity, TestEntity.NAME, myName);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void shouldFailToAssertAttributeEquals() {
        final String myName = "myname";
        entity.sensors().set(TestEntity.NAME, myName);
        EntityAsserts.assertAttributeEquals(entity, TestEntity.NAME, "bogus");
    }

    @Test
    public void shouldAssertConfigEquals() {
        EntityAsserts.assertConfigEquals(entity, TestEntity.CONF_NAME, "defaultval");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void shouldFailToAssertConfigEquals() {
        EntityAsserts.assertConfigEquals(entity, TestEntity.CONF_NAME, "bogus");
    }

    @Test(groups="Integration")
    public void shouldAssertAttributeEqualsEventually() {
        entity.sensors().set(TestEntity.NAME, "before");
        final String after = "after";
        setSensorValueLater(TestEntity.NAME, after, Duration.seconds(2));
        EntityAsserts.assertAttributeEqualsEventually(entity, TestEntity.NAME, after);
    }

    @Test(groups="Integration", expectedExceptions = AssertionError.class)
    public void shouldFailToAssertAttributeEqualsEventually() {
        entity.sensors().set(TestEntity.NAME, "before");
        final String after = "after";
        setSensorValueLater(TestEntity.NAME, after, Duration.seconds(2));
        EntityAsserts.assertAttributeEqualsEventually(ImmutableMap.of("timeout", "1s"), entity, TestEntity.NAME, after);
    }

    private <T> void setSensorValueLater(final AttributeSensor<T> sensor, final T value, final Duration delay) {
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                entity.sensors().set(sensor, value);
            }
        }, delay.toUnit(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
    }

    @Test(groups="Integration")
    public void shouldAssertAttributeEventuallyNonNull() {
        EntityAsserts.assertAttributeEquals(entity, TestEntity.NAME, null);
        setSensorValueLater(TestEntity.NAME, "something", Duration.seconds(1));
        EntityAsserts.assertAttributeEventuallyNonNull(entity, TestEntity.NAME);
    }

    @Test(groups="Integration")
    public void shouldAssertAttributeEventually() {
        setSensorValueLater(TestEntity.NAME, "testing testing 123", Duration.seconds(1));
        EntityAsserts.assertAttributeEventually(entity, TestEntity.NAME, new Predicate<String>() {
            @Override
            public boolean apply(String input) {
                return input.matches(".*\\d+");
            }
        });
    }

    @Test
    public void shouldAssertAttribute() {
        final String before = "before";
        entity.sensors().set(TestEntity.NAME, before);
        EntityAsserts.assertAttribute(entity, TestEntity.NAME, Predicates.equalTo(before));
    }

    @Test(groups="Integration")
    public void shouldAssertPredicateEventuallyTrue() {
        final int testVal = 987654321;
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                entity.setSequenceValue(testVal);
            }
        }, 1, TimeUnit.SECONDS);
        EntityAsserts.assertPredicateEventuallyTrue(entity, new Predicate<TestEntity>() {
            @Override
            public boolean apply(TestEntity input) {
                return testVal == input.getSequenceValue() ;
            }
        });
    }

    @Test(groups="Integration")
    public void shouldAssertAttributeEqualsContinually() {
        final String myName = "myname";
        entity.sensors().set(TestEntity.NAME, myName);
        EntityAsserts.assertAttributeEqualsContinually(
                ImmutableMap.of("timeout", "2s"), entity, TestEntity.NAME, myName);
    }

    @Test(groups="Integration", expectedExceptions = AssertionError.class)
    public void shouldFailAssertAttributeEqualsContinually() {
        final String myName = "myname";
        entity.sensors().set(TestEntity.NAME, myName);
        setSensorValueLater(TestEntity.NAME, "something", Duration.seconds(1));
        EntityAsserts.assertAttributeEqualsContinually(
                ImmutableMap.of("timeout", "2s"), entity, TestEntity.NAME, myName);
    }

    @Test(groups="Integration")
    public void shouldAssertGroupSizeEqualsEventually() {
        setGroupFilterLater(STOOGE, 1);
        EntityAsserts.assertGroupSizeEqualsEventually(ImmutableMap.of("timeout", "2s"), stooges, 3);
        setGroupFilterLater("Marx Brother", 1);
        EntityAsserts.assertGroupSizeEqualsEventually(stooges, 0);
    }

    private void setGroupFilterLater(final String conf, long delaySeconds) {
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                stooges.setEntityFilter(EntityPredicates.configEqualTo(TestEntity.CONF_NAME, conf));
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    @Test(groups="Integration")
    public void shouldAssertAttributeChangesEventually () {
        entity.sensors().set(TestEntity.NAME, "before");
        setSensorValueLater(TestEntity.NAME, "after", Duration.seconds(2));
        EntityAsserts.assertAttributeChangesEventually(entity, TestEntity.NAME);
    }

    @Test(groups="Integration")
    public void shouldAssertAttributeNever() {
        entity.sensors().set(TestEntity.NAME, "ever");
        EntityAsserts.assertAttributeContinuallyNotEqualTo(ImmutableMap.of("timeout", "5s"), entity, TestEntity.NAME, "after");
    }

}
