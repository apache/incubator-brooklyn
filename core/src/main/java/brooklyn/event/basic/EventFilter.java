package brooklyn.event.basic;

import com.google.common.base.Predicate;

public interface EventFilter<T> extends Predicate<SensorEvent<T>> {
}
