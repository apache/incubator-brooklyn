package brooklyn.entity.basic;

import static brooklyn.test.TestUtils.executeUntilSucceeds;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.LocallyManagedEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class EntityTypeTest {
    private static final AttributeSensor<String> TEST_SENSOR = new BasicAttributeSensor<String>(String.class, "test.sensor");
    private AbstractEntity entity;

    @BeforeMethod
    public void setUpTestEntity() throws Exception{
        entity = new LocallyManagedEntity();
    }

    @Test
    public void testGetSensors() throws Exception{
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(AbstractEntity.SENSOR_ADDED, AbstractEntity.SENSOR_REMOVED));
    }

    @Test
    public void testAddSensors() throws Exception{
        entity.getMutableEntityType().addSensor(TEST_SENSOR);
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(TEST_SENSOR, AbstractEntity.SENSOR_ADDED, AbstractEntity.SENSOR_REMOVED));
    }

    @Test
    public void testAddSensorValueThroughEntity() throws Exception{
        entity.setAttribute(TEST_SENSOR, "abc");
        assertEquals(entity.getEntityType().getSensors(), 
                ImmutableSet.of(TEST_SENSOR, AbstractEntity.SENSOR_ADDED, AbstractEntity.SENSOR_REMOVED));
    }

    @Test
    public void testRemoveSensor() throws Exception {
        entity.getMutableEntityType().removeSensor(AbstractEntity.SENSOR_ADDED);
        assertEquals(entity.getEntityType().getSensors(), ImmutableSet.of(AbstractEntity.SENSOR_REMOVED));
    }

    @Test
    public void testRemoveSensors() throws Exception {
        entity.getMutableEntityType().removeSensor("entity.sensor.added");
        assertEquals(entity.getEntityType().getSensors(), ImmutableSet.of(AbstractEntity.SENSOR_REMOVED));
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

    @Test
    public void testSensorAddedEvent() throws Exception {
        final AtomicReference<Sensor<?>> receivedSensor = new AtomicReference<Sensor<?>>();
        entity.subscribe(entity, AbstractEntity.SENSOR_ADDED, new SensorEventListener<Sensor>(){
            public void onEvent(SensorEvent<Sensor> e) {receivedSensor.set(e.getValue());}});

        entity.getMutableEntityType().addSensor(TEST_SENSOR);
        executeUntilSucceeds(ImmutableMap.of("timeout", 3*1000), new Runnable() { public void run() {
            assertEquals(receivedSensor.get(), TEST_SENSOR);
        }});
    }

    @Test
    public void testSensorRemovedEvent() throws Exception {
        final AtomicReference<Sensor<?>> removedSensor = new AtomicReference<Sensor<?>>();
        entity.getMutableEntityType().addSensor(TEST_SENSOR);

        entity.subscribe(entity, AbstractEntity.SENSOR_REMOVED, new SensorEventListener<Sensor>() {
            public void onEvent(SensorEvent<Sensor> e) {removedSensor.set(e.getValue());}});

        entity.getMutableEntityType().removeSensor("test.sensor");

        executeUntilSucceeds(ImmutableMap.of("timeout", 3*1000), new Runnable() { public void run() {
            assertEquals(TEST_SENSOR, removedSensor.get());
        }});
    }
}
