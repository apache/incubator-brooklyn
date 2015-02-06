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
package brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class HaPolicyRebindTest extends RebindTestFixtureWithApp {

    private TestEntity origEntity;
    private SensorEventListener<FailureDescriptor> eventListener;
    private List<SensorEvent<FailureDescriptor>> events;
    
    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        events = Lists.newCopyOnWriteArrayList();
        eventListener = new SensorEventListener<FailureDescriptor>() {
            @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                events.add(event);
            }
        };
    }

    @Test
    public void testServiceRestarterWorksAfterRebind() throws Exception {
        origEntity.addPolicy(PolicySpec.create(ServiceRestarter.class)
                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        
        TestApplication newApp = rebind();
        final TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        
        newEntity.emit(HASensors.ENTITY_FAILED, new FailureDescriptor(origEntity, "simulate failure"));
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(newEntity.getCallHistory(), ImmutableList.of("restart"));
            }});
    }

    @Test
    public void testServiceReplacerWorksAfterRebind() throws Exception {
        Location origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        DynamicCluster origCluster = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntity.class))
                .configure(DynamicCluster.INITIAL_SIZE, 3));
        origApp.start(ImmutableList.<Location>of(origLoc));

        origCluster.addPolicy(PolicySpec.create(ServiceReplacer.class)
                .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));

        // rebind
        TestApplication newApp = rebind();
        final DynamicCluster newCluster = (DynamicCluster) Iterables.find(newApp.getChildren(), Predicates.instanceOf(DynamicCluster.class));

        // stimulate the policy
        final Set<Entity> initialMembers = ImmutableSet.copyOf(newCluster.getMembers());
        final TestEntity e1 = (TestEntity) Iterables.get(initialMembers, 1);
        
        newApp.getManagementContext().getSubscriptionManager().subscribe(e1, HASensors.ENTITY_FAILED, eventListener);
        newApp.getManagementContext().getSubscriptionManager().subscribe(e1, HASensors.ENTITY_RECOVERED, eventListener);
        
        e1.emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));
        
        // Expect e1 to be replaced
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Set<Entity> newMembers = Sets.difference(ImmutableSet.copyOf(newCluster.getMembers()), initialMembers);
                Set<Entity> removedMembers = Sets.difference(initialMembers, ImmutableSet.copyOf(newCluster.getMembers()));
                assertEquals(removedMembers, ImmutableSet.of(e1));
                assertEquals(newMembers.size(), 1);
                assertEquals(((TestEntity)Iterables.getOnlyElement(newMembers)).getCallHistory(), ImmutableList.of("start"));
                
                // TODO e1 not reporting "start" after rebind because callHistory is a field rather than an attribute, so was not persisted
                Asserts.assertEqualsIgnoringOrder(e1.getCallHistory(), ImmutableList.of("stop"));
                assertFalse(Entities.isManaged(e1));
            }});
    }
    
    @Test
    public void testServiceFailureDetectorWorksAfterRebind() throws Exception {
        origEntity.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));

        // rebind
        TestApplication newApp = rebind();
        final TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newApp.getManagementContext().getSubscriptionManager().subscribe(newEntity, HASensors.ENTITY_FAILED, eventListener);

        newEntity.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(newEntity, Lifecycle.RUNNING);
        
        // trigger the failure
        newEntity.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(newEntity), null);
        assertEquals(events.size(), 1, "events="+events);
    }
    
    private void assertHasEventEventually(final Sensor<?> sensor, final Predicate<Object> componentPredicate, final Predicate<? super CharSequence> descriptionPredicate) {
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                assertHasEvent(sensor, componentPredicate, descriptionPredicate);
            }});
    }
    
    private void assertHasEvent(Sensor<?> sensor, Predicate<Object> componentPredicate, Predicate<? super CharSequence> descriptionPredicate) {
        for (SensorEvent<FailureDescriptor> event : events) {
            if (event.getSensor().equals(sensor) && 
                    (componentPredicate == null || componentPredicate.apply(event.getValue().getComponent())) &&
                    (descriptionPredicate == null || descriptionPredicate.apply(event.getValue().getDescription()))) {
                return;
            }
        }
        fail("No matching "+sensor+" event found; events="+events);
    }
}
