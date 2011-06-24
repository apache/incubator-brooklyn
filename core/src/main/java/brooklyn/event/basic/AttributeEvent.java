package brooklyn.event.basic;

import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;

public interface AttributeEvent<T> extends SensorEvent<T> {
    /** @see SensorEvent#getSensor() */
    public AttributeSensor<T> getSensor();
}