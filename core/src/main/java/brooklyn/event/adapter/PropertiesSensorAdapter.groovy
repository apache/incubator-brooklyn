package brooklyn.event.adapter

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.basic.LogSensor
import brooklyn.event.basic.BasicSensorEvent

/**
 * A {@link SensorAdapter} that observes changes to the attributes in a {@link Map} and registers events
 * on an {@link EntityLocal} entity for particular {@link Sensor}s.
 * 
 * @see SensorAdapter
 * @see JmxSensorAdapter
 */
public class PropertiesSensorAdapter implements SensorAdapter {
    private static final Logger log = LoggerFactory.getLogger(SensorAdapter.class);
 
    private final EntityLocal entity;
    private final ObservableMap attributes;
    private final ObservableMap logs;
    
    public PropertiesSensorAdapter(EntityLocal entity, Map attributes = [:], Map logs = [:]) {
        this.entity = entity;
        this.attributes = new ObservableMap(attributes);
        this.logs = new ObservableMap(logs);
    }
    
    public <T> void addSensor(AttributeSensor<T> attribute, T initialValue) {
        attributes.put(attribute.getName(), attribute);
        update(attribute, initialValue)
    }
    
    public <T> void addSensor(LogSensor<T> logs, T initialValue) {
        logs.put(attribute.getName(), logs);
        update(logs, initialValue)
    }
    
    public void subscribe(String sensorName) {
        Sensor<?> sensor = getSensor sensorName
        subscribe sensor
    }
 
    public <T> void subscribe(final Sensor<T> sensor) {
        properties.addPropertyChangeListener sensorName, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent change) {
				//FIXME ENGR-1458  sensor.newEvent(change.newValue)  does feel better as second arg here 
				//(in light of comments on EntityLocal; we don't know the sensor type, is it an AttributeChanged?
				//or, say we one day had ConfigChanged?)
                entity.raiseEvent sensor, change.newValue
            }
        };
    }
    
    public <T> T poll(String sensorName) {
        Sensor<T> sensor = getSensor sensorName
        poll sensor
    }
 
    public <T> T poll(Sensor<T> sensor) {
        def value = entity.attributes[sensorName]
        entity.raiseEvent sensor, value
        value
    }
    
    public <T> T update(Sensor<T> sensor, T newValue) {
        log.debug "sensor $sensor field {} set to {}", sensor.name, newValue
        def oldValue = entity.properties[sensor.getName()]
        entity.properties[sensor.getName()] = newValue
        entity.updateAttribute sensor, newValue
        entity.raiseEvent sensor, newValue
        oldValue
    }
    
    private Sensor<?> getSensor(String sensorName) {
        entity.entityClass.sensors.find { it.name.equals sensorName }
    }
}
