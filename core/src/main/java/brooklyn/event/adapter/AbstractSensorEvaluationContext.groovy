package brooklyn.event.adapter;

import groovy.lang.Closure

import java.util.Map

import javax.annotation.Nullable

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.event.Sensor


/** provides fields and methods that are brought into scope when executing closures submitted
 * by an entity developer for evaluating a sensor; fields can be made explicit, to be shared
 * among multiple sensors; and specific per-sensor evaluation properties can be passed as 
 * "extraProperties" which exist only for that evaluation context duration
 * 
 * @deprecated See brooklyn.event.feed.jmx.JmxFeed
 */
@Deprecated
public abstract class AbstractSensorEvaluationContext {

	public static final Logger log = LoggerFactory.getLogger(AbstractSensorEvaluationContext.class);

	final Map extraProperties=[:]
	
	/** exception returned by call, or null if no error */
	@Nullable
	Exception error;
	
	/** may throw exception, or return UNSET to not change */
	public static final Object UNSET = [];
	
	def propertyMissing(String name) {
		if (extraProperties.containsKey(name)) return extraProperties.get(name)
		else throw new MissingPropertyException(name)
	}
	
	/** evaluate the closure in the (resolve) context of the implementing class (e.g. a specific HTTP response),
	 * so that methods and variables in the class are available to the closure;
	 * entity and sensor available for inspection but can be null during testing;
	 * default values are specified by the response context;
	 * return value is normally adapted by the caller (e.g. the sensor adapter) to suit the sensor;
	 * this method (and thus the closure) may return UNSET to indicate the adapter should not update the sensor */
	public Object evaluate(Entity entity, Sensor sensor, Closure c) {
		evaluate(entity:entity, sensor:sensor, c)
	}
	
	// not thread-safe due to use of extra properties; but not currently used multi-threaded anyway
	public synchronized Object evaluate(Map properties=[:], Closure c) {
		try {
			extraProperties << properties
			c.setDelegate(this)
			c.setResolveStrategy(Closure.DELEGATE_FIRST)
			return c.call(getDefaultValue())
		} catch (Exception e) {
			if (error) {
				if (log.isDebugEnabled()) log.debug "unable to evaluate sensor {} because call had an error ({}): {}", properties, error, e
				return UNSET;
			}
			else throw e;
		} finally {
			extraProperties.clear()
		}
	}
	
	protected abstract Object getDefaultValue();

}
