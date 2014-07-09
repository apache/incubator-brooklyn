package brooklyn.event;

import brooklyn.entity.Entity;

/**
 * A listener for {@link SensorEvent}s on an {@link Entity}.
 */
public interface SensorEventListener<T> {
    
    public static final SensorEventListener<Object> NOOP = new SensorEventListener<Object>() {
        @Override public void onEvent(SensorEvent<Object> event) {
        }
    };
    
    /**
     * The {@link SensorEvent} handler method.
     */
    void onEvent(SensorEvent<T> event);
}
