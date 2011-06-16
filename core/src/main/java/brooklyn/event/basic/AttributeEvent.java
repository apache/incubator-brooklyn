package brooklyn.event.basic;

import brooklyn.event.Event;

public interface AttributeEvent<T> extends Event<T> {
    /** @see Event#getSensor() */
    public AttributeSensor<T> getSensor();
}