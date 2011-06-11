package org.overpaas.event;

import org.overpaas.types.SensorEvent;

public interface EventListener<T> {

    void onEvent(SensorEvent<T> event);
}
