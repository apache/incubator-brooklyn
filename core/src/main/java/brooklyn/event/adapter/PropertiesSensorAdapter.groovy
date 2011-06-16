package brooklyn.event.adapter

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

import brooklyn.entity.Entity
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.basic.SensorEvent

/**
 * A {@link SensorAdapter} that observes changes to the attributes in a {@link Map} and registers events
 * on an {@link Entity} for particuular {@link Sensor}s.
 * 
 * @see SensorAdapter
 * @see JmxSensorAdapter
 */
public class PropertiesSensorAdapter implements SensorAdapter {
    private final Entity entity;
    private final ObservableMap properties;
    
    public PropertiesSensorAdapter(Entity entity, Map<?, ?> properties) {
        this.entity = entity;
        this.properties = new ObservableMap(properties);
    }
    
    public <T> void subscribe(String sensorName) {
        Sensor<T> sensor = getSensor(sensorName)
        subscribe(sensor)
    }
 
    public <T> void subscribe(final Sensor<T> sensor) {
        properties.addPropertyChangeListener sensorName, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent change) {
                SensorEvent<?> event = new SensorEvent(sensor, entity, change.getNewvalue())
                entity.raiseEvent event
            }
        };
    }
    
    public <T> T poll(String sensorName) {
        Sensor<T> sensor = getSensor(sensorName)
        poll(sensor)
    }
 
    public <T> T poll(Sensor<T> sensor) {
        def value = entity.attributes[sensorName]
        SensorEvent<?> event = new SensorEvent(sensor, entity, value)
        entity.raiseEvent event
        value
    }
    
    private <T> Sensor<T> getSensor(String sensorName) {
        entity.getEntityClass().getSensors() find { s -> s.name.equals(sensorName) }
    }
}
