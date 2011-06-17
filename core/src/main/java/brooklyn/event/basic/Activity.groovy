package brooklyn.event.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.event.Sensor

public class Activity {
    static final Logger log = LoggerFactory.getLogger(Activity.class)
 
	Entity entity;
	Map values = [:];
	
	public Activity(Entity entity) {
		this.entity = entity;
	}
	
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
	
	public <T> Object update(Sensor<T> sensor, T newValue) {
		log.debug "sensor $sensor field {} set to {}", sensor.name, newValue
		update(sensor.getNameParts(), newValue)
        SensorEvent event = new SensorEvent<T>(sensor, entity, newValue)
        entity.raiseEvent event
	}
	
	public Object getValue(Collection<String> path) {
		return getValueRecurse(values, path)
	}
	
	public <T> T getValue(Sensor<T> sensor) {
		return getValueRecurse(values, sensor.getNameParts())
	}

	private static Object getValueRecurse(Map node, Collection<String> path) {
		if (node == null) throw new IllegalArgumentException("node is null")
		if (path.size() == 0) throw new IllegalArgumentException("field name is empty")
		
		def key = path[0]
		if (key == null) throw new IllegalArgumentException("head of path is null")
		if (key.length() == 0) throw new IllegalArgumentException("head of path is empty")
		
		def child = node.get(key)
		if (child == null) throw new IllegalArgumentException("node $key is not present")
		
		if (path.size() > 1)
			return getValueRecurse(child, path[1..-1])
		else
			return child
	}

}
