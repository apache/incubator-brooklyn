package brooklyn.event.adapter;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;

//FIXME ENGR-1458  javadoc wrong (it's the other way round, no?)  
//eg: Retrieves values for given sensors (when instantiated with a particular entity)
//(nice abstraction and implementation however!!!)
/**
 * Adapter to convert data from a sensor to events on an entity.
 * 
 * Implementations should provide a constructor that takes an {@link Entity} and any appropriate configuration, and should then convert
 * changes to subscribed {@link Sensor}s into {@link Entity#raiseEvent(Sensor, Object)} calls.
 */
public interface SensorAdapter {
	//FIXME ENGR-1458  remove string support from interface?
    /** @see #subscribe(Sensor) */
    public void subscribe(String sensorName);
 
	//FIXME ENGR-1458  setActive(Sensor, boolean active)  ?
    //(that's what we are doing, because someone is subscribed; from the adapter's point of view we aren't subscribing,
    //plus we want a way to unsubscribe-i-mean-turn-off, no?)
    
    //with note that API may change in future to allow setting more sophisticated items, e.g. poll period etc
    
    //JAVADOC to say:  causes the entity to publish events on a given sensor
    
    /**
     * 
     */
    public <T> void subscribe(final Sensor<T> sensor);

	//FIXME ENGR-1458  remove string support from interface?
    /** @see #poll(Sensor) */
    public <T> T poll(String sensorName);
 
    /**
     * 
     */
    public <T> T poll(Sensor<T> sensor);
}
