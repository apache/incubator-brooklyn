package org.overpaas.event;

import org.overpaas.types.SensorEvent;

/**
 * A listener for {@link SensorEvent}s on an {@link Entity}.
 */
public interface EventListener<T> {
    /**
     * Event handler.
     */
    void onEvent(SensorEvent<T> event);
}
