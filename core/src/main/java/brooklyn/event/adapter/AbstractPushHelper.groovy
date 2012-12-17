package brooklyn.event.adapter;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.management.NotificationListener

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor


/** 
 * Captures common fields and processes for pushers that support sensor adapters.
 * 
 * For example, a JMX notification subscription would push notifications here, to be 
 * turned into sensor updates.
 */
public abstract class AbstractPushHelper {
	public static final Logger log = LoggerFactory.getLogger(AbstractPushHelper.class);
	
	private final Map<AttributeSensor, Closure> pushedSensors = [:]
    private final AbstractSensorAdapter adapter;
	
	boolean lastWasSuccessful = false;
	
	public AbstractPushHelper(AbstractSensorAdapter adapter) {
		this.adapter = checkNotNull(adapter, "adapter");
	}
    
    public void init() {
		adapter.addActivationLifecycleListeners({ activatePushing() }, { deactivatePushing() })
	}
    
	public EntityLocal getEntity() { adapter.entity }
	
	public void addSensor(Sensor s, Closure c) {
		Object old = pushedSensors.put(s, c)
		if (old!=null) log.warn "change of provider (discouraged) for sensor ${s} on ${entity}", new Throwable("path of discouraged change");
	}
		
	// FIXME if we subscribe too early, will we get exceptions? Do we need to delay subscription until here?
	protected abstract void activatePushing();

	protected void deactivatePushing() {}
	
	/** implementation-specific generation of AbstractSensorEvaluationContext which is then typically passed to evaluateSensorsOnPollResponse */
	protected void onPush(AbstractSensorEvaluationContext notification) {
		if (log.isDebugEnabled()) log.debug "push for {} got: {}", adapter.entity, notification
		if (notification!=null) evaluateSensorsOnResponse(notification)
	}
	
	protected String getOptionalContextForErrors(AbstractSensorEvaluationContext response) { null }
	
	void evaluateSensorsOnResponse(AbstractSensorEvaluationContext response) {
		pushedSensors.each { s, c -> evaluateSensorOnResponse(s, c, response) }
	}
	
	Object evaluateSensorOnResponse(Sensor<?> s, Closure c, AbstractSensorEvaluationContext response) {
		try {
			Object v = response.evaluate(entity, s, c)
			if (v!=AbstractSensorEvaluationContext.UNSET) {
				v = v?.asType(s.getType());
				entity.setAttribute(s, v);
				return v
			}
		} catch (Exception e) {
			String optionalContextForErrors = getOptionalContextForErrors(response)
			if (adapter.isConnected())
				log.warn "unable to compute ${s} for ${entity}: ${e.getMessage()}"+
					(optionalContextForErrors?"\n"+optionalContextForErrors:""), e
			else
				if (log.isDebugEnabled()) log.debug "unable to compute ${s} for ${entity} (when deactive): ${e.getMessage()}"+
					(optionalContextForErrors?"\n"+optionalContextForErrors:""), e
		}
		return null
	}
}
