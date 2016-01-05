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
package org.apache.brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import org.apache.brooklyn.core.entity.trait.FailingEntity;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.QuorumCheck;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.policy.ha.HASensors.FailureDescriptor;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ServiceReplacerTest {

    private static final Logger log = LoggerFactory.getLogger(ServiceReplacerTest.class);
    
    private ManagementContext managementContext;
    private TestApplication app;
    private SimulatedLocation loc;
    private SensorEventListener<Object> eventListener;
    private List<SensorEvent<?>> events;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        events = Lists.newCopyOnWriteArrayList();
        eventListener = new SensorEventListener<Object>() {
            @Override public void onEvent(SensorEvent<Object> event) {
                events.add(event);
            }
        };
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testReplacesFailedMember() throws Exception {
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntity.class))
                .configure(DynamicCluster.INITIAL_SIZE, 3));
        app.start(ImmutableList.<Location>of(loc));

        ServiceReplacer policy = new ServiceReplacer(new ConfigBag().configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        cluster.policies().add(policy);

        final Set<Entity> initialMembers = ImmutableSet.copyOf(cluster.getMembers());
        final TestEntity e1 = (TestEntity) Iterables.get(initialMembers, 1);
        
        e1.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));
        
        // Expect e1 to be replaced
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Set<Entity> newMembers = Sets.difference(ImmutableSet.copyOf(cluster.getMembers()), initialMembers);
                Set<Entity> removedMembers = Sets.difference(initialMembers, ImmutableSet.copyOf(cluster.getMembers()));
                assertEquals(removedMembers, ImmutableSet.of(e1));
                assertEquals(newMembers.size(), 1);
                assertEquals(((TestEntity)Iterables.getOnlyElement(newMembers)).getCallHistory(), ImmutableList.of("start"));
                assertEquals(e1.getCallHistory(), ImmutableList.of("start", "stop"));
                assertFalse(Entities.isManaged(e1));
            }});
    }

    @Test(invocationCount=100)
    public void testSetsOnFireWhenFailToReplaceMemberManyTimes() throws Exception {
        testSetsOnFireWhenFailToReplaceMember();
    }
    
    // fails the startup of the replacement entity (but not the original). 
    @Test
    public void testSetsOnFireWhenFailToReplaceMember() throws Exception {
        app.subscriptions().subscribe(null, ServiceReplacer.ENTITY_REPLACEMENT_FAILED, eventListener);
        
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(FailingEntity.class)
                        .configure(FailingEntity.FAIL_ON_START_CONDITION, predicateOnlyTrueForCallAtOrAfter(2)))
                .configure(DynamicCluster.INITIAL_SIZE, 1)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true)
                .configure(ComputeServiceIndicatorsFromChildrenAndMembers.UP_QUORUM_CHECK, QuorumCheck.QuorumChecks.alwaysTrue())
                .configure(ComputeServiceIndicatorsFromChildrenAndMembers.RUNNING_QUORUM_CHECK, QuorumCheck.QuorumChecks.alwaysTrue()));
        app.start(ImmutableList.<Location>of(loc));
        
        // should not be on fire
        Assert.assertNotEquals(cluster.getAttribute(Attributes.SERVICE_STATE_ACTUAL), Lifecycle.ON_FIRE);
        // and should eventually be running
        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        log.info("started "+app+" for "+JavaClassNames.niceClassAndMethod());
        
        ServiceReplacer policy = new ServiceReplacer(new ConfigBag().configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        cluster.policies().add(policy);
        
        final Set<Entity> initialMembers = ImmutableSet.copyOf(cluster.getMembers());
        final TestEntity e1 = (TestEntity) Iterables.get(initialMembers, 0);
        
        e1.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));

        // Expect cluster to go on-fire when fails to start replacement
        // Note that we've set up-quorum and running-quorum to be "alwaysTrue" so that we don't get a transient onFire
        // when the failed node fails to start (but before it has been removed from the group to be put in quarantine).
        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);

        // Expect to have the second failed entity still kicking around as proof (in quarantine)
        // The cluster should NOT go on fire until after the 2nd failure
        Iterable<Entity> members = Iterables.filter(managementContext.getEntityManager().getEntities(), Predicates.instanceOf(FailingEntity.class));
        assertEquals(Iterables.size(members), 2);

        // e2 failed to start, so it won't have called stop on e1
        TestEntity e2 = (TestEntity) Iterables.getOnlyElement(Sets.difference(ImmutableSet.copyOf(members), initialMembers));
        assertEquals(e1.getCallHistory(), ImmutableList.of("start"), "e1.history="+e1.getCallHistory());
        assertEquals(e2.getCallHistory(), ImmutableList.of("start"), "e2.history="+e2.getCallHistory());

        // And will have received notification event about it
        assertEventuallyHasEntityReplacementFailedEvent(cluster);
    }
    
    @Test(groups="Integration") // has a 1 second wait
    public void testDoesNotOnFireWhenFailToReplaceMember() throws Exception {
        app.subscriptions().subscribe(null, ServiceReplacer.ENTITY_REPLACEMENT_FAILED, eventListener);
        
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(FailingEntity.class)
                        .configure(FailingEntity.FAIL_ON_START_CONDITION, predicateOnlyTrueForCallAtOrAfter(2)))
                .configure(DynamicCluster.INITIAL_SIZE, 1)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true));
        app.start(ImmutableList.<Location>of(loc));
        
        ServiceReplacer policy = new ServiceReplacer(new ConfigBag()
                .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED)
                .configure(ServiceReplacer.SET_ON_FIRE_ON_FAILURE, false));
        cluster.policies().add(policy);
        
        final Set<Entity> initialMembers = ImmutableSet.copyOf(cluster.getMembers());
        final TestEntity e1 = (TestEntity) Iterables.get(initialMembers, 0);
        
        e1.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));

        // Configured to not mark cluster as on fire
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertNotEquals(cluster.getAttribute(Attributes.SERVICE_STATE_ACTUAL), Lifecycle.ON_FIRE);
            }});
        
        // And will have received notification event about it
        assertEventuallyHasEntityReplacementFailedEvent(cluster);
    }

    @Test(groups="Integration")  // 1s wait
    public void testStopFailureOfOldEntityDoesNotSetClusterOnFire() throws Exception {
        app.subscriptions().subscribe(null, ServiceReplacer.ENTITY_REPLACEMENT_FAILED, eventListener);
        
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(FailingEntity.class)
                        .configure(FailingEntity.FAIL_ON_STOP_CONDITION, predicateOnlyTrueForCallAt(1)))
                .configure(DynamicCluster.INITIAL_SIZE, 2));
        app.start(ImmutableList.<Location>of(loc));
        
        cluster.policies().add(PolicySpec.create(ServiceReplacer.class)
                .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        
        final Set<Entity> initialMembers = ImmutableSet.copyOf(cluster.getMembers());
        final TestEntity e1 = (TestEntity) Iterables.get(initialMembers, 0);
        
        e1.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));

        // Expect e1 to be replaced
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Set<Entity> newMembers = Sets.difference(ImmutableSet.copyOf(cluster.getMembers()), initialMembers);
                Set<Entity> removedMembers = Sets.difference(initialMembers, ImmutableSet.copyOf(cluster.getMembers()));
                assertEquals(removedMembers, ImmutableSet.of(e1));
                assertEquals(newMembers.size(), 1);
                assertEquals(((TestEntity)Iterables.getOnlyElement(newMembers)).getCallHistory(), ImmutableList.of("start"));
                assertEquals(e1.getCallHistory(), ImmutableList.of("start", "stop"));
                assertFalse(Entities.isManaged(e1));
            }});

        // Failure to stop the failed member should not cause "on-fire" of cluster
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertNotEquals(cluster.getAttribute(Attributes.SERVICE_STATE_ACTUAL), Lifecycle.ON_FIRE);
            }});
    }

    /**
     * If we keep on getting failure reports, never managing to replace the failed node, then don't keep trying
     * (i.e. avoid infinite loop).
     * 
     * TODO This code + configuration needs some work; it's not testing quite the scenarios that I
     * was thinking of!
     * I saw problem where a node failed, and the replacements failed, and we ended up trying thousands of times.
     * (describing this scenario is made more complex by me having temporarily disabled the cluster from 
     * removing failed members, for debugging purposes!)
     * Imagine these two scenarios:
     * <ol>
     *   <li>Entity fails during call to start().
     *       Here, the cluster removes it as a member (either unmanages it or puts it in quarantine)
     *       So the ENTITY_FAILED is ignored because the entity is not a member at that point.
     *   <li>Entity returns from start(), but quickly goes to service-down.
     *       Here we'll keep trying to replace that entity. Depending how long that takes, we'll either 
     *       enter a horrible infinite loop, or we'll just provision a huge number of VMs over a long 
     *       time period.
     *       Unfortunately this scenario is not catered for in the code yet.
     * </ol>
     */
    @Test(groups="Integration") // because takes 1.2 seconds
    public void testAbandonsReplacementAfterNumFailures() throws Exception {
        app.subscriptions().subscribe(null, ServiceReplacer.ENTITY_REPLACEMENT_FAILED, eventListener);
        
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(FailingEntity.class)
                        .configure(FailingEntity.FAIL_ON_START_CONDITION, predicateOnlyTrueForCallAtOrAfter(11)))
                .configure(DynamicCluster.INITIAL_SIZE, 10)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true));
        app.start(ImmutableList.<Location>of(loc));
        
        ServiceReplacer policy = new ServiceReplacer(new ConfigBag()
                .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED)
                .configure(ServiceReplacer.FAIL_ON_NUM_RECURRING_FAILURES, 3));
        cluster.policies().add(policy);

        final Set<Entity> initialMembers = ImmutableSet.copyOf(cluster.getMembers());
        for (int i = 0; i < 5; i++) {
            final int counter = i+1;
            EntityInternal entity = (EntityInternal) Iterables.get(initialMembers, i);
            entity.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(entity, "simulate failure"));
            if (i <= 3) {
                Asserts.succeedsEventually(new Runnable() {
                    @Override public void run() {
                        Set<FailingEntity> all = ImmutableSet.copyOf(Iterables.filter(managementContext.getEntityManager().getEntities(), FailingEntity.class));
                        Set<FailingEntity> replacements = Sets.difference(all, initialMembers);
                        Set<?> replacementMembers = Sets.intersection(ImmutableSet.of(cluster.getMembers()), replacements);
                        assertTrue(replacementMembers.isEmpty());
                        assertEquals(replacements.size(), counter);
                    }});
            } else {
                Asserts.succeedsContinually(new Runnable() {
                    @Override public void run() {
                        Set<FailingEntity> all = ImmutableSet.copyOf(Iterables.filter(managementContext.getEntityManager().getEntities(), FailingEntity.class));
                        Set<FailingEntity> replacements = Sets.difference(all, initialMembers);
                        assertEquals(replacements.size(), 4);
                    }});
            }
        }
    }


    private Predicate<Object> predicateOnlyTrueForCallAt(final int callNumber) {
        return predicateOnlyTrueForCallRange(callNumber, callNumber);
    }

    private Predicate<Object> predicateOnlyTrueForCallAtOrAfter(final int callLowerNumber) {
        return predicateOnlyTrueForCallRange(callLowerNumber, Integer.MAX_VALUE);
    }
    
    private Predicate<Object> predicateOnlyTrueForCallRange(final int callLowerNumber, final int callUpperNumber) {
        return new Predicate<Object>() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override public boolean apply(Object input) {
                int num = counter.incrementAndGet();
                return num >= callLowerNumber && num <= callUpperNumber;
            }
        };
    }

    private void assertEventuallyHasEntityReplacementFailedEvent(final Entity expectedCluster) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(Iterables.getOnlyElement(events).getSensor(), ServiceReplacer.ENTITY_REPLACEMENT_FAILED, "events="+events);
                assertEquals(Iterables.getOnlyElement(events).getSource(), expectedCluster, "events="+events);
                assertEquals(((FailureDescriptor)Iterables.getOnlyElement(events).getValue()).getComponent(), expectedCluster, "events="+events);
            }});
    }
}
