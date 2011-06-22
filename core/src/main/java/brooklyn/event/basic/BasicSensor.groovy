package brooklyn.event.basic;

import groovy.transform.InheritConstructors

import java.util.List

import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor

import com.google.common.base.Splitter
import com.google.common.base.Throwables
import com.google.common.collect.Lists

/**
 * Parent for all {@link Sensor}s.
 */
public class BasicSensor<T> implements Sensor<T> {
    private static final long serialVersionUID = -3762018534086101323L;
    
    private static final Splitter dots = Splitter.on('.');

    public transient final Class<T> type;
    public final String typeName;
    public final String name;
    public final String description;

    public BasicSensor() { /* for gson */ }

	/** name is typically a dot-separated identifier; description is optional */
	public BasicSensor(Class<T> type, String name, String description=name) {
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
@InheritConstructors
public class BasicAttributeSensor<T> extends BasicSensor<T> implements AttributeSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;
}

/**
 * A {@link Sensor} describing a log message or exceptional condition.
 */
@InheritConstructors
public class LogSensor<T> extends BasicSensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;
}

/**
 * A {@link Sensor} describing a property retrieved via JMX. 
 */
@InheritConstructors
public class JmxAttributeSensor<T> extends BasicAttributeSensor<T> {
    private static final long serialVersionUID = -1;
    
    public final String objectName
    public final String attribute

    public JmxAttributeSensor(Class<T> type, String name, String description=name, String objectName, String attribute) {
        super(type, name, description);
        
        this.objectName = objectName
        this.attribute = attribute
    }
}
