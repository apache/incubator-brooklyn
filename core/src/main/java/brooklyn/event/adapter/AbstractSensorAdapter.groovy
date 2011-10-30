package brooklyn.event.adapter

import java.util.Map;

import groovy.lang.Closure

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor
import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag

public abstract class AbstractSensorAdapter {

	private static final Logger log = LoggerFactory.getLogger(AbstractSensorAdapter)
	
	@SetFromFlag("period")
	long pollPeriod;
	
	AttributePoller registry;
	EntityLocal entity;

	final Map<AttributeSensor, Closure> polledSensors = [:]
	
	public AbstractSensorAdapter(Map flags) {
		Map unused = FlagUtils.setFieldsFromFlags(flags, this);
		if (unused) log.warn("unknown flags when constructing {}: {}", this, unused)
	}

	void register(AttributePoller registry) {
		if (this.registry==registry) return
		if (this.registry!=null) throw new IllegalStateException("cannot change registry for ${this}: from ${this.registry} to ${registry}")
		if (!registry.adapters.contains(this)) 
			throw new IllegalStateException("${this}.register should only be called by ${registry}");
		this.registry = registry;
		this.entity = registry.entity;
	}
	
	public void poll(Sensor s, Closure c={ content }) {
		Object old = polledSensors.put(s, c)
		if (old!=null) log.warn "change of provider (discouraged) for sensor ${s} on ${entity}", new Throwable("path of discouraged change");
	}
	
	protected abstract void executePoll();
	
	void evaluateSensorsOnPollResponse(SensorEvaluationContext response, String optionalContextForErrors=null) {
		polledSensors.each { s, c -> evaluateSensorOnResponse(s, c, response, optionalContextForErrors) }
	}
	Object evaluateSensorOnResponse(Sensor<?> s, Closure c, SensorEvaluationContext response, String optionalContextForErrors=null) {
		try {
			Object v = response.evaluate(entity, s, c)
			if (v!=SensorEvaluationContext.UNSET) {
				v = v?.asType(s.getType());
				entity.setAttribute(s, v);
				return v
			}
		} catch (Exception e) {
			log.warn "unable to compute ${s} for ${entity}: ${e.getMessage()}"+
					(optionalContextForErrors?"\n"+optionalContextForErrors:""), e
		}
		return null
	}


	//TODO event listeners...
	//	public void onEvent(Sensor s, Event, Closure c={ content }) {
	//		Object old = polledSensors.put(s, c)
	//		if (old!=null) log.warn "discouraged change of provider for sensor ${s} on ${entity}", new Throwable("path of discouraged change");
	//	}
	//
	//	public void onStop(Sensor s, Closure c={ content }) {
	//		Object old = polledSensors.put(s, c)
	//		if (old!=null) log.warn "discouraged change of provider for sensor ${s} on ${entity}", new Throwable("path of discouraged change");
	//	}

	interface SensorEvaluationContext {
		/** may throw exception, or return UNSET to not change */
		public static final Object UNSET = []
		/** evaluate the closure in the context of the implementing class (e.g. a specific HTTP response),
		 * so that methods and variables in the class are available to the closure;
		 * entity and sensor available for inspection but can be null during testing;
		 * return value is normally adapted by the caller (e.g. the sensor adapter) to suit the sensor;
		 * this method (and thus the closure) may return UNSET to indicate the adapter should not update the sensor */
		public Object evaluate(Entity entity, Sensor<?> sensor, Closure c);
	}
			
}
