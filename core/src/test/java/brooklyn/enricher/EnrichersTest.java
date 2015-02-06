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
package brooklyn.enricher;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityAdjuncts;
import brooklyn.entity.basic.EntitySubscriptionTest.RecordingSensorEventListener;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.Sensors;
import brooklyn.policy.Enricher;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.guava.Functionals;
import brooklyn.util.text.StringFunctions;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
public class EnrichersTest extends BrooklynAppUnitTestSupport {

    public static final AttributeSensor<Integer> NUM1 = Sensors.newIntegerSensor("test.num1");
    public static final AttributeSensor<Integer> NUM2 = Sensors.newIntegerSensor("test.num2");
    public static final AttributeSensor<Integer> NUM3 = Sensors.newIntegerSensor("test.num3");
    public static final AttributeSensor<String> STR1 = Sensors.newStringSensor("test.str1");
    public static final AttributeSensor<String> STR2 = Sensors.newStringSensor("test.str2");
    public static final AttributeSensor<Set<Object>> SET1 = Sensors.newSensor(new TypeToken<Set<Object>>() {}, "test.set1", "set1 descr");
    public static final AttributeSensor<Long> LONG1 = Sensors.newLongSensor("test.long1");
    public static final AttributeSensor<Map<String,String>> MAP1 = Sensors.newSensor(new TypeToken<Map<String,String>>() {}, "test.map1", "map1 descr");
    @SuppressWarnings("rawtypes")
    public static final AttributeSensor<Map> MAP2 = Sensors.newSensor(Map.class, "test.map2");
    
    private TestEntity entity;
    private TestEntity entity2;
    private BasicGroup group;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testAdding() {
        Enricher enr = entity.addEnricher(Enrichers.builder()
                .combining(NUM1, NUM2)
                .publishing(NUM3)
                .computingSum()
                .build());
        
        Assert.assertEquals(EntityAdjuncts.getNonSystemEnrichers(entity), ImmutableList.of(enr));
        
        entity.setAttribute(NUM1, 2);
        entity.setAttribute(NUM2, 3);
        EntityTestUtils.assertAttributeEqualsEventually(entity, NUM3, 5);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testCombiningWithCustomFunction() {
        entity.addEnricher(Enrichers.builder()
                .combining(NUM1, NUM2)
                .publishing(NUM3)
                .computing(Functions.constant(1))
                .build());
        
        entity.setAttribute(NUM1, 2);
        entity.setAttribute(NUM2, 3);
        EntityTestUtils.assertAttributeEqualsEventually(entity, NUM3, 1);
    }
    
    @SuppressWarnings("unchecked")
    @Test(groups="Integration") // because takes a second
    public void testCombiningRespectsUnchanged() {
        entity.addEnricher(Enrichers.builder()
                .combining(NUM1, NUM2)
                .<Object>publishing(NUM3)
                .computing(new Function<Iterable<Integer>, Object>() {
                        @Override public Object apply(Iterable<Integer> input) {
                            if (input != null && Iterables.contains(input, 123)) {
                                return Enrichers.sum(input, 0, 0, new TypeToken<Integer>(){});
                            } else {
                                return Entities.UNCHANGED;
                            }
                        }})
                .build());
        
        entity.setAttribute(NUM1, 123);
        entity.setAttribute(NUM2, 3);
        EntityTestUtils.assertAttributeEqualsEventually(entity, NUM3, 126);
        
        entity.setAttribute(NUM1, 2);
        EntityTestUtils.assertAttributeEqualsContinually(entity, NUM3, 126);
    }
    
    @Test
    public void testFromEntity() {
        entity.addEnricher(Enrichers.builder()
                .transforming(NUM1)
                .publishing(NUM1)
                .computing(Functions.<Integer>identity())
                .from(entity2)
                .build());
        
        entity2.setAttribute(NUM1, 2);
        EntityTestUtils.assertAttributeEqualsEventually(entity, NUM1, 2);
    }
    
    @Test
    public void testTransforming() {
        entity.addEnricher(Enrichers.builder()
                .transforming(STR1)
                .publishing(STR2)
                .computing(StringFunctions.append("mysuffix"))
                .build());
        
        entity.setAttribute(STR1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR2, "myvalmysuffix");
    }

    @Test
    public void testTransformingCastsResult() {
        entity.addEnricher(Enrichers.builder()
                .transforming(NUM1)
                .publishing(LONG1)
                .computing(Functions.constant(Long.valueOf(1)))
                .build());
        
        entity.setAttribute(NUM1, 123);
        EntityTestUtils.assertAttributeEqualsEventually(entity, LONG1, Long.valueOf(1));
    }

    @Test
    public void testTransformingFromEvent() {
        entity.addEnricher(Enrichers.builder()
                .transforming(STR1)
                .publishing(STR2)
                .computingFromEvent(new Function<SensorEvent<String>, String>() {
                    @Override public String apply(SensorEvent<String> input) {
                        return input.getValue() + "mysuffix";
                    }})
                .build());
        
        entity.setAttribute(STR1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR2, "myvalmysuffix");
    }

    @Test(groups="Integration") // because takes a second
    public void testTransformingRespectsUnchangedButWillRepublish() {
        RecordingSensorEventListener record = new RecordingSensorEventListener();
        app.getManagementContext().getSubscriptionManager().subscribe(entity, STR2, record);
        
        entity.addEnricher(Enrichers.builder()
                .transforming(STR1)
                .<Object>publishing(STR2)
                .computing(new Function<String, Object>() {
                        @Override public Object apply(String input) {
                            return ("ignoredval".equals(input)) ? Entities.UNCHANGED : input;
                        }})
                .build());
        Asserts.assertThat(record.events, CollectionFunctionals.sizeEquals(0));
        
        entity.setAttribute(STR1, "myval");
        Asserts.eventually(Suppliers.ofInstance(record.events), CollectionFunctionals.sizeEquals(1));
        EntityTestUtils.assertAttributeEquals(entity, STR2, "myval");
        
        entity.setAttribute(STR1, "ignoredval");
        EntityTestUtils.assertAttributeEqualsContinually(entity, STR2, "myval");
        
        entity.setAttribute(STR1, "myval2");
        Asserts.eventually(Suppliers.ofInstance(record.events), CollectionFunctionals.sizeEquals(2));
        EntityTestUtils.assertAttributeEquals(entity, STR2, "myval2");
        
        entity.setAttribute(STR1, "myval2");
        entity.setAttribute(STR1, "myval2");
        entity.setAttribute(STR1, "myval3");
        Asserts.eventually(Suppliers.ofInstance(record.events), CollectionFunctionals.sizeEquals(5));
    }

    public void testTransformingSuppressDuplicates() {
        RecordingSensorEventListener record = new RecordingSensorEventListener();
        app.getManagementContext().getSubscriptionManager().subscribe(entity, STR2, record);
        
        entity.addEnricher(Enrichers.builder()
                .transforming(STR1)
                .publishing(STR2)
                .computing(Functions.<String>identity())
                .suppressDuplicates(true)
                .build());

        entity.setAttribute(STR1, "myval");
        Asserts.eventually(Suppliers.ofInstance(record.events), CollectionFunctionals.sizeEquals(1));
        EntityTestUtils.assertAttributeEquals(entity, STR2, "myval");
        
        entity.setAttribute(STR1, "myval2");
        entity.setAttribute(STR1, "myval2");
        entity.setAttribute(STR1, "myval3");
        EntityTestUtils.assertAttributeEqualsContinually(entity, STR2, "myval3");
        Asserts.assertThat(record.events, CollectionFunctionals.sizeEquals(3));
    }

    @Test
    public void testPropagating() {
        entity.addEnricher(Enrichers.builder()
                .propagating(ImmutableList.of(STR1))
                .from(entity2)
                .build());
        
        entity2.setAttribute(STR1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR1, "myval");
    }
    
    @Test
    public void testPropagatingAndRenaming() {
        entity.addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(STR1, STR2))
                .from(entity2)
                .build());
        
        entity2.setAttribute(STR1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(entity, STR2, "myval");
    }
    
    // FIXME What is default? members? children? fail?
    @Test
    public void testAggregatingGroupSum() {
        TestEntity child1 = group.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(child1);
        group.addMember(entity);
        group.addMember(entity2);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .publishing(NUM2)
                .fromMembers()
                .computingSum()
                .build());
        
        child1.setAttribute(NUM1, 1);
        entity.setAttribute(NUM1, 2);
        entity2.setAttribute(NUM1, 3);
        EntityTestUtils.assertAttributeEqualsEventually(group, NUM2, 5);
    }
    
    @Test
    public void testAggregatingChildrenSum() {
        group.addMember(entity);
        TestEntity child1 = group.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(child1);
        TestEntity child2 = group.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(child2);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .publishing(NUM2)
                .fromChildren()
                .computingSum()
                .build());
        
        entity.setAttribute(NUM1, 1);
        child1.setAttribute(NUM1, 2);
        child2.setAttribute(NUM1, 3);
        EntityTestUtils.assertAttributeEqualsEventually(group, NUM2, 5);
    }

    @Test
    public void testAggregatingExcludingBlankString() {
        group.addMember(entity);
        group.addMember(entity2);
        group.addEnricher(Enrichers.builder()
                .aggregating(STR1)
                .publishing(SET1)
                .fromMembers()
                .excludingBlank()
                .computing(new Function<Collection<?>, Set<Object>>() {
                    @Override public Set<Object> apply(Collection<?> input) {
                        // accept null values, so don't use ImmutableSet
                        return (input == null) ? ImmutableSet.<Object>of() : MutableSet.<Object>copyOf(input);
                    }})
                .build());
        
        entity.setAttribute(STR1, "1");
        entity2.setAttribute(STR1, "2");
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of("1", "2"));
        
        entity.setAttribute(STR1, "3");
        entity2.setAttribute(STR1, null);
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of("3"));
        
        entity.setAttribute(STR1, "");
        entity2.setAttribute(STR1, "4");
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of("4"));
    }

    @Test
    public void testAggregatingExcludingNull() {
        group.addMember(entity);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .publishing(SET1)
                .fromMembers()
                .excludingBlank()
                .computing(new Function<Collection<?>, Set<Object>>() {
                    @Override public Set<Object> apply(Collection<?> input) {
                        // accept null values, so don't use ImmutableSet
                        return (input == null) ? ImmutableSet.<Object>of() : MutableSet.<Object>copyOf(input);
                    }})
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of());

        entity.setAttribute(NUM1, 1);
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of(1));
        
        entity.setAttribute(NUM1, null);
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of());
        
        entity.setAttribute(NUM1, 2);
        EntityTestUtils.assertAttributeEqualsEventually(group, SET1, ImmutableSet.<Object>of(2));
    }

    @Test
    public void testAggregatingCastsResult() {
        group.addMember(entity);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .publishing(LONG1)
                .fromMembers()
                .computing(Functions.constant(Long.valueOf(1)))
                .build());
        
        entity.setAttribute(NUM1, 123);
        EntityTestUtils.assertAttributeEqualsEventually(group, LONG1, Long.valueOf(1));
    }
    
    @Test(groups="Integration") // because takes a second
    public void testAggregatingRespectsUnchanged() {
        group.addMember(entity);
        group.addEnricher(Enrichers.builder()
                .aggregating(NUM1)
                .<Object>publishing(LONG1)
                .fromMembers()
                .computing(new Function<Iterable<Integer>, Object>() {
                        @Override public Object apply(Iterable<Integer> input) {
                            if (input != null && Iterables.contains(input, 123)) {
                                return Enrichers.sum(input, 0, 0, new TypeToken<Integer>(){});
                            } else {
                                return Entities.UNCHANGED;
                            }
                        }})
                .build());
        
        entity.setAttribute(NUM1, 123);
        EntityTestUtils.assertAttributeEqualsEventually(group, LONG1, Long.valueOf(123));
        
        entity.setAttribute(NUM1, 987654);
        EntityTestUtils.assertAttributeEqualsContinually(group, LONG1, Long.valueOf(123));
    }
    @Test
    public void testUpdatingMap1() {
        entity.addEnricher(Enrichers.builder()
                .updatingMap(MAP1)
                .from(LONG1)
                .computing(Functionals.ifEquals(-1L).value("-1 is not allowed"))
                .build());
        
        doUpdatingMapChecks(MAP1);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testUpdatingMap2() {
        entity.addEnricher(Enrichers.builder()
                .updatingMap((AttributeSensor)MAP2)
                .from(LONG1)
                .computing(Functionals.ifEquals(-1L).value("-1 is not allowed"))
                .build());
        
        doUpdatingMapChecks(MAP2);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void doUpdatingMapChecks(AttributeSensor mapSensor) {
        EntityTestUtils.assertAttributeEqualsEventually(entity, mapSensor, MutableMap.<String,String>of());
        
        entity.setAttribute(LONG1, -1L);
        EntityTestUtils.assertAttributeEqualsEventually(entity, mapSensor, MutableMap.<String,String>of(
            LONG1.getName(), "-1 is not allowed"));
        
        entity.setAttribute(LONG1, 1L);
        EntityTestUtils.assertAttributeEqualsEventually(entity, mapSensor, MutableMap.<String,String>of());
    }

}
