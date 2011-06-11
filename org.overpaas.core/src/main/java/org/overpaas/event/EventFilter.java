package org.overpaas.event;

import org.overpaas.types.SensorEvent;

import com.google.common.base.Predicate;

public interface EventFilter<T> extends Predicate<SensorEvent<T>> {
}
