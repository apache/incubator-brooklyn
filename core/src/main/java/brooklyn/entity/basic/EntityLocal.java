package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.basic.AttributeSensor;

public interface EntityLocal extends Entity {

    /**
     * Gets the value of the given attribute on this entity, or null if has not been set.
     * 
     * Attributes can be things like workrate and status information, as well as 
     * configuration (e.g. url/jmxHost/jmxPort), etc.
     */
    <T> T getAttribute(AttributeSensor<T> sensor);

    /**
     * Sets the value for the given sensor on this entity, 
     * 
     * This can be used to "enrich" the entity, such as adding aggregated information, 
     * rolling averages, etc.
     */
    <T> void updateAttribute(AttributeSensor<T> sensor, T val);

    /**
     * Generates and emits an event (as though produced by this entity).
     * 
     * For example, it could be used by a 
     * @param <T>
     * @param event
     */
    <T> void raiseEvent(Sensor<T> sensor, T val);
}
