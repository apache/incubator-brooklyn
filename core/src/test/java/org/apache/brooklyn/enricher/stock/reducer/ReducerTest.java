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
package org.apache.brooklyn.enricher.stock.reducer;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ReducerTest extends BrooklynAppUnitTestSupport {

    public static final AttributeSensor<String> STR1 = Sensors.newStringSensor("test.str1");
    public static final AttributeSensor<String> STR2 = Sensors.newStringSensor("test.str2");
    public static final AttributeSensor<String> STR3 = Sensors.newStringSensor("test.str3");
    public static final AttributeSensor<Integer> INT1 = Sensors.newIntegerSensor("test.int1");

    private TestEntity entity;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @Test
    public void testBasicReducer(){
        entity.enrichers().add(EnricherSpec.create(Reducer.class).configure(
                MutableMap.of(
                Reducer.SOURCE_SENSORS, ImmutableList.of(STR1, STR2),
                Reducer.PRODUCER, entity,
                Reducer.TARGET_SENSOR, STR3,
                Reducer.REDUCER_FUNCTION, new Concatenator())
            )
        );

        EntityTestUtils.assertAttributeEquals(entity, STR3, null);
        
        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsContinually(entity, STR3, null);

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foobar");
    }

    @Test
    public void testReducingBuilderWithConcatenator() {
        entity.enrichers().add(Enrichers.builder()
                .reducing(Reducer.class, ImmutableList.<AttributeSensor<?>>of(STR1, STR2))
                .from(entity)
                .computing(new Concatenator())
                .publishing(STR3)
                .build()
        );

        EntityTestUtils.assertAttributeEquals(entity, STR3, null);
        
        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsContinually(entity, STR3, null);

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foobar");
    }
    
    @Test
    public void testReducingBuilderWithLengthCalculator() {
        entity.enrichers().add(Enrichers.builder()
                .reducing(Reducer.class, ImmutableList.<AttributeSensor<?>>of(STR1, STR2))
                .from(entity)
                .computing(new LengthCalculator())
                .publishing(INT1)
                .build()
        );

        EntityTestUtils.assertAttributeEquals(entity, INT1, null);
        
        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(entity, INT1, 3);

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, INT1, 6);
    }
    
    @Test
    public void testReducingBuilderWithJoinerFunction() {
        entity.enrichers().add(Enrichers.builder()
                .reducing(Reducer.class, ImmutableList.<AttributeSensor<?>>of(STR1, STR2))
                .from(entity)
                .computing("joiner", ImmutableMap.<String, Object>of("separator", "-"))
                .publishing(STR3)
                .build()
        );

        EntityTestUtils.assertAttributeEquals(entity, STR3, null);
        
        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foo-null");

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foo-bar");
    }
    
    @Test
    public void testReducingBuilderWithJoinerFunctionWithDefaultParameter() {
        entity.enrichers().add(Enrichers.builder()
            .reducing(Reducer.class, ImmutableList.<AttributeSensor<?>>of(STR1, STR2))
            .from(entity)
            .computing("joiner")
            .publishing(STR3)
            .build()
        );
        EntityTestUtils.assertAttributeEquals(entity, STR3, null);
        
        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foo, null");

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foo, bar");
    }
    
    @Test
    public void testReducingBuilderWithJoinerFunctionAndUnusedParameter() {
        
        entity.enrichers().add(Enrichers.builder()
            .reducing(Reducer.class, ImmutableList.<AttributeSensor<?>>of(STR1, STR2))
            .from(entity)
            .computing("joiner", ImmutableMap.<String, Object>of("non.existent.parameter", "-"))
            .publishing(STR3)
            .build()
        );
        EntityTestUtils.assertAttributeEquals(entity, STR3, null);
        
        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foo, null");

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "foo, bar");
    }
    
    @Test
    public void testReducingBuilderWithFormatStringFunction() {
        entity.enrichers().add(Enrichers.builder()
            .reducing(Reducer.class, ImmutableList.<AttributeSensor<?>>of(STR1, STR2))
            .from(entity)
            .computing("formatString", ImmutableMap.<String, Object>of("format", "hello, %s and %s"))
            .publishing(STR3)
            .build()
        );
        
        EntityTestUtils.assertAttributeEquals(entity, STR3, null);
        
        entity.sensors().set(STR1, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "hello, foo and null");

        entity.sensors().set(STR2, "bar");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR3, "hello, foo and bar");
    }
    
    @Test
    public void testReducingBuilderWithNamedNonExistentFunction() {
        try { 
            entity.enrichers().add(Enrichers.builder()
                .reducing(Reducer.class, ImmutableList.<AttributeSensor<?>>of(STR1, STR2))
                .from(entity)
                .computing("unknown function name", ImmutableMap.<String, Object>of("separator", "-"))
                .publishing(STR3)
                .build()
            );
            Asserts.fail("Expected exception when adding reducing enricher with unknown named function");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            Assert.assertNotNull(t);
        }
    }
    
    private static class Concatenator implements Function<List<String>, String> {
        @Nullable
        @Override
        public String apply(List<String> values) {
            StringBuilder result = new StringBuilder();
            for (String value : values) {
                if (value == null) {
                    return null;
                } else {
                    result.append(value);
                }
            }
            return result.toString();
        }
    }
    
    private static class LengthCalculator implements Function<List<String>, Integer>{

        @Override
        public Integer apply(List<String> values) {
            int acc = 0;
            for (String value : values) {
                if (value != null) {
                    acc += value.length();
                }
            }
            return acc;
        }
        
    }
}
