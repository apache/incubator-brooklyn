package brooklyn.event

import groovy.transform.InheritConstructors

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity

public abstract class Sensor<T> {
    // TODO Sensor modeled on concepts in MBeanNotificationInfo
    // String getName; String getType; String getDescription
    
    static final Logger log = LoggerFactory.getLogger(Activity.class)
 
	public final String name;
	public final Class<T> type;
    
    public Sensor() { this([:]) }
	 
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
	Entity entity
	T value
    
    public SensorEvent() { /* Meh */ }
    
    public SensorEvent(Sensor<T> sensor, Entity entity, T value) {
        this.sensor = sensor
        this.entity = entity
        this.value = value
    }
}
