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
    
    /**
     * Generates and emits an event (as though produced by this entity).
     * 
     * For example, it could be used by a <em>???</em>...
     */
    <T> void raiseEvent(Sensor<T> sensor, T val);
}
