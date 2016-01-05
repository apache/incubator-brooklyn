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

import static org.apache.brooklyn.core.entity.AbstractEntity.CHILD_ADDED;
import static org.apache.brooklyn.core.entity.AbstractEntity.CHILD_REMOVED;
import static org.apache.brooklyn.core.entity.AbstractEntity.CONFIG_KEY_ADDED;
import static org.apache.brooklyn.core.entity.AbstractEntity.CONFIG_KEY_REMOVED;
import static org.apache.brooklyn.core.entity.AbstractEntity.EFFECTOR_ADDED;
import static org.apache.brooklyn.core.entity.AbstractEntity.EFFECTOR_CHANGED;
import static org.apache.brooklyn.core.entity.AbstractEntity.EFFECTOR_REMOVED;
import static org.apache.brooklyn.core.entity.AbstractEntity.GROUP_ADDED;
import static org.apache.brooklyn.core.entity.AbstractEntity.GROUP_REMOVED;
import static org.apache.brooklyn.core.entity.AbstractEntity.LOCATION_ADDED;
import static org.apache.brooklyn.core.entity.AbstractEntity.LOCATION_REMOVED;
import static org.apache.brooklyn.core.entity.AbstractEntity.POLICY_ADDED;
import static org.apache.brooklyn.core.entity.AbstractEntity.POLICY_REMOVED;
import static org.apache.brooklyn.core.entity.AbstractEntity.SENSOR_ADDED;
import static org.apache.brooklyn.core.entity.AbstractEntity.SENSOR_REMOVED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.sensor.BasicSensorEvent;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EntityTypeTest extends BrooklynAppUnitTestSupport {
    private static final AttributeSensor<String> TEST_SENSOR = Sensors.newStringSensor("test.sensor");
    private EntityInternal entity;
    @SuppressWarnings("rawtypes")
    private RecordingSensorEventListener<Sensor> listener;

    public final static Set<Sensor<?>> DEFAULT_SENSORS = ImmutableSet.<Sensor<?>>of(
            SENSOR_ADDED, SENSOR_REMOVED,
            CONFIG_KEY_ADDED, CONFIG_KEY_REMOVED,
            EFFECTOR_ADDED, EFFECTOR_REMOVED, EFFECTOR_CHANGED,
            POLICY_ADDED, POLICY_REMOVED,
            CHILD_ADDED, CHILD_REMOVED,
            LOCATION_ADDED, LOCATION_REMOVED,
            GROUP_ADDED, GROUP_REMOVED); 

    public static class EmptyEntityForTesting extends AbstractEntity {}
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception{
        super.setUp();
        entity = (EntityInternal) app.createAndManageChild(EntitySpec.create(Entity.class, EmptyEntityForTesting.class));
        listener = new RecordingSensorEventListener<>();
        app.subscriptions().subscribe(entity, SENSOR_ADDED, listener);
        app.subscriptions().subscribe(entity, SENSOR_REMOVED, listener);
    }

    @Test
    public void testGetName() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertEquals(entity2.getEntityType().getName(), TestEntity.class.getCanonicalName());
    }
    
    @Test
    public void testGetSimpleName() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertEquals(entity2.getEntityType().getSimpleName(), TestEntity.class.getSimpleName());
    }

    @Test
    public void testGetEffectors() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Set<Effector<?>> effectors = entity2.getEntityType().getEffectors();
        
        class MatchesNamePredicate implements Predicate<Effector<?>> {
            private final String name;
            public MatchesNamePredicate(String name) {
                this.name = name;
            }
            @Override public boolean apply(@Nullable Effector<?> input) {
                return name.equals(input.getName());
            }
        };
        
        assertNotNull(Iterables.find(effectors, new MatchesNamePredicate("myEffector")), null);
        assertNotNull(Iterables.find(effectors, new MatchesNamePredicate("identityEffector")), null);
    }

    @Test
    public void testGetEffector() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Effector<?> effector = entity2.getEntityType().getEffectorByName("myEffector").get();
        Effector<?> effector2 = entity2.getEntityType().getEffectorByName("identityEffector").get();
        assertEquals(effector.getName(), "myEffector");
        assertTrue(effector.getParameters().isEmpty(), "myEffector should have had no params, but had "+effector.getParameters());
        assertEquals(effector2.getName(), "identityEffector");
        assertEquals(effector2.getParameters().size(), 1, "identityEffector should have had one param, but had "+effector2.getParameters());
        assertEquals(Iterables.getOnlyElement(effector2.getParameters()).getName(), "arg", "identityEffector should have had 'arg' param, but had "+effector2.getParameters());
    }

    @Test
    public void testGetEffectorDeprecated() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Effector<?> effector = entity2.getEntityType().getEffectorByName("myEffector").get();
        Effector<?> effector2 = entity2.getEntityType().getEffectorByName("identityEffector").get();
        assertEquals(effector.getName(), "myEffector");
        assertEquals(effector2.getName(), "identityEffector");
    }

    @Test
    public void testCustomSimpleName() throws Exception {
        class CustomTypeNamedEntity extends AbstractEntity {
            private final String typeName;
            @SuppressWarnings("deprecation")
            CustomTypeNamedEntity(Entity parent, String typeName) {
                super(parent);
                this.typeName = typeName;
            }
            @Override protected String getEntityTypeName() {
                return typeName;
            }
        }
        
        CustomTypeNamedEntity entity2 = new CustomTypeNamedEntity(app, "a.b.with space");
        Entities.manage(entity2);
        assertEquals(entity2.getEntityType().getSimpleName(), "with_space");
        
        CustomTypeNamedEntity entity3 = new CustomTypeNamedEntity(app, "a.b.with$dollar");
        Entities.manage(entity3);
        assertEquals(entity3.getEntityType().getSimpleName(), "with_dollar");
        
        CustomTypeNamedEntity entity4 = new CustomTypeNamedEntity(app, "a.nothingafterdot.");
        Entities.manage(entity4);
        assertEquals(entity4.getEntityType().getSimpleName(), "a.nothingafterdot.");
    }
    
    @Test
    public void testGetSensors() throws Exception{
        assertEquals(entity.getEntityType().getSensors(), DEFAULT_SENSORS);
    }

    private void assertEventuallyListenerEventsEqual(@SuppressWarnings("rawtypes") final List<SensorEvent<Sensor>> sensorEvents) {
        assertEventuallyListenerEventsEqual(listener, sensorEvents);
    }

    protected static <T> void assertEventuallyListenerEventsEqual(final RecordingSensorEventListener<T> listener, final List<SensorEvent<T>> sensorEvents) {
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(listener.getEvents(), sensorEvents);
            }
        });
    }
    
    @Test
    public void testAddSensors() throws Exception{
        entity.getMutableEntityType().addSensor(TEST_SENSOR);
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.builder().addAll(DEFAULT_SENSORS).add(TEST_SENSOR).build());
        
        assertEventuallyListenerEventsEqual(ImmutableList.of(BasicSensorEvent.ofUnchecked(SENSOR_ADDED, entity, TEST_SENSOR)));
    }

    @Test
    public void testAddSensorValueThroughEntity() throws Exception{
        entity.sensors().set(TEST_SENSOR, "abc");
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.builder().addAll(DEFAULT_SENSORS).add(TEST_SENSOR).build());
        
        assertEventuallyListenerEventsEqual(ImmutableList.of(BasicSensorEvent.ofUnchecked(SENSOR_ADDED, entity, TEST_SENSOR)));
    }

    @Test
    public void testRemoveSensorThroughEntity() throws Exception{
        entity.sensors().set(TEST_SENSOR, "abc");
        entity.removeAttribute(TEST_SENSOR);
        assertFalse(entity.getEntityType().getSensors().contains(TEST_SENSOR), "sensors="+entity.getEntityType().getSensors()); 
        assertEquals(entity.getAttribute(TEST_SENSOR), null);
        
        assertEventuallyListenerEventsEqual(ImmutableList.of(BasicSensorEvent.ofUnchecked(SENSOR_ADDED, entity, TEST_SENSOR),
            BasicSensorEvent.ofUnchecked(SENSOR_REMOVED, entity, TEST_SENSOR)));
    }

    @Test
    public void testRemoveSensor() throws Exception {
        entity.getMutableEntityType().removeSensor(SENSOR_ADDED);
        assertEquals(entity.getEntityType().getSensors(), 
                MutableSet.builder().addAll(DEFAULT_SENSORS).remove(SENSOR_ADDED).build().asUnmodifiable());
        
        assertEventuallyListenerEventsEqual(ImmutableList.of(
            BasicSensorEvent.ofUnchecked(SENSOR_REMOVED, entity, SENSOR_ADDED)));
    }

    @Test
    public void testRemoveSensors() throws Exception {
        entity.getMutableEntityType().removeSensor(SENSOR_ADDED.getName());
        entity.getMutableEntityType().removeSensor(POLICY_ADDED.getName());
        assertEquals(entity.getEntityType().getSensors(), 
                MutableSet.builder().addAll(DEFAULT_SENSORS).remove(SENSOR_ADDED).remove(POLICY_ADDED).build().asUnmodifiable());

        final RecordingSensorEventListener<?> listener = this.listener;
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(Iterables.size(listener.getEvents()), 2);
            }
        });
        assertEventuallyListenerEventsEqual(ImmutableList.of(
            BasicSensorEvent.ofUnchecked(SENSOR_REMOVED, entity, SENSOR_ADDED),
            BasicSensorEvent.ofUnchecked(SENSOR_REMOVED, entity, POLICY_ADDED)));
    }

    @Test
    public void testGetSensor() throws Exception {
        Sensor<?> sensor = entity.getEntityType().getSensor("entity.sensor.added");
        assertEquals(sensor.getDescription(), "Sensor dynamically added to entity");
        assertEquals(sensor.getName(), "entity.sensor.added");
        
        assertNull(entity.getEntityType().getSensor("does.not.exist"));
    }

    @Test
    public void testHasSensor() throws Exception {
        assertTrue(entity.getEntityType().hasSensor("entity.sensor.added"));
        assertFalse(entity.getEntityType().hasSensor("does.not.exist"));
    }
    
    // Previously EntityDynamicType's constructor when passed `entity` during the entity's construction (!)
    // would pass this to EntityDynamicType.findEffectors, which would do log.warn in some cirumstances,
    // with entity.toString as part of the log message. But if the toString called getConfig() this would 
    // fail because we were still in the middle of constructing the entity.entityType!
    @Test
    public void testEntityDynamicTypeDoesNotCallToStringDuringConstruction() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).impl(EntityWithToStringAccessingConfig.class));
        entity.toString();
    }
    
    public static class EntityWithToStringAccessingConfig extends TestEntityImpl {
        
        // to cause warning to be logged: non-static constant
        public final MethodEffector<Void> NON_STATIC_EFFECTOR = new MethodEffector<Void>(EntityWithToStringAccessingConfig.class, "nonStaticEffector");

        public void nonStaticEffector() {
        }
        
        @Override
        public String toString() {
            return super.toString() + getConfig(CONF_NAME);
        }
    }
}
