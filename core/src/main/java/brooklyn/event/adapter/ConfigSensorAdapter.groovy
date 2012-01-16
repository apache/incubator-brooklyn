package brooklyn.event.adapter;

import groovy.transform.InheritConstructors
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey


/** simple config adapter which, on registration, sets all config-attributes from config values */ 
@InheritConstructors
public class ConfigSensorAdapter extends AbstractSensorAdapter {

	void register(SensorRegistry registry) {
		super.register(registry)
		addActivationLifecycleListeners({ apply() }, {})
	}
	
	public void apply() {
		apply(entity)
	}
	
	//normally just applied once, statically, not registered...
	public static void apply(EntityLocal entity) {
		entity.sensors.values().each { 
			if (it in AttributeSensorAndConfigKey && entity.getAttribute(it)==null) entity.setAttribute(it) }
	}
	
}
