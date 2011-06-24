package brooklyn.event;

import brooklyn.entity.Entity;

/**
 * A tuple representing a piece of data from a {@link Sensor} on an {@link Entity}.
 */
public interface Event<T> {
    /**
     * The {@link Entity} where the data originated.
     */
    public Entity getSource();
 
    /**
     * The {@link Sensor} describing the data.
     */
    public Sensor<T> getSensor();
 
    /**
     * The value for the {@link Sensor} data.
     */
    public T getValue();
}