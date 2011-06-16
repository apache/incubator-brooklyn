package brooklyn.event;

import brooklyn.event.Event;

/**
 * A listener for {@link Event}s on an {@link Entity}.
 */
public interface EventListener<T> {
    /**
     * Event handler.
     */
    void onEvent(Event<T> event);
}
