package brooklyn.event.adapter;

import groovy.transform.InheritConstructors
import brooklyn.event.basic.ConfiguredAttributeSensor


/** simple config adapter which, on registration, sets all config-attributes from config values */ 
@InheritConstructors
public class ConfigSensorAdapter extends AbstractSensorAdapter {

	void register(SensorRegistry registry) {
		super.register(registry)
		addActivationLifecycleListeners({ apply() }, {})
	}
	
	public void apply() {
		entity.sensors.values().each { 
			if (it in ConfiguredAttributeSensor) entity.setAttribute(it) }
	}
	
}
