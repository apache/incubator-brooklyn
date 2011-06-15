package brooklyn.event.basic;


import brooklyn.event.SensorEvent;

import com.google.common.base.Predicate;

public interface EventFilter<T> extends Predicate<SensorEvent<T>> {
}
