package brooklyn.event;

import java.io.Serializable;
import java.util.List;

import brooklyn.entity.Entity;

/**
 * The interface implemented by concrete sensors.
 * 
 * A sensor is a container for a piece of data of a particular type, and exists in a hierarchical namespace.
 * The name of the sensor is described as a set of tokens separated by dots.
 * 
 * @see SensorEvent
 */
public interface Sensor<T> extends Serializable {
    /**
     * Returns the Java {@link Class} for the sensor data.
     */
    Class<T> getType();
 
    /**
     * Returns the type of the sensor data, as a {@link String} representation of the class name.
     */
    String getTypeName();
 
    /**
     * Returns the name of the sensor, in a dot-separated namespace.
     */
    String getName();
 
    /**
     * Returns the constitient parts of the sensor name as a {@link List}.
     */
    List<String> getNameParts();
 
    /**
     * Returns the description of the sensor, for display.
     */
    String getDescription();
 
    /**
     * Create a new {@link SensorEvent} object for a specific {@link Entity} and data point.
     */
    SensorEvent<T> newEvent(Entity entity, T value);
}
