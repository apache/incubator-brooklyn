package org.overpaas.types;

import groovy.util.logging.Slf4j;

import java.util.Collection;
import java.util.Map;

import org.overpaas.entities.Entity
import org.overpaas.web.tomcat.TomcatNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Slf4j
public class Activity {
	Entity entity;
	Map values = [:];
	
	public Activity(Entity entity) {
		this.entity = entity;
	}
	
	public Object update(Collection<String> path, Object newValue) {
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
		// assert val == oldValue
	}
	
	public <T> Object update(ActivitySensor<T> sensor, T newValue) {
		log.debug "sensor $sensor field {} set to {}", sensor.field, newValue
		update( sensor.field.split("\\.") as List, newValue )

		//TODO notify subscribers!
				
        SensorEvent<T> event = new SensorEvent<T>();
        event.sensor = sensor;
        event.entity = entity;
        event.value = newValue;
        entity.raiseEvent event
        
//		entity.getApplication().getSubscriptionManager().fire(entity, sensor, newValue)
		// so that a policy could say e.g.
		//application.subscribe(entity, sensor)
		//or
		//application.subscribeToChildren(entity, sensor)
		//AND group defines a NotificationSensor around its children
		//(so anyone who subscribesToChildren will implicitly also subscribe to CHILDREN sensor on the entity) 
	}
	
	public Object getValue(Collection<String> path) {
		return getValueRecurse( values, path )
	}
	
	public <T> T getValue(ActivitySensor<T> sensor) {
		return getValueRecurse( values, sensor.field.split("\\.") as List )
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
