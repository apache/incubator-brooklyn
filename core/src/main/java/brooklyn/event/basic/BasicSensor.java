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
import com.google.common.reflect.TypeToken;

/**
 * Parent for all {@link Sensor}s.
 */
public class BasicSensor<T> implements Sensor<T> {
    private static final long serialVersionUID = -3762018534086101323L;
    private static final Logger LOG = LoggerFactory.getLogger(BasicSensor.class);
    
    private static final Splitter dots = Splitter.on('.');

    private TypeToken<T> typeToken;
    private Class<? super T> type;
    private String name;
    private String description;
    private transient List<String> nameParts;
    
    // FIXME In groovy, fields were `public final` with a default constructor; do we need the gson?
    public BasicSensor() { /* for gson */ }

    /** name is typically a dot-separated identifier; description is optional */
    public BasicSensor(Class<T> type, String name) {
        this(type, name, name);
    }
    
    public BasicSensor(Class<T> type, String name, String description) {
        this(TypeToken.of(type), name, description);
    }
    
    public BasicSensor(TypeToken<T> typeToken, String name, String description) {
        this.typeToken = checkNotNull(typeToken, "typeToken");
        this.type = typeToken.getRawType();
        this.name = checkNotNull(name, "name");
        this.description = description;
    }

    /** @see Sensor#getTypeToken() */
    public TypeToken<T> getTypeToken() { return typeToken; }
    
    /** @see Sensor#getType() */
    public Class<? super T> getType() { return type; }
 
    /** @see Sensor#getTypeName() */
    public String getTypeName() { 
        return type.getName();
    }
 
    /** @see Sensor#getName() */
    public String getName() { return name; }
 
    /** @see Sensor#getNameParts() */
    public synchronized List<String> getNameParts() {
        if (nameParts==null) nameParts = ImmutableList.copyOf(dots.split(name));
        return nameParts; 
    }
 
    /** @see Sensor#getDescription() */
    public String getDescription() { return description; }
    
    /** @see Sensor#newEvent(Entity, Object) */
    public SensorEvent<T> newEvent(Entity producer, T value) {
        return new BasicSensorEvent<T>(this, producer, value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(getTypeName(), name, description);
    }
 
    @Override
    public boolean equals(Object other) {
        if (this==other) return true;
        if (!(other instanceof BasicSensor)) return false;
        BasicSensor<?> o = (BasicSensor) other;
        
        return Objects.equal(getTypeName(), o.getTypeName()) && Objects.equal(name, o.name) && Objects.equal(description, o.description);
    }
    
    @Override
    public String toString() {
        return String.format("Sensor: %s (%s)", name, getTypeName());
    }
}
