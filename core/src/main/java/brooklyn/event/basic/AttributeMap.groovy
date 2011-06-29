package brooklyn.event.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor

import com.google.common.base.Preconditions

/**
 * A {@link Map} of {@link Entity} attribute values.
 */
public class AttributeMap {
    static final Logger log = LoggerFactory.getLogger(AttributeMap.class)
 
    EntityLocal entity;
    Map values = [:];
    
    public AttributeMap(EntityLocal entity) {
        this.entity = entity;
    }
    
    public Map asMap() { values }
        
    public <T> T update(Collection<String> path, T newValue) {
        Map map
        String key
        Object val = values
        path.each { 
            if (!(val in Map)) {
                if (val!=null)
                    log.debug "wrong type of value encountered when setting $path; expected map at $key but found $val; overwriting"
                val = [:]
                map.put(key, val)
            }
            map = val
            key = it
            val = map.get(it)
        }
        log.debug "putting at $path, $key under $map"
        def oldValue = map.put(key, newValue)
        oldValue
    }
    
    public <T> void update(Sensor<T> sensor, T newValue) {
        Preconditions.checkArgument(sensor in AttributeSensor, "AttributeMap can only update an attribute sensor's value, not %s", sensor)
        def oldValue = update(sensor.getNameParts(), newValue)
        log.debug "sensor {} set to {}", sensor.name, newValue
        entity.emit sensor, newValue
        oldValue
    }
    
    public Object getValue(Collection<String> path) {
        return getValueRecurse(values, path)
    }
    
    public <T> T getValue(Sensor<T> sensor) {
        return getValueRecurse(values, sensor.getNameParts())
    }

    private static Object getValueRecurse(Map node, Collection<String> path) {
        if (node==null) return null
        Preconditions.checkArgument(!path.isEmpty(), "field name is empty")
        
        def key = path[0]
        Preconditions.checkState(key != null, "head of path is null")
        Preconditions.checkState(key.length() > 0, "head of path is empty")
        
        def child = node.get(key)
        Preconditions.checkState(child != null || path.size() > 0, "node $key is not present")
        
        if (path.size() > 1)
            return getValueRecurse(child, path[1..-1])
        else
            return child
    }
}
