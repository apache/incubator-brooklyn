package brooklyn.event.basic;

import brooklyn.event.Sensor

import com.google.common.base.Splitter
import com.google.common.base.Throwables
import com.google.common.collect.Lists

/**
 * Abstract super class for all {@link Sensor} types.
 */
public abstract class AbstractSensor<T> implements Sensor<T> {
    private static final long serialVersionUID = -3762018534086101323L;
    
    private static final Splitter dots = Splitter.on('.');

	//TODO Alex suggests:  make these public final (groovy automatically invokes getter if there is one for a field anyway, unless you use @ operator, e.g. otherObj.@field) 
    private String description;
    private String name;
	//TODO Alex strongly suggests: make it a Class. java API nicer that way. use custom serialisers rather than corrupt the internal type
	//or if you must, make it transient, and have non-transient String typeName, and getType will check for type being null...
    private String type;

    public AbstractSensor() { /* for gson */ }

    public AbstractSensor(String description=name, String name, Class<T> type) {
        this.description = description;
        this.name = name;
        this.type = type.getName();
    }

	//TODO remove getters which groovy gives for free?
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

	//TODO Alex suggests: remove this, it is confusing in light of 'type' (and EntityClass)
	//if/when we need it we'll consider (with a better understanding) whether this internal class hierarchy 
	//is really the best way to expose, or whether we want an enum, explicit SensorType class with static values, etc...
    /** @see Sensor#getSensorClass() */
    public Class<T> getSensorClass() {
        try {
            return (Class<T>) Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
}

//@InheritConstructors
public class AttributeSensor<T> extends AbstractSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;
	public AttributeSensor(String description=name, String name, Class<T> type) { super(description, name, type) }
}

//@InheritConstructors
public class LogSensor<T> extends AbstractSensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;
	public LogSensor(String description=name, String name, Class<T> type) { super(description, name, type) }
}
