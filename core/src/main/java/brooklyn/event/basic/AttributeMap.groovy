package brooklyn.event.basic

import java.util.concurrent.ConcurrentHashMap

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor

import com.google.common.base.Preconditions

/**
 * A {@link Map} of {@link Entity} attribute values.
 */
public class AttributeMap implements Serializable {
    static final Logger log = LoggerFactory.getLogger(AttributeMap.class)
 
    EntityLocal entity;
    
    /**
     * The values are stored as nested maps, with the key being the constituent parts of the sensor.
     */
    // Note that we synchronize on the top-level map, to handle concurrent updates and and gets (ENGR-2111)
    Map<Collection<String>, Object> values = [:];
    
    public AttributeMap(EntityLocal entity) {
        this.entity = entity;
    }
    
    // TODO semantics of asMap have changed; previously returned a map-of-map-of-map etc for the values, arranged by the sensors' name parts.
    // Now keys are Collection<String> for the name-parts of the sensor
    public Map asMap() { values }
    
    /** returns oldValue */
    public <T> T update(Collection<String> path, T newValue) {
        // TODO path must be ordered(and legal to contain duplicates like "a.b.a"; list would be better
        synchronized (values) {
            if (log.isTraceEnabled()) log.trace "setting sensor $path=$newValue for $entity"
            return values.put(path, newValue)
        }
    }
    
    public <T> void update(Sensor<T> sensor, T newValue) {
        Preconditions.checkArgument(sensor in AttributeSensor, "AttributeMap can only update an attribute sensor's value, not %s", sensor)
        def oldValue = (T) update(sensor.getNameParts(), newValue)
        entity.emitInternal sensor, newValue
        oldValue
    }
    
    public Object getValue(Collection<String> path) {
        // TODO previously this would return a map of the sub-tree if the path matched a prefix of a group of sensors, 
        // or the leaf value if only one value. Arguably that is not required - what is/was the use-case?
        // 
        Preconditions.checkArgument(!path.isEmpty(), "path is empty")
        synchronized (values) {
            return values.get(path);
        }
    }
    
    public <T> T getValue(Sensor<T> sensor) {
        synchronized (values) {
            return values.get(sensor.getNameParts());
        }
    }

    //ENGR-1458  interesting to use property change. if it works great.
    //if there are any issues with it consider instead just making attributesInternal private,
    //and forcing all changes to attributesInternal to go through update(AttributeSensor,...)
    //and do the publishing there...  (please leave this comment here for several months until we know... it's Jun 2011 right now)
//    protected final PropertiesSensorAdapter propertiesAdapter = new PropertiesSensorAdapter(this, attributes)
    //if wee need this, fold the capabilities into this class.
}
