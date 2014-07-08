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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeMap;
import brooklyn.event.basic.Sensors;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.test.entity.TestEntityImpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class AttributeMapTest {

    Application app;
    AttributeMap map;
    private final AttributeSensor<Integer> exampleSensor = Sensors.newIntegerSensor("attributeMapTest.exampleSensor", "");

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new TestApplicationImpl();
        TestEntityImpl e = new TestEntityImpl(app);
        map = new AttributeMap(e, Collections.synchronizedMap(new LinkedHashMap()));
        Entities.startManagement(app);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    // See ENGR-2111
    @Test
    public void testConcurrentUpdatesDoNotCauseConcurrentModificationException() throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future> futures = Lists.newArrayList();
        
        try {
            for (int i = 0; i < 1000; i++) {
                final AttributeSensor<String> nextSensor = Sensors.newStringSensor("attributeMapTest.exampleSensor"+i, "");
                Future<?> future = executor.submit(newUpdateMapRunnable(map, nextSensor, "a"));
                futures.add(future);
            }
            
            for (Future<?> future : futures) {
                future.get();
            }
            
        } finally {
            executor.shutdownNow();
        }
    }
    
    @Test
    public void testConcurrentUpdatesAndGetsDoNotCauseConcurrentModificationException() throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future> futures = Lists.newArrayList();
        
        try {
            for (int i = 0; i < 1000; i++) {
                final AttributeSensor<String> nextSensor = Sensors.newStringSensor("attributeMapTest.exampleSensor"+i, "");
                Future<?> future = executor.submit(newUpdateMapRunnable(map, nextSensor, "a"));
                Future<?> future2 = executor.submit(newGetAttributeCallable(map, nextSensor));
                futures.add(future);
                futures.add(future2);
            }

            for (Future<?> future : futures) {
                future.get();
            }
            
        } finally {
            executor.shutdownNow();
        }
    }
    
    @Test
    public void testStoredSensorsCanBeRetrieved() throws Exception {
        AttributeSensor<String> sensor1 = Sensors.newStringSensor("a", "");
        AttributeSensor<String> sensor2 = Sensors.newStringSensor("b.c", "");
        
        map.update(sensor1, "1val");
        map.update(sensor2, "2val");
        
        assertEquals(map.getValue(sensor1), "1val");
        assertEquals(map.getValue(sensor2), "2val");
        
        assertEquals(map.getValue(ImmutableList.of("a")), "1val");
        assertEquals(map.getValue(ImmutableList.of("b","c")), "2val");
    }
        
    @Test
    public void testStoredByPathCanBeRetrieved() throws Exception {
        AttributeSensor<String> sensor1 = Sensors.newStringSensor("a", "");
        AttributeSensor<String> sensor2 = Sensors.newStringSensor("b.c", "");
        
        map.update(ImmutableList.of("a"), "1val");
        map.update(ImmutableList.of("b", "c"), "2val");
        
        assertEquals(map.getValue(sensor1), "1val");
        assertEquals(map.getValue(sensor2), "2val");
        
        assertEquals(map.getValue(ImmutableList.of("a")), "1val");
        assertEquals(map.getValue(ImmutableList.of("b","c")), "2val");
    }
        
    @Test
    public void testCanStoreSensorThenChildSensor() throws Exception {
        AttributeSensor<String> sensor = Sensors.newStringSensor("a", "");
        AttributeSensor<String> childSensor = Sensors.newStringSensor("a.b", "");
        
        map.update(sensor, "parentValue");
        map.update(childSensor, "childValue");
        
        assertEquals(map.getValue(childSensor), "childValue");
        assertEquals(map.getValue(sensor), "parentValue");
    }
        
    @Test
    public void testCanStoreChildThenParentSensor() throws Exception {
        AttributeSensor<String> sensor = Sensors.newStringSensor("a", "");
        AttributeSensor<String> childSensor = Sensors.newStringSensor("a.b", "");
        
        map.update(childSensor, "childValue");
        map.update(sensor, "parentValue");
        
        assertEquals(map.getValue(childSensor), "childValue");
        assertEquals(map.getValue(sensor), "parentValue");
    }
    
    protected <T> Runnable newUpdateMapRunnable(final AttributeMap map, final AttributeSensor<T> attribute, final T val) {
        return new Runnable() {
            @Override public void run() {
                map.update(attribute, val);
            }
        };
    }
    
    protected <T> Callable<T> newGetAttributeCallable(final AttributeMap map, final AttributeSensor<T> attribute) {
        return new Callable<T>() {
            @Override public T call() {
                return map.getValue(attribute);
            }
        };
    }
}
