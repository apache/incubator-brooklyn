package brooklyn.entity.basic;

import static brooklyn.entity.basic.AbstractEntity.POLICY_ADDED;
import static brooklyn.entity.basic.AbstractEntity.POLICY_REMOVED;
import static brooklyn.entity.basic.AbstractEntity.SENSOR_ADDED;
import static brooklyn.entity.basic.AbstractEntity.SENSOR_REMOVED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import javax.annotation.Nullable;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EntityTypeTest {
    private static final AttributeSensor<String> TEST_SENSOR = new BasicAttributeSensor<String>(String.class, "test.sensor");
    private TestApplication app;
    private AbstractEntity entity;
    private EntitySubscriptionTest.RecordingSensorEventListener listener;
    
    @BeforeMethod
    public void setUpTestEntity() throws Exception{
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = new AbstractEntity(app) {};
        Entities.startManagement(entity);
        
        listener = new EntitySubscriptionTest.RecordingSensorEventListener();
        app.getSubscriptionContext().subscribe(entity, SENSOR_ADDED, listener);
        app.getSubscriptionContext().subscribe(entity, SENSOR_REMOVED, listener);
    }

    @Test
    public void testGetName() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        assertEquals(entity2.getEntityType().getName(), TestEntity.class.getCanonicalName());
    }
    
    @Test
    public void testGetSimpleName() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        assertEquals(entity2.getEntityType().getSimpleName(), TestEntity.class.getSimpleName());
    }

    @Test
    public void testGetEffectors() throws Exception {
        TestEntity entity2 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
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
        TestEntity entity2 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        Effector<?> effector = entity2.getEntityType().getEffector("myEffector");
        Effector<?> effector2 = entity2.getEntityType().getEffector("identityEffector", Object.class);
        assertEquals(effector.getName(), "myEffector");
        assertEquals(effector2.getName(), "identityEffector");
    }

    @Test
    public void testCustomSimpleName() throws Exception {
        class CustomTypeNamedEntity extends AbstractEntity {
            private final String typeName;
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
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(SENSOR_ADDED, SENSOR_REMOVED, POLICY_ADDED, POLICY_REMOVED));
    }

    @Test
    public void testAddSensors() throws Exception{
        entity.getMutableEntityType().addSensor(TEST_SENSOR);
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(TEST_SENSOR, SENSOR_ADDED, SENSOR_REMOVED, POLICY_ADDED, POLICY_REMOVED));
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_ADDED, entity, TEST_SENSOR))));
    }

    @Test
    public void testAddSensorValueThroughEntity() throws Exception{
        entity.setAttribute(TEST_SENSOR, "abc");
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(TEST_SENSOR, SENSOR_ADDED, SENSOR_REMOVED, POLICY_ADDED, POLICY_REMOVED));
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_ADDED, entity, TEST_SENSOR))));
    }

    @Test
    public void testRemoveSensorThroughEntity() throws Exception{
        entity.setAttribute(TEST_SENSOR, "abc");
        entity.removeAttribute(TEST_SENSOR);
        assertFalse(entity.getEntityType().getSensors().contains(TEST_SENSOR), "sensors="+entity.getEntityType().getSensors()); 
        assertEquals(entity.getAttribute(TEST_SENSOR), null);
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(
                        new BasicSensorEvent(SENSOR_ADDED, entity, TEST_SENSOR), 
                        new BasicSensorEvent(SENSOR_REMOVED, entity, TEST_SENSOR))));
    }

    @Test
    public void testRemoveSensor() throws Exception {
        entity.getMutableEntityType().removeSensor(SENSOR_ADDED);
        assertEquals(entity.getEntityType().getSensors(), ImmutableSet.of(SENSOR_REMOVED, POLICY_ADDED, POLICY_REMOVED));
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_REMOVED, entity, SENSOR_ADDED))));
    }

    @Test
    public void testRemoveSensors() throws Exception {
        entity.getMutableEntityType().removeSensor(SENSOR_ADDED.getName());
        assertEquals(entity.getEntityType().getSensors(), ImmutableSet.of(SENSOR_REMOVED, POLICY_ADDED, POLICY_REMOVED));
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_REMOVED, entity, SENSOR_ADDED))));
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
}
