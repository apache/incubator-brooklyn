package brooklyn.entity.basic;

import static brooklyn.entity.basic.AbstractEntity.SENSOR_ADDED;
import static brooklyn.entity.basic.AbstractEntity.SENSOR_REMOVED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.SimpleEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class EntityTypeTest {
    private static final AttributeSensor<String> TEST_SENSOR = new BasicAttributeSensor<String>(String.class, "test.sensor");
    private TestApplication app;
    private AbstractEntity entity;
    private EntitySubscriptionTest.RecordingSensorEventListener listener;
    
    @BeforeMethod
    public void setUpTestEntity() throws Exception{
        app = new TestApplication();
        entity = new SimpleEntity(app);
        
        listener = new EntitySubscriptionTest.RecordingSensorEventListener();
        app.getManagementContext().getSubscriptionManager().subscribe(entity, SENSOR_ADDED, listener);
        app.getManagementContext().getSubscriptionManager().subscribe(entity, SENSOR_REMOVED, listener);
    }

    @Test
    public void testGetSensors() throws Exception{
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(SENSOR_ADDED, SENSOR_REMOVED));
    }

    @Test
    public void testAddSensors() throws Exception{
        entity.getMutableEntityType().addSensor(TEST_SENSOR);
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(TEST_SENSOR, SENSOR_ADDED, SENSOR_REMOVED));
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_ADDED, entity, TEST_SENSOR))));
    }

    @Test
    public void testAddSensorValueThroughEntity() throws Exception{
        entity.setAttribute(TEST_SENSOR, "abc");
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(TEST_SENSOR, SENSOR_ADDED, SENSOR_REMOVED));
        
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
        assertEquals(entity.getEntityType().getSensors(), ImmutableSet.of(SENSOR_REMOVED));
        
        TestUtils.assertEventually(
                Suppliers.ofInstance(listener.events), 
                Predicates.equalTo(ImmutableList.of(new BasicSensorEvent(SENSOR_REMOVED, entity, SENSOR_ADDED))));
    }

    @Test
    public void testRemoveSensors() throws Exception {
        entity.getMutableEntityType().removeSensor(SENSOR_ADDED.getName());
        assertEquals(entity.getEntityType().getSensors(), ImmutableSet.of(SENSOR_REMOVED));
        
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
