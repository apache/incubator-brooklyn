package brooklyn.event.basic;

import brooklyn.event.Sensor
import java.util.List

import groovy.transform.InheritConstructors

import com.google.common.base.Splitter
import com.google.common.base.Throwables
import com.google.common.collect.Lists

/**
 * Abstract super class for all {@link Sensor} types.
 */
public abstract class AbstractSensor<T> implements Sensor<T> {
    private static final long serialVersionUID = -3762018534086101323L;
    
    private static final Splitter dots = Splitter.on('.');

    public transient final Class<T> type;
    public final String typeName;
    public final String name;
    public final String description;

    public AbstractSensor() { /* for gson */ }

	/** name is typically a dot-separated identifier; description is optional */
	public AbstractSensor(Class<T> type, String name, String description=name) {
		this.type = type;
		this.typeName = type.getName();
		this.name = name;
		this.description = description;
    }

    /** @see Sensor#getType() */
    public Class<T> getType() {
        try {
            return type ?: (Class<T>) Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
 
    /** @see Sensor#getTypeName() */
    public String getTypeName() { return typeName }
 
    /** @see Sensor#getName() */
    public String getName() { return name }
 
    /** @see Sensor#getNameParts() */
    public List<String> getNameParts() {
        return Lists.newArrayList(dots.split(name));
    }
 
    /** @see Sensor#getDescription() */
    public String getDescription() { return description }
}

/**
 * A {@link Sensor} describing an attribute change.
 */
public class AttributeSensor<T> extends AbstractSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;

    public AttributeSensor() { /* for gson */ }

	public AttributeSensor(Class<T> type, String name, String description=name) {
        super(type, name, description);
    }
}

/**
 * A {@link Sensor} describing a log message or exceptional condition.
 */
public class LogSensor<T> extends AbstractSensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;

    public LogSensor() { /* for gson */ }

    public LogSensor(Class<T> type, String name, String description=name) {
        super(type, name, description);
    }
}
