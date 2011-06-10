package org.overpaas.core.types

import org.overpaas.core.decorators.OverpaasEntity;

import groovy.transform.InheritConstructors;

public abstract class Sensor<T> {
	public final String name;
	public final Class<T> type;
	
	public Sensor(Map m) { super(m) }
	
	protected Sensor(String name, Class<T> type) {
		this.name = name;
		this.type = type;
	}
}

public class ActivitySensor<T> extends Sensor<T> {
	public final String field;
	public ActivitySensor(Map m) {
		super(m);
		if (!field) field=name
	}
	public ActivitySensor(String name, String field=name, Class<T> type) {
		super(name, type);
		this.field = field;
	}
}

@InheritConstructors
public class ExceptionSensor<T> extends Sensor<T> {
}

@InheritConstructors
public class LogSensor<T> extends Sensor<T> {
}


public class SensorEvent<T> {
	Sensor<T> sensor
	OverpaasEntity entity
	T value
	public T get() { value }
}
