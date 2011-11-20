package brooklyn.event.adapter;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.util.task.BasicTask
import brooklyn.util.task.ScheduledTask


/** captures common fields and processes for pollers that support sensor adapters */
public abstract class AbstractPollHelper {
	public static final Logger log = LoggerFactory.getLogger(AbstractPollHelper.class);
	
	final Map<AttributeSensor, Closure> polledSensors = [:]
	
	boolean lastWasSuccessful = false;
	
	AbstractSensorAdapter adapter;
	public AbstractPollHelper(AbstractSensorAdapter adapter) {
		this.adapter = adapter;
		adapter.addActivationLifecycleListeners({ activatePoll() }, { deactivatePoll() })
	}
	public EntityLocal getEntity() { adapter.entity }
	
	public void addSensor(Sensor s, Closure c) {
		Object old = polledSensors.put(s, c)
		if (old!=null) log.warn "change of provider (discouraged) for sensor ${s} on ${entity}", new Throwable("path of discouraged change");
	}
		
	ScheduledTask schedule;
	
	protected activatePoll() {
		log.debug "activating poll (period {}) for {} sensors {} (using {})", adapter.pollPeriod, adapter.entity, polledSensors.keySet(), this
		Closure pollingTaskFactory = { new BasicTask(entity: entity, { executePoll() }); }
		schedule = new ScheduledTask(period: adapter.pollPeriod, pollingTaskFactory);
		entity.executionContext.submit schedule;
	}
	protected deactivatePoll() {
		log.debug "deactivating poll for {} sensors {} (using {})", adapter.entity, polledSensors.keySet(), this
		if (schedule) schedule.cancel();
	}
	
	/** implementation-specific generation of AbstractSensorEvaluationContext which is then typically passed to evaluateSensorsOnPollResponse */
	protected void executePoll() {
		if (polledSensors.isEmpty()) return;
		if (!adapter.isActivated()) return;
		AbstractSensorEvaluationContext response;
		try {
			log.debug "executing poll for {} sensors {} (using {})", adapter.entity, polledSensors.keySet(), this
			response = executePollOnSuccess();
			lastWasSuccessful = true;
		} catch (Exception e) {
			if (!adapter.isConnected() || !lastWasSuccessful)
				log.debug("error reading ${this} (disconnect): ${e}", e)
			else
				log.warn("error reading ${this} (disconnect): ${e}", e)
			lastWasSuccessful = false;
			response = executePollOnError(e);
		}
		log.debug "poll for {} got: {}", adapter.entity, response
		if (response!=null) evaluateSensorsOnResponse(response)
	}
	
	protected abstract AbstractSensorEvaluationContext executePollOnSuccess();
	
	protected AbstractSensorEvaluationContext executePollOnError(Exception e) { null }
	
	protected String getOptionalContextForErrors(AbstractSensorEvaluationContext response) { null }
	
	void evaluateSensorsOnResponse(AbstractSensorEvaluationContext response) {
		polledSensors.each { s, c -> evaluateSensorOnResponse(s, c, response) }
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
				log.debug "unable to compute ${s} for ${entity} (when deactive): ${e.getMessage()}"+
					(optionalContextForErrors?"\n"+optionalContextForErrors:""), e
		}
		return null
	}
}
