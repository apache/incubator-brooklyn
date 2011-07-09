package brooklyn.event;

import brooklyn.entity.Entity;

/**
 * A tuple representing a piece of data from a {@link Sensor} on an {@link Entity}.
 */
public interface SensorEvent<T> {
    /**
     * The {@link Entity} where the data originated.
     */
    Entity getSource();
 
    /**
     * The {@link Sensor} describing the data.
     */
    Sensor<T> getSensor();
 
    /**
     * The value for the {@link Sensor} data.
     */
    T getValue();

    /**
     * The time this data was published.
     */
    long getTimestamp();
}