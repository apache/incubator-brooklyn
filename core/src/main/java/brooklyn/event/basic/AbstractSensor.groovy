package brooklyn.event.basic;

import groovy.transform.InheritConstructors;
import brooklyn.event.Sensor;

/**
 * Abstract parent class for all {@link Sensor} types.
 */
public abstract class AbstractSensor<T> implements Sensor<T> {
    private static final long serialVersionUID = -3762018534086101323L;

    private String description;
    private String name;
    private String type;

    public AbstractSensor() {
        /* for gson */
    }

    public AbstractSensor(String name, Class<T> type) {
        this(name, name, type);
    }

    public AbstractSensor(String description, String name, Class<T> type) {
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
    public Iterable<String> getNameParts() {
        return Splitter.on('.').split(name);
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
