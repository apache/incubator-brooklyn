package brooklyn.event;

import java.io.Serializable;
import java.util.List;

import com.google.common.reflect.TypeToken;

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
     * <p>
     * This returns a "super" of T only in the case where T is generified, 
     * and in such cases it returns the Class instance for the unadorned T ---
     * i.e. for List<String> this returns Class<List> ---
     * this is of course because there is no actual Class<List<String>> instance.
     */
    Class<? super T> getType();
    
    /**
     * Returns the Guava TypeToken (including generics info)
     */
    TypeToken<T> getTypeToken();
    
    /**
     * Returns the type of the sensor data, as a {@link String} representation of the class name.
     * (Useful for contexts where Type is not accessible.)
     */
    String getTypeName();

    /**
     * Returns the name of the sensor, in a dot-separated namespace.
     */
    String getName();
    
    /**
     * Returns the constituent parts of the sensor name as a {@link List}.
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
