package brooklyn.event.basic;

import brooklyn.event.SensorEvent;

/**
 * A listener for {@link SensorEvent}s on an {@link Entity}.
 */
public interface EventListener<T> {
    /**
     * Event handler.
     */
    void onEvent(SensorEvent<T> event);
}
