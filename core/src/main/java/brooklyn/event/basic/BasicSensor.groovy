package brooklyn.event.basic;

import groovy.transform.InheritConstructors

import java.util.List

import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.Objects
import com.google.common.base.Splitter
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
    public Class<T> getType() { type }
 
    /** @see Sensor#getTypeName() */
    public String getTypeName() { typeName }
 
    /** @see Sensor#getName() */
    public String getName() { name }
 
    /** @see Sensor#getNameParts() */
    public List<String> getNameParts() {
        return Lists.newArrayList(dots.split(name));
    }
 
    /** @see Sensor#getDescription() */
    public String getDescription() { description }
    
    /** @see Sensor#newEvent(Entity, Object) */
    public SensorEvent<T> newEvent(Entity producer, T value) {
        return new BasicSensorEvent<T>(this, producer, value)
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(typeName, name, description)
    }
 
    @Override
    public boolean equals(Object other) {
        LanguageUtils.equals(this, other, ["type", "name", "description"]);
    }
    
    @Override
    public String toString() {
        return String.format("Sensor: %s (%s)", name, typeName)
    }
}

/**
 * A {@link Sensor} describing an attribute change.
 */
@InheritConstructors
public class BasicAttributeSensor<T> extends BasicSensor<T> implements AttributeSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;

    public BasicAttributeSensor(Class<T> type, String name, String description=name) {
        super(type, name, description)
    }
}

/**
 * A {@link Sensor} used to notify subscribers about events.
 */
@InheritConstructors
public class BasicNotificationSensor<T> extends BasicSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;

    public BasicNotificationSensor(Class<T> type, String name, String description=name) {
        super(type, name, description)
    }
}

/**
 * A {@link Sensor} describing a log message or exceptional condition.
 */
@InheritConstructors
public class LogSensor<T> extends BasicSensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;

    public LogSensor(Class<T> type, String name, String description=name) {
        super(type, name, description)
    }
}
