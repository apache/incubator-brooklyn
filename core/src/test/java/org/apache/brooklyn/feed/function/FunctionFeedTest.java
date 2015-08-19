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
package org.apache.brooklyn.feed.function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Feed;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityInternal.FeedSupport;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionFeedTest;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Callables;

public class FunctionFeedTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(FunctionFeedTest.class);
    
    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor("aLong", "");

    private Location loc;
    private EntityLocal entity;
    private FunctionFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = new LocalhostMachineProvisioningLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        super.tearDown();
    }
    
    @Test
    public void testPollsFunctionRepeatedlyToSetAttribute() throws Exception {
        feed = FunctionFeed.builder()
                .entity(entity)
                .poll(new FunctionPollConfig<Integer,Integer>(SENSOR_INT)
                        .period(1)
                        .callable(new IncrementingCallable())
                        //.onSuccess((Function<Object,Integer>)(Function)Functions.identity()))
                        )
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                Integer val = entity.getAttribute(SENSOR_INT);
                assertTrue(val != null && val > 2, "val=" + val);
            }
        });
    }
    
    @Test
    public void testFeedDeDupe() throws Exception {
        testPollsFunctionRepeatedlyToSetAttribute();
        entity.addFeed(feed);
        log.info("Feed 0 is: "+feed);
        Feed feed0 = feed;
        
        testPollsFunctionRepeatedlyToSetAttribute();
        entity.addFeed(feed);
        log.info("Feed 1 is: "+feed);
        Feed feed1 = feed;
        Assert.assertFalse(feed1==feed0);

        FeedSupport feeds = ((EntityInternal)entity).feeds();
        Assert.assertEquals(feeds.getFeeds().size(), 1, "Wrong feed count: "+feeds.getFeeds());

        // a couple extra checks, compared to the de-dupe test in other *FeedTest classes
        Feed feedAdded = Iterables.getOnlyElement(feeds.getFeeds());
        Assert.assertTrue(feedAdded==feed1);
        Assert.assertFalse(feedAdded==feed0);
    }
    
    @Test
    public void testFeedDeDupeIgnoresSameObject() throws Exception {
        testPollsFunctionRepeatedlyToSetAttribute();
        entity.addFeed(feed);
        assertFeedIsPolling();
        entity.addFeed(feed);
        assertFeedIsPollingContinuously();
    }

    @Test
    public void testCallsOnSuccessWithResultOfCallable() throws Exception {
        feed = FunctionFeed.builder()
                .entity(entity)
                .poll(new FunctionPollConfig<Integer, Integer>(SENSOR_INT)
                        .period(1)
                        .callable(Callables.returning(123))
                        .onSuccess(new AddOneFunction()))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 124);
    }
    
    @Test
    public void testCallsOnExceptionWithExceptionFromCallable() throws Exception {
        final String errMsg = "my err msg";
        
        feed = FunctionFeed.builder()
                .entity(entity)
                .poll(new FunctionPollConfig<Object, String>(SENSOR_STRING)
                        .period(1)
                        .callable(new ExceptionCallable(errMsg))
                        .onException(new ToStringFunction()))
                .build();

        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains(errMsg), "val=" + val);
            }
        });
    }

    @Test
    public void testCallsOnFailureWithResultOfCallable() throws Exception {
        feed = FunctionFeed.builder()
                .entity(entity)
                .poll(new FunctionPollConfig<Integer, Integer>(SENSOR_INT)
                        .period(1)
                        .callable(Callables.returning(1))
                        .checkSuccess(Predicates.alwaysFalse())
                        .onSuccess(new AddOneFunction())
                        .onFailure(Functions.constant(-1)))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, -1);
    }

    @Test
    public void testCallsOnExceptionWhenCheckSuccessIsFalseButNoFailureHandler() throws Exception {
        feed = FunctionFeed.builder()
                .entity(entity)
                .poll(new FunctionPollConfig<Integer, Integer>(SENSOR_INT)
                        .period(1)
                        .callable(Callables.returning(1))
                        .checkSuccess(Predicates.alwaysFalse())
                        .onSuccess(new AddOneFunction())
                        .onException(Functions.constant(-1)))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, -1);
    }
    
    @Test
    public void testSharesFunctionWhenMultiplePostProcessors() throws Exception {
        final IncrementingCallable incrementingCallable = new IncrementingCallable();
        final List<Integer> ints = new CopyOnWriteArrayList<Integer>();
        final List<String> strings = new CopyOnWriteArrayList<String>();
        
        entity.subscribe(entity, SENSOR_INT, new SensorEventListener<Integer>() {
                @Override public void onEvent(SensorEvent<Integer> event) {
                    ints.add(event.getValue());
                }});
        entity.subscribe(entity, SENSOR_STRING, new SensorEventListener<String>() {
                @Override public void onEvent(SensorEvent<String> event) {
                    strings.add(event.getValue());
                }});
        
        feed = FunctionFeed.builder()
                .entity(entity)
                .poll(new FunctionPollConfig<Integer, Integer>(SENSOR_INT)
                        .period(10)
                        .callable(incrementingCallable))
                .poll(new FunctionPollConfig<Integer, String>(SENSOR_STRING)
                        .period(10)
                        .callable(incrementingCallable)
                        .onSuccess(new ToStringFunction()))
                .build();

        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(ints.subList(0, 2), ImmutableList.of(0, 1));
                assertTrue(strings.size()>=2, "wrong strings list: "+strings);
                assertEquals(strings.subList(0, 2), ImmutableList.of("0", "1"), "wrong strings list: "+strings);
            }});
    }
    
    @Test
    @SuppressWarnings("unused")
    public void testFunctionPollConfigBuilding() throws Exception {
        FunctionPollConfig<Integer, Integer> typeFromCallable = FunctionPollConfig.forSensor(SENSOR_INT)
                .period(1)
                .callable(Callables.returning(1))
                .onSuccess(Functions.constant(-1));

        FunctionPollConfig<Integer, Integer> typeFromSupplier = FunctionPollConfig.forSensor(SENSOR_INT)
                .period(1)
                .supplier(Suppliers.ofInstance(1))
                .onSuccess(Functions.constant(-1));

        FunctionPollConfig<Integer, Integer> usingConstructor = new FunctionPollConfig<Integer, Integer>(SENSOR_INT)
                .period(1)
                .supplier(Suppliers.ofInstance(1))
                .onSuccess(Functions.constant(-1));

        FunctionPollConfig<Integer, Integer> usingConstructorWithFailureOrException = new FunctionPollConfig<Integer, Integer>(SENSOR_INT)
                .period(1)
                .supplier(Suppliers.ofInstance(1))
                .onFailureOrException(Functions.<Integer>constant(null));
    }
    
    
    private void assertFeedIsPolling() {
        final Integer val = entity.getAttribute(SENSOR_INT);
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertNotEquals(val, entity.getAttribute(SENSOR_INT));
            }
        });
    }
    
    private void assertFeedIsPollingContinuously() {
        Asserts.succeedsContinually(new Runnable() {
            @Override
            public void run() {
                assertFeedIsPolling();
            }
        });
    }

    private static class IncrementingCallable implements Callable<Integer> {
        private final AtomicInteger next = new AtomicInteger(0);
        
        @Override public Integer call() {
            return next.getAndIncrement();
        }
    }
    
    private static class AddOneFunction implements Function<Integer, Integer> {
        @Override public Integer apply(@Nullable Integer input) {
            return (input != null) ? (input + 1) : null;
        }
    }
    
    private static class ExceptionCallable implements Callable<Void> {
        private final String msg;
        ExceptionCallable(String msg) {
            this.msg = msg;
        }
        @Override public Void call() {
            throw new RuntimeException(msg);
        }
    }
    
    public static class ToStringFunction implements Function<Object, String> {
        @Override public String apply(@Nullable Object input) {
            return (input != null) ? (input.toString()) : null;
        }
    }
}
