package brooklyn.entity

import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicSensor
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.TimeExtras
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import static brooklyn.test.TestUtils.executeUntilSucceeds
import static java.util.concurrent.TimeUnit.SECONDS
import static org.testng.AssertJUnit.assertEquals
import brooklyn.test.entity.TestApplication

public class AbstractEntityTest {
    private AbstractEntity entity
    private static final Sensor sensor = new BasicSensor(Sensor.class, "test.sensor")

    static { TimeExtras.init() }

    @BeforeMethod
    public void setUpTestEntity() throws Exception{
        entity = new LocallyManagedEntity()
    }

    @Test
    public void testAddSensors() throws Exception{
        assertEquals(2, entity.getSensors().size())
        entity.addSensor(sensor)
        assertEquals(3, entity.getSensors().size())
    }

    @Test
    public void testRemoveSensors() throws Exception {
        entity.removeSensor("entity.sensor.added")
        assertEquals(1, entity.getSensors().size())
    }

    @Test
    public void testGetSensor() throws Exception {
        assertEquals("Sensor dynamically added to entity", entity.getSensor("entity.sensor.added").description)
    }

    @Test
    public void testSensorAddedEvent() throws Exception {
        Sensor receivedSensor = null
        entity.subscribe(entity, AbstractEntity.SENSOR_ADDED, new SensorEventListener<Sensor>(){
            void onEvent(SensorEvent<Sensor> e) {receivedSensor = e.getValue()}})

        entity.addSensor(sensor)
        executeUntilSucceeds(timeout: 3*SECONDS, {
            assertEquals sensor, receivedSensor
        })
    }

    @Test
    public void testSensorRemovedEvent() throws Exception {
        Sensor removedSensor = null
        entity.addSensor(sensor)

        entity.subscribe(entity, AbstractEntity.SENSOR_REMOVED, new SensorEventListener<Sensor>(){
            void onEvent(SensorEvent<Sensor> e) {removedSensor = e.getValue()}})

        entity.removeSensor("test.sensor")

        executeUntilSucceeds(timeout: 3*SECONDS, {
            assertEquals sensor, removedSensor
        })
    }
}
