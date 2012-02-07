package brooklyn.event.basic

import java.util.List

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.ConfigKey.HasConfigKey
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.location.PortRange
import brooklyn.location.PortSupplier
import brooklyn.util.flags.TypeCoercions
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.Objects
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists

/**
 * Parent for all {@link Sensor}s.
 */
public class BasicSensor<T> implements Sensor<T> {
    private static final long serialVersionUID = -3762018534086101323L;
    private static final Logger LOG = LoggerFactory.getLogger(BasicSensor.class)
    
    private static final Splitter dots = Splitter.on('.');

    public transient final Class<T> type;
    public final String typeName;
    public final String name;
    public final String description;
    private final List<String> _nameParts;
    
    public BasicSensor() { /* for gson */ }

    /** name is typically a dot-separated identifier; description is optional */
    public BasicSensor(Class<T> type, String name, String description=name) {
        this.type = type;
        this.typeName = type.getName();
        this.name = name;
        this.description = description;
        this._nameParts = ImmutableList.copyOf(dots.split(name))
    }

    /** @see Sensor#getType() */
    public Class<T> getType() { type }
 
    /** @see Sensor#getTypeName() */
    public String getTypeName() { typeName }
 
    /** @see Sensor#getName() */
    public String getName() { name }
 
    /** @see Sensor#getNameParts() */
    public List<String> getNameParts() {
        return _nameParts;
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
public class BasicAttributeSensor<T> extends BasicSensor<T> implements AttributeSensor<T> {
    private static final long serialVersionUID = -2493209215974820300L;

    public BasicAttributeSensor(Class<T> type, String name, String description=name) {
        super(type, name, description)
    }
}

/**
* A {@link Sensor} describing an attribute that can be configured with inputs that are used to derive the final value.
*
* The {@link ConfigKey} will have the same name and description as the sensor but not necessarily the same type.
* Conversion to set the sensor value from the config key must be supplied in a subclass.
*/
public abstract class AttributeSensorAndConfigKey<ConfigType,SensorType> extends BasicAttributeSensor<SensorType> 
        implements HasConfigKey<ConfigType> {
    private static final long serialVersionUID = -3103809215973264600L;

    private ConfigKey<ConfigType> configKey

    public AttributeSensorAndConfigKey(Class<ConfigType> configType, Class<SensorType> sensorType, String name, String description=name, Object defaultValue=null) {
        super(sensorType, name, description)
        configKey = new BasicConfigKey<ConfigType>(configType, name, description, TypeCoercions.coerce(defaultValue, configType))
    }

    public AttributeSensorAndConfigKey(AttributeSensorAndConfigKey<ConfigType,SensorType> orig, ConfigType defaultValue) {
        super(orig.type, orig.name, orig.description)
        configKey = new BasicConfigKey<ConfigType>(orig.configKey.type, orig.name, orig.description, 
            TypeCoercions.coerce(defaultValue, orig.configKey.type))
    }

    public ConfigKey<ConfigType> getConfigKey() { return configKey }
    
    /** returns the sensor value for this attribute on the given entity, if present,
     * otherwise works out what the sensor value should be based on the config key's value
     * <p>
     * calls to this may allocate resources (e.g. ports) so should be called only once and 
     * then (if non-null) assigned as the sensor's value
     * <p>
     * <b>(for this reason this method should generally not be invoked except in tests (and in AbstractEntity),
     * and similarly should not be overridden; implement convertConfigToSensor instead)</b> 
     */
    public SensorType getAsSensorValue(Entity e) {
        def v = e.getAttribute(this);
        if (v!=null) return v;
        v = e.getConfig(this);
        if (v==null) v = configKey.defaultValue;
        try {
            return convertConfigToSensor(v, e)
        } catch (Throwable t) {
            throw new IllegalArgumentException("Cannot convert config value $v for sensor "+this+": "+t, t);
        }
    }
    
    /** converts the given ConfigType value to the corresponding SensorType value, 
     * with respect to the given entity
     * <p>
     * this is invoked after checks whether the entity already has a value for the sensor,
     * and the entity-specific config value is passed for convenience if set, 
     * otherwise the config key default value is passed for convenience
     * <p>
     * this message should be allowed to return null if the conversion cannot be completed at this time */
    protected abstract SensorType convertConfigToSensor(ConfigType value, Entity entity);
}
  
/**
 * A {@link Sensor} describing an attribute that can be configured with a default value.
 *
 * The {@link ConfigKey} has the same type, name and description as the sensor,
 * and is typically used to populate the sensor's value at runtime.
 */
public class BasicAttributeSensorAndConfigKey<T> extends AttributeSensorAndConfigKey<T,T> {
    public BasicAttributeSensorAndConfigKey(Class<T> type, String name, String description=name, T defaultValue=null) {
        super(type, type, name, description, defaultValue)
    }

    public BasicAttributeSensorAndConfigKey(BasicAttributeSensorAndConfigKey<T> orig, T defaultValue) {
        super(orig, defaultValue)
    }
    protected T convertConfigToSensor(T value, Entity entity) { value }
}
       
/**
 * A {@link Sensor} describing a port on a system,
 * with a {@link ConfigKey} which can be configured with a port range
 * (either a number e.g. 80, or a string e.g. "80" or "8080-8089" or even "80, 8080-8089, 8800+", or a list of these).
 * <p>
 * To convert at runtime a single port is chosen, respecting the entity.
 */
public class PortAttributeSensorAndConfigKey extends AttributeSensorAndConfigKey<PortRange,Integer> {
    public static final Logger LOG = LoggerFactory.getLogger(PortAttributeSensorAndConfigKey.class);
    
    public PortAttributeSensorAndConfigKey(String name, String description=name, Object defaultValue=null) {
        super(PortRange, Integer, name, description, defaultValue)
    }
    public PortAttributeSensorAndConfigKey(PortAttributeSensorAndConfigKey orig, Object defaultValue) {
        super(orig, defaultValue)
    }
    protected Integer convertConfigToSensor(PortRange value, Entity entity) {
        if (value==null) return null;
        if (entity.locations) {
            if (entity.locations.size()==1) {
                def l = entity.locations.iterator().next();
                if (l in PortSupplier) {
                    def p = ((PortSupplier)l).obtainPort(value);
                    if (p!=-1) {
                        LOG.info(""+entity+" choosing port "+p+" for "+getName())
                        return p;
                    }
                    LOG.warn(""+entity+" no port available for "+getName())
                    // definitively, no ports available
                    return null;
                }
                // ports may be available, we just can't tell from the location
                def v = (value.isEmpty() ? null : value.iterator().next())
                LOG.info(""+entity+" choosing port "+v+" (unconfirmed) for "+getName());
                return v;
            }
        }
        LOG.info(""+entity+" ports not applicable to non-server location, ignoring "+getName());
        return null
    }
    
}

/**
 * A {@link Sensor} used to notify subscribers about events.
 */
public class BasicNotificationSensor<T> extends BasicSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;

    public BasicNotificationSensor(Class<T> type, String name, String description=name) {
        super(type, name, description)
    }
}

/**
 * A {@link Sensor} describing a log message or exceptional condition.
 */
public class LogSensor<T> extends BasicSensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;

    public LogSensor(Class<T> type, String name, String description=name) {
        super(type, name, description)
    }
}
