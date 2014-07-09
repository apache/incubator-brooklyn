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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class CustomAggregatingEnricherTest extends BrooklynAppUnitTestSupport {

    public static final Logger log = LoggerFactory.getLogger(CustomAggregatingEnricherTest.class);
            
    private static final long TIMEOUT_MS = 10*1000;
    private static final long SHORT_WAIT_MS = 250;
    
    TestEntity entity;
    SimulatedLocation loc;
    
    AttributeSensor<Integer> intSensor;
    AttributeSensor<Double> doubleSensor;
    AttributeSensor<Integer> target;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor");
        doubleSensor = new BasicAttributeSensor<Double>(Double.class, "double sensor");
        target = new BasicAttributeSensor<Integer>(Integer.class, "target sensor");
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        app.start(ImmutableList.of(loc));
    }
    
    @Test
    public void testSummingEnricherWithNoProducersDefaultsToNull() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromChildren()
                .build());
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, target, null);
    }

    @Test
    public void testSummingEnricherWithNoProducers() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromChildren()
                .defaultValueForUnreportedSensors(11)
                .valueToReportIfNoSensors(40)
                .build());
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 40);
    }

    @Test
    public void testSummingEnricherWhenNoSensorValuesYet() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromHardcodedProducers(ImmutableList.of(entity))
                .defaultValueForUnreportedSensors(11)
                .valueToReportIfNoSensors(40)
                .build());
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 11);
    }

    @Test
    public void testSummingEnricherWhenNoSensorValuesYetDefaultsToNull() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromHardcodedProducers(ImmutableList.of(entity))
                .build());
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, target, null);
    }

    @Test
    public void testSummingEnricherWithNoValues() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromHardcodedProducers(ImmutableList.of(entity))
                .build());
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, target, null);
    }
    
    @Test
    public void testSummingEnricherWithOneValue() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromHardcodedProducers(ImmutableList.of(entity))
                .build());

        entity.setAttribute(intSensor, 1);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);
    }
    
    @Test
    public void testSummingEnricherWhenNullSensorValue() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromHardcodedProducers(ImmutableList.of(entity))
                .build());

        entity.setAttribute(intSensor, null);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, target, null);
    }
    
    @Test
    public void testSummingEnricherWhenDefaultValueForUnreportedSensors() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromHardcodedProducers(ImmutableList.of(entity))
                .defaultValueForUnreportedSensors(3)
                .valueToReportIfNoSensors(5)
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 3);
        
        entity.setAttribute(intSensor, null);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, target, 3);
        
        entity.setAttribute(intSensor, 1);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);
        
        entity.setAttribute(intSensor, 7);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 7);
    }
    
    @Test
    public void testMultipleProducersSum() {
        TestEntity producer1 = app.createAndManageChild(EntitySpec.create(TestEntity.class)); 
        TestEntity producer2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity producer3 = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromHardcodedProducers(ImmutableList.of(producer1, producer2, producer3))
                .build());

        producer3.setAttribute(intSensor, 1);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);

        producer1.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 3);

        producer2.setAttribute(intSensor, 4);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 7);
    }
    
    @Test
    public void testAveragingEnricherWhenNoAndNullSensorValues() {
        TestEntity producer1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(doubleSensor)
                .computingAverage()
                .fromHardcodedProducers(ImmutableList.of(producer1))
                .build());

        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, doubleSensor, null);
        
        producer1.setAttribute(intSensor, null);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, doubleSensor, null);
    }

    @Test
    public void testAveragingEnricherWhenDefaultValueForUnreportedSensors() {
        TestEntity producer1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(doubleSensor)
                .computingAverage()
                .fromHardcodedProducers(ImmutableList.of(producer1))
                .defaultValueForUnreportedSensors(3)
                .valueToReportIfNoSensors(5)
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 3d);
        
        producer1.setAttribute(intSensor, null);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, doubleSensor, 3d);
        
        producer1.setAttribute(intSensor, 4);
        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 4d);
    }

    @Test
    public void testAveragingEnricherWhenNoSensors() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(doubleSensor)
                .computingAverage()
                .fromChildren()
                .defaultValueForUnreportedSensors(3)
                .valueToReportIfNoSensors(5)
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 5d);
    }

    @Test
    public void testAveragingEnricherWhenNoProducersDefaultsToNull() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(doubleSensor)
                .computingAverage()
                .fromChildren()
                .build());

        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, doubleSensor, null);
    }

    @Test
    public void testMultipleProducersAverage() {
        TestEntity producer1 = app.createAndManageChild(EntitySpec.create(TestEntity.class)); 
        TestEntity producer2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity producer3 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(doubleSensor)
                .computingAverage()
                .fromHardcodedProducers(ImmutableList.of(producer1, producer2, producer3))
                .build());

        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 50), entity, doubleSensor, null);

        producer1.setAttribute(intSensor, 3);
        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 3d);
        
        producer2.setAttribute(intSensor, 1);
        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 2d);

        producer3.setAttribute(intSensor, 5);
        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 3d);

        producer2.setAttribute(intSensor, 4);
        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 4d);
    }
    
    @Test
    public void testMultipleProducersAverageDefaultingZero() {
        TestEntity producer1 = app.createAndManageChild(EntitySpec.create(TestEntity.class)); 
        TestEntity producer2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity producer3 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(doubleSensor)
                .computingAverage()
                .fromHardcodedProducers(ImmutableList.of(producer1, producer2, producer3))
                .defaultValueForUnreportedSensors(0)
                .valueToReportIfNoSensors(0)
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 0d);

        producer1.setAttribute(intSensor, 3);
        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 1d);

        producer2.setAttribute(intSensor, 3);
        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 2d);

        producer3.setAttribute(intSensor, 3);
        EntityTestUtils.assertAttributeEqualsEventually(entity, doubleSensor, 3d);
    }
    
    @Test
    public void testAggregatesNewMembersOfGroup() {
        BasicGroup group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        TestEntity p1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity p2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        log.debug("created {} and the entities it will contain {} {}", new Object[] {group, p1, p2});

        group.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromMembers()
                .defaultValueForUnreportedSensors(0)
                .valueToReportIfNoSensors(0)
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(group, target, 0);

        group.addMember(p1);
        p1.setAttribute(intSensor, 1);
        EntityTestUtils.assertAttributeEqualsEventually(group, target, 1);

        group.addMember(p2);
        p2.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsEventually(group, target, 3);

        group.removeMember(p2);
        EntityTestUtils.assertAttributeEqualsEventually(group, target, 1);
    }
    
    @Test(groups = "Integration", invocationCount=50)
    public void testAggregatesGroupMembersFiftyTimes() {
        testAggregatesNewMembersOfGroup();
    }
    
    @Test
    public void testAggregatesExistingMembersOfGroup() {
        BasicGroup group = app.addChild(EntitySpec.create(BasicGroup.class));
        TestEntity p1 = app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class).parent(group)); 
        TestEntity p2 = app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class).parent(group)); 
        group.addMember(p1);
        group.addMember(p2);
        p1.setAttribute(intSensor, 1);
        Entities.manage(group);
        
        group.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromMembers()
                .build());


        EntityTestUtils.assertAttributeEqualsEventually(group, target, 1);

        p2.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsEventually(group, target, 3);
        
        group.removeMember(p2);
        EntityTestUtils.assertAttributeEqualsEventually(group, target, 1);
    }
    
    @Test
    public void testAggregatesMembersOfProducer() {
        BasicGroup group = app.addChild(EntitySpec.create(BasicGroup.class));
        TestEntity p1 = app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class).parent(group)); 
        TestEntity p2 = app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(TestEntity.class).parent(group)); 
        group.addMember(p1);
        group.addMember(p2);
        p1.setAttribute(intSensor, 1);
        Entities.manage(group);
        
        app.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .from(group)
                .fromMembers()
                .build());


        EntityTestUtils.assertAttributeEqualsEventually(app, target, 1);

        p2.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsEventually(app, target, 3);
        
        group.removeMember(p2);
        EntityTestUtils.assertAttributeEqualsEventually(app, target, 1);
    }
    
    @Test
    public void testAppliesFilterWhenAggregatingMembersOfGroup() {
        BasicGroup group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        TestEntity p1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity p2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity p3 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        group.addMember(p1);
        group.addMember(p2);
        p1.setAttribute(intSensor, 1);
        p2.setAttribute(intSensor, 2);
        p3.setAttribute(intSensor, 4);
        
        group.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromMembers()
                .entityFilter(Predicates.equalTo((Entity)p1))
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(group, target, 1);
        
        group.addMember(p3);
        EntityTestUtils.assertAttributeEqualsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), group, target, 1);
    }
    
    @Test
    public void testAggregatesNewChidren() {
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromChildren()
                .defaultValueForUnreportedSensors(0)
                .valueToReportIfNoSensors(0)
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 0);

        TestEntity p1 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        p1.setAttribute(intSensor, 1);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);

        TestEntity p2 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        p2.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 3);

        Entities.unmanage(p2);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);
    }
    
    @Test
    public void testAggregatesExistingChildren() {
        TestEntity p1 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity p2 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        p1.setAttribute(intSensor, 1);
        
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromChildren()
                .build());


        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);

        p2.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 3);
        
        Entities.unmanage(p2);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);
    }
    
    @Test
    public void testAggregatesChildrenOfProducer() {
        TestEntity p1 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity p2 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        p1.setAttribute(intSensor, 1);
        
        app.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .from(entity)
                .fromChildren()
                .build());


        EntityTestUtils.assertAttributeEqualsEventually(app, target, 1);

        p2.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsEventually(app, target, 3);
        
        Entities.unmanage(p2);
        EntityTestUtils.assertAttributeEqualsEventually(app, target, 1);
    }
    
    @Test
    public void testAppliesFilterWhenAggregatingChildrenOfGroup() {
        TestEntity p1 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        p1.setAttribute(intSensor, 1);
        
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computingSum()
                .fromChildren()
                .entityFilter(Predicates.equalTo((Entity)p1))
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);
        
        TestEntity p2 = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        p2.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), entity, target, 1);
    }
    
    @Test
    public void testCustomAggregatingFunction() {
        TestEntity producer1 = app.createAndManageChild(EntitySpec.create(TestEntity.class)); 
        Function<Collection<Integer>,Integer> aggregator = new Function<Collection<Integer>, Integer>() {
            public Integer apply(Collection<Integer> input) { 
                int result = 1;
                for (Integer in : input) result += in*in;
                return result;
            }
        };
        
        entity.addEnricher(Enrichers.builder()
                .aggregating(intSensor)
                .publishing(target)
                .computing(aggregator)
                .fromHardcodedProducers(ImmutableList.of(producer1))
                .defaultValueForUnreportedSensors(0)
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 1);
        
        // Event by producer
        producer1.setAttribute(intSensor, 2);
        EntityTestUtils.assertAttributeEqualsEventually(entity, target, 5);
    }
}
