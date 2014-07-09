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
package brooklyn.event.feed.function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Callables;

public class FunctionFeedTest extends BrooklynAppUnitTestSupport {

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
            public void run() {
                Integer val = entity.getAttribute(SENSOR_INT);
                assertTrue(val != null && val > 2, "val=" + val);
            }
        });
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
            public void run() {
                assertEquals(ints.subList(0, 2), ImmutableList.of(0, 1));
                assertEquals(strings.subList(0, 2), ImmutableList.of("0", "1"));
            }});
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
