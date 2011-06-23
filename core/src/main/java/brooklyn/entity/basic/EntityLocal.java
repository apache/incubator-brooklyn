package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;

public interface EntityLocal extends Entity {
    /**
     * Gets the value of the given attribute on this entity, or null if has not been set.
     * 
     * Attributes can be things like workrate and status information, as well as 
     * configuration (e.g. url/jmxHost/jmxPort), etc.
     */
    <T> T getAttribute(AttributeSensor<T> sensor);

    /**
     * Update the {@link Sensor} data for the given attribute with a new value.
     * 
     * This can be used to "enrich" the entity, such as adding aggregated information, 
     * rolling averages, etc.
     * 
     * @return the old value for the attribute
     */
    <T> T updateAttribute(AttributeSensor<T> sensor, T val);
    
    //FIXME ENGR-1458  change arg #2 to be the SensorEvent?
    //generating new BasicSensorEvent() is _wrong_ for some sensor types (e.g. attribute sensor)
    
    //JAVADOC - remove word 'Generates and' as per above
    // ??? = policy which detects a group is too hot and want the entity to fire a TOO_HOT event
    
    //not convinced by word 'raise' ... but 'fire' and 'emit' aren't significantly better;
    //personally, i actually prefer just the word "update" for this _and_ for updateAttribute above;
    //and change getAttribute(AttS) to get(AttS).  and same for config, e.g.  get(ConfigKey).
    
    //thoughts?
    
    //get slightly concerned that folks could still generate the wrong event type;
    //is it worth using more generics?  e.g. defining Sensor<EventValueType,EventType extends SensorEvent<EventValueType>>
    //then e.g. <T,C> raiseEvent(Sensor<T,C> s, C e) 
    
    //also/instead, we could by convention put newEvent on the concrete Sensor implementations,
    //e.g. BasicSensor<T>.newEvent(T val) 
    //     LogSensor.newEvent(LogLevel level, String topic, String message)
    //then it is 
    /**
     * Generates and emits an event (as though produced by this entity).
     * 
     * For example, it could be used by a <em>???</em>...
     */
    <T> void raiseEvent(Sensor<T> sensor, T val);
}
