package brooklyn.event;

import java.io.Serializable;
import java.util.List;

 
/**
 * The interface implemented by concrete sensors.
 * 
 * A sensor is a container for a piece of data of a particular type, and exists in a hierarchical namespace.
 * The name of the sensor is described as a set of tokens separated by dots.
 * 
 * @see Event
 */
public interface Sensor<T> extends Serializable {
    /**
     * Returns the description of the sensor, for display.
     */
    public String getDescription();
 
    /**
     * Returns the name of the sensor, in a dot-separated namespace.
     */
    public String getName();
 
    /**
     * Returns the constitient parts of the sensor name as a {@link Collection}.
     */
    public List<String> getNameParts();
 
    /**
     * Returns the type of the sensor data, as a {@link String} representation of the class name.
     */
    public String getType();
 
    /**
     * Returns the Java {@link Class} for the sensor data.
     */
    public Class<T> getSensorClass();
}
