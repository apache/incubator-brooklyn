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
package org.apache.brooklyn.enricher.stock;


import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.test.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@SuppressWarnings("deprecation")
public class CustomAggregatingEnricherDeprecatedTest {

    public static final Logger log = LoggerFactory.getLogger(CustomAggregatingEnricherDeprecatedTest.class);
            
    private static final long TIMEOUT_MS = 10*1000;
    private static final long SHORT_WAIT_MS = 250;
    
    TestApplication app;
    TestEntity producer;
    Map<String, ?> producersFlags;
    
    AttributeSensor<Integer> intSensor = Sensors.newIntegerSensor("int sensor");
    AttributeSensor<Double> doubleSensor = Sensors.newDoubleSensor("double sensor");
    AttributeSensor<Integer> target = Sensors.newIntegerSensor("target sensor");

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        producer = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        producersFlags = ImmutableMap.of("producers", ImmutableList.of(producer));
        
        app.start(ImmutableList.of(new SimulatedLocation()));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app!=null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testEnrichersWithNoProducers() {
        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(ImmutableMap.<String,Object>of(), intSensor, target, 11, 40);
        producer.enrichers().add(cae);
        assertEquals(cae.getAggregate(), 40);
    }

    @Test
    public void testSummingEnricherWhenNoSensorValuesYet() {
        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                producersFlags, intSensor, target, 11, 40);
        producer.enrichers().add(cae);
        assertEquals(cae.getAggregate(), 11);
    }

    @Test
    public void testSingleProducerSum() {
        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                producersFlags, intSensor, target, null, null);
        producer.enrichers().add(cae);
        Assert.assertEquals(cae.getAggregate(), null);
        cae.onEvent(intSensor.newEvent(producer, 1));
        assertEquals(cae.getAggregate(), 1);
    }
    
    @Test
    public void testSummingEnricherWhenNoAndNullSensorValue() {
        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                producersFlags, intSensor, target, null, null);
        producer.enrichers().add(cae);
        Assert.assertEquals(cae.getAggregate(), null);
        cae.onEvent(intSensor.newEvent(producer, null));
        Assert.assertEquals(cae.getAggregate(), null);
    }
    
    @Test
    public void testSummingEnricherWhenNoAndNullSensorValueExplicitValue() {
        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                producersFlags, intSensor, target, 3 /** if null */, 5 /** if none */);
        producer.enrichers().add(cae);
        assertEquals(cae.getAggregate(), 3);
        cae.onEvent(intSensor.newEvent(producer, null));
        assertEquals(cae.getAggregate(), 3);
        cae.onEvent(intSensor.newEvent(producer, 1));
        assertEquals(cae.getAggregate(), 1);
        cae.onEvent(intSensor.newEvent(producer, 7));
        assertEquals(cae.getAggregate(), 7);
    }
    
    @Test
    public void testMultipleProducersSum() {
        List<TestEntity> producers = ImmutableList.of(
                app.createAndManageChild(EntitySpec.create(TestEntity.class)), 
                app.createAndManageChild(EntitySpec.create(TestEntity.class)),
                app.createAndManageChild(EntitySpec.create(TestEntity.class))
                );
        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                ImmutableMap.of("producers", producers), intSensor, target, null, null);
        
        producer.enrichers().add(cae);
        Assert.assertEquals(cae.getAggregate(), null);
        cae.onEvent(intSensor.newEvent(producers.get(2), 1));
        assertEquals(cae.getAggregate(), 1);
        cae.onEvent(intSensor.newEvent(producers.get(0), 3));
        assertEquals(cae.getAggregate(), 4);
        cae.onEvent(intSensor.newEvent(producers.get(1), 3));
        assertEquals(cae.getAggregate(), 7);

    }
    
    @Test
    public void testAveragingEnricherWhenNoAndNullSensorValues() {
        List<TestEntity> producers = ImmutableList.of(
                app.createAndManageChild(EntitySpec.create(TestEntity.class))
                );
        CustomAggregatingEnricher<Integer, Double> cae = CustomAggregatingEnricher.<Integer>newAveragingEnricher(
                ImmutableMap.of("producers", producers), intSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), null, null);
        producer.enrichers().add(cae);
        Assert.assertEquals(cae.getAggregate(), null);
        cae.onEvent(intSensor.newEvent(producers.get(0), null));
        Assert.assertEquals(cae.getAggregate(), null);
    }

    @Test
    public void testAveragingEnricherWhenNoAndNullSensorValuesExplicit() {
        List<TestEntity> producers = ImmutableList.of(
                app.createAndManageChild(EntitySpec.create(TestEntity.class))
                );
        CustomAggregatingEnricher<Integer, Double> cae = CustomAggregatingEnricher.<Integer>newAveragingEnricher(
                ImmutableMap.of("producers", producers), intSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), 3 /** if null */, 5d /** if none */);
        producer.enrichers().add(cae);
        
        assertEquals(cae.getAggregate(), 3d);
        cae.onEvent(intSensor.newEvent(producers.get(0), null));
        assertEquals(cae.getAggregate(), 3d);
        cae.onEvent(intSensor.newEvent(producers.get(0), 4));
        assertEquals(cae.getAggregate(), 4d);
    }

    @Test
    public void testAveragingEnricherWhenNoSensors() {
        List<TestEntity> producers = ImmutableList.of(
                );
        CustomAggregatingEnricher<Integer, Double> cae = CustomAggregatingEnricher.<Integer>newAveragingEnricher(
                ImmutableMap.of("producers", producers), intSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), 3 /** if null */, 5d /** if none */);
        producer.enrichers().add(cae);
        
        assertEquals(cae.getAggregate(), 5d);
    }

    @Test
    public void testMultipleProducersAverage() {
        List<TestEntity> producers = ImmutableList.of(
                app.createAndManageChild(EntitySpec.create(TestEntity.class)), 
                app.createAndManageChild(EntitySpec.create(TestEntity.class)),
                app.createAndManageChild(EntitySpec.create(TestEntity.class))
                );
        CustomAggregatingEnricher<Double, Double> cae = CustomAggregatingEnricher.<Double>newAveragingEnricher(
                ImmutableMap.of("producers", producers),
                doubleSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), null, null);
        
        producer.enrichers().add(cae);
        
        Assert.assertEquals(cae.getAggregate(), null);
        cae.onEvent(doubleSensor.newEvent(producers.get(0), 3d));
        assertEquals(cae.getAggregate(), 3d);
        
        cae.onEvent(doubleSensor.newEvent(producers.get(1), 3d));
        assertEquals(cae.getAggregate(), 3d);
        
        cae.onEvent(doubleSensor.newEvent(producers.get(2), 6d));
        assertEquals(cae.getAggregate(), 4d);

        // change p2's value to 7.5, average increase of 0.5.
        cae.onEvent(doubleSensor.newEvent(producers.get(2), 7.5d));
        assertEquals(cae.getAggregate(), 4.5d);
    }
    
    @Test
    public void testMultipleProducersAverageDefaultingZero() {
        List<TestEntity> producers = ImmutableList.of(
                app.createAndManageChild(EntitySpec.create(TestEntity.class)), 
                app.createAndManageChild(EntitySpec.create(TestEntity.class)),
                app.createAndManageChild(EntitySpec.create(TestEntity.class))
                );
        CustomAggregatingEnricher<Double, Double> cae = CustomAggregatingEnricher.<Double>newAveragingEnricher(
                ImmutableMap.of("producers", producers),
                doubleSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), 0d, 0d);
        
        producer.enrichers().add(cae);
        
        assertEquals(cae.getAggregate(), 0d);
        cae.onEvent(doubleSensor.newEvent(producers.get(0), 3d));
        assertEquals(cae.getAggregate(), 1d);
        
        cae.onEvent(doubleSensor.newEvent(producers.get(1), 3d));
        assertEquals(cae.getAggregate(), 2d);
        
        cae.onEvent(doubleSensor.newEvent(producers.get(2), 6d));
        assertEquals(cae.getAggregate(), 4d);

        // change p2's value to 7.5, average increase of 0.5.
        cae.onEvent(doubleSensor.newEvent(producers.get(2), 7.5d));
        assertEquals(cae.getAggregate(), 4.5d);
    }
    
    @Test
    public void testAddingAndRemovingProducers() {
        TestEntity p1 = app.createAndManageChild(EntitySpec.create(TestEntity.class)); 
        TestEntity p2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                ImmutableMap.of("producers", ImmutableList.of(p1)),
                intSensor, target, null, null);

        producer.enrichers().add(cae);
        Assert.assertEquals(cae.getAggregate(), null);
        
        // Event by initial producer
        cae.onEvent(intSensor.newEvent(p1, 1));
        assertEquals(cae.getAggregate(), 1);
        
        // Add producer and fire event
        cae.addProducer(p2);
        cae.onEvent(intSensor.newEvent(p2, 4));
        assertEquals(cae.getAggregate(), 5);
        
        cae.removeProducer(p2);
        assertEquals(cae.getAggregate(), 1);
    }
    
    @Test
    public void testAggregatesNewMembersOfGroup() {
        try {
            BasicGroup group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
            TestEntity p1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
            TestEntity p2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
            log.debug("created $group and the entities it will contain $p1 $p2");

            CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                    ImmutableMap.of("allMembers", true),
                    intSensor, target, 0, 0);
            group.enrichers().add(cae);

            assertEquals(cae.getAggregate(), 0);

            group.addMember(p1);
            p1.sensors().set(intSensor, 1);
            aggregateIsEventually(cae, 1);

            group.addMember(p2);
            p2.sensors().set(intSensor, 2);
            aggregateIsEventually(cae, 3);

            group.removeMember(p2);
            aggregateIsEventually(cae, 1);
        } catch (Exception e) {
            log.error("testAggregatesNewMembersOfGroup failed (now cleaning up): "+e);
            throw e;
        }
    }
    
    @Test(groups = "Integration")
    public void testAggregatesGroupMembersFiftyTimes() {
        for (int i=0; i<50; i++) {
            log.debug("testAggregatesNewMembersOfGroup $i");
            testAggregatesNewMembersOfGroup();
        }
    }
    
    @Test
    public void testAggregatesExistingMembersOfGroup() {
        BasicGroup group = app.addChild(EntitySpec.create(BasicGroup.class));
        TestEntity p1 = app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class).parent(group)); 
        TestEntity p2 = app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class).parent(group)); 
        group.addMember(p1);
        group.addMember(p2);
        p1.sensors().set(intSensor, 1);
        Entities.manage(group);
        
        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                ImmutableMap.of("allMembers", true),
                intSensor, target, null, null);
        group.enrichers().add(cae);
        
        assertEquals(cae.getAggregate(), 1);

        p2.sensors().set(intSensor, 2);
        aggregateIsEventually(cae, 3);
        
        group.removeMember(p2);
        aggregateIsEventually(cae, 1);
    }
    
    @Test
    public void testAppliesFilterWhenAggregatingMembersOfGroup() {
        BasicGroup group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        TestEntity p1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity p2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity p3 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        group.addMember(p1);
        group.addMember(p2);
        p1.sensors().set(intSensor, 1);
        p2.sensors().set(intSensor, 2);
        p3.sensors().set(intSensor, 4);
        
        final CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newSummingEnricher(
                ImmutableMap.of("allMembers", true, "filter", Predicates.equalTo(p1)),
                intSensor, target, null, null);
        group.enrichers().add(cae);
        
        assertEquals(cae.getAggregate(), 1);
        
        group.addMember(p3);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), new Runnable() {
            @Override
            public void run() {
                assertEquals(cae.getAggregate(), 1);
            }
        });
    }
    
    @Test
    public void testCustomAggregatingFunction() {
        TestEntity p1 = app.createAndManageChild(EntitySpec.create(TestEntity.class)); 
        Function<Collection<Integer>,Integer> aggregator = new Function<Collection<Integer>, Integer>() {
            @Override
            public Integer apply(Collection<Integer> c) {
                int result = 0;
                for (Integer it : c) {
                    result += it*it;
                }
                return result;
            }
        };

        CustomAggregatingEnricher<Integer, Integer> cae = CustomAggregatingEnricher.<Integer, Integer>newEnricher(
                ImmutableMap.of("producers", ImmutableList.of(p1)),
                intSensor, target, aggregator, 0);

        producer.enrichers().add(cae);
        assertEquals(cae.getAggregate(), 0);

        // Event by producer
        cae.onEvent(intSensor.newEvent(p1, 2));
        assertEquals(cae.getAggregate(), 4);
    }


    private void assertEquals(Integer i1, int i2) {
        Assert.assertEquals(i1, (Integer)i2);
     }
    private void assertEquals(Double i1, double i2) {
        Assert.assertEquals(i1, (Double)i2);
    }

    private void aggregateIsEventually(final CustomAggregatingEnricher<Integer, Integer> cae, final int avg) {
        ImmutableMap<String, Long> timeout = ImmutableMap.of("timeout", TIMEOUT_MS);

        Asserts.succeedsEventually(timeout, new Runnable() {
            @Override
            public void run() {
                assertEquals(cae.getAggregate(), avg);
            }
        });
    }

}
