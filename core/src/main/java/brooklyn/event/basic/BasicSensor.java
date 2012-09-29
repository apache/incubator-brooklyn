package brooklyn.event.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Parent for all {@link Sensor}s.
 */
public class BasicSensor<T> implements Sensor<T> {
    private static final long serialVersionUID = -3762018534086101323L;
    private static final Logger LOG = LoggerFactory.getLogger(BasicSensor.class);
    
    private static final Splitter dots = Splitter.on('.');

    private transient Class<T> type;
    private String typeName;
    private String name;
    private String description;
    private List<String> nameParts;
    
    // FIXME In groovy, fields were `public final` with a default constructor; do we need the gson?
    public BasicSensor() { /* for gson */ }

    /** name is typically a dot-separated identifier; description is optional */
    public BasicSensor(Class<T> type, String name) {
        this(type, name, name);
    }
    
    public BasicSensor(Class<T> type, String name, String description) {
        this.type = checkNotNull(type, "type");
        this.typeName = type.getName();
        this.name = checkNotNull(name, "name");
        this.description = description;
        this.nameParts = ImmutableList.copyOf(dots.split(name));
    }

    /** @see Sensor#getType() */
    public Class<T> getType() { return type; }
 
    /** @see Sensor#getTypeName() */
    public String getTypeName() { return typeName; }
 
    /** @see Sensor#getName() */
    public String getName() { return name; }
 
    /** @see Sensor#getNameParts() */
    public List<String> getNameParts() { return nameParts; }
 
    /** @see Sensor#getDescription() */
    public String getDescription() { return description; }
    
    /** @see Sensor#newEvent(Entity, Object) */
    public SensorEvent<T> newEvent(Entity producer, T value) {
        return new BasicSensorEvent<T>(this, producer, value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(typeName, name, description);
    }
 
    @Override
    public boolean equals(Object other) {
        if (this==other) return true;
        if (!(other instanceof BasicSensor)) return false;
        BasicSensor<?> o = (BasicSensor) other;
        
        return Objects.equal(typeName, o.typeName) && Objects.equal(name, o.name) && Objects.equal(description, o.description);
    }
    
    @Override
    public String toString() {
        return String.format("Sensor: %s (%s)", name, typeName);
    }
}
