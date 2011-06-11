package org.overpaas.types;

import java.util.Collection;
import java.util.Map;

import org.overpaas.entities.Entity;

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
					println "WARNING: wrong type of value encountered when setting $path; expected map at $key but found $val; overwriting"
				val = [:]
				map.put(key, val)
			}
			map = val
			key = it
			val = map.get(it)
		}
//		println "putting at $path, $key under $map"
		def oldValue = map.put(key, newValue)
		// assert val == oldValue
	}
	
	public <T> Object update(ActivitySensor<T> sensor, T newValue) {
//		println "sensor $sensor field is "+sensor.field
//		println "  split is "+sensor.field.split(".")
//		println "  split regex is "+sensor.field.split("\\.")
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