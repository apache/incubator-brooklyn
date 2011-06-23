package brooklyn.event;

/**
 * A listener for {@link Event}s on an {@link Entity}.
 */
public interface EventListener<T> {
    /**
     * Event handler.
     */
    void onEvent(Event<T> event);
}
