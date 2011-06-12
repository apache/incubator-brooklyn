package org.overpaas.types;

import groovy.util.logging.Slf4j;

import java.util.Collection;
import java.util.Map;

import org.overpaas.entities.Entity;

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
	
}