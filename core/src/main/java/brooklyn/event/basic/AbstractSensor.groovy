package brooklyn.event.basic;

import groovy.transform.InheritConstructors

import com.google.common.base.Splitter
import com.google.common.base.Throwables
import com.google.common.collect.Lists;

import brooklyn.event.Sensor

/**
 * Abstract parent class for all {@link Sensor} types.
 */
public abstract class AbstractSensor<T> implements Sensor<T> {
    private static final long serialVersionUID = -3762018534086101323L;
    
    private static final Splitter dots = Splitter.on('.');

    private String description;
    private String name;
    private String type;

    public AbstractSensor() { /* for gson */ }

    public AbstractSensor(String description=name, String name, Class<T> type) {
        this.description = description;
        this.name = name;
        this.type = type.getName();
    }

    /** @see Sensor#getDescription() */
    public String getDescription() {
        return description;
    }

    /** @see Sensor#getName() */
    public String getName() {
        return name;
    }

    /** @see Sensor#getNameParts() */
    public Collection<String> getNameParts() {
        return Lists.newArrayList(dots.split(name));
    }

    /** @see Sensor#getType() */
    public String getType() {
        return type;
    }

    /** @see Sensor#getSensorClass() */
    public Class<T> getSensorClass() {
        try {
            return (Class<T>) Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
}

@InheritConstructors
public class AttributeSensor<T> extends AbstractSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;
}

@InheritConstructors
public class LogSensor<T> extends AbstractSensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;
}
