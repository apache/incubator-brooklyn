package brooklyn.event.adapter;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

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

    final List<Closure> polledListeners = []

    boolean lastWasSuccessful = false;

    // TODO Can't make this final because sub-classes want to override getAdapter() to have a more specific return type
    AbstractSensorAdapter adapter;
    
    public AbstractPollHelper(AbstractSensorAdapter adapter) {
        this.adapter = checkNotNull(adapter, "adapter");
    }
    
    public void init() {
        adapter.addActivationLifecycleListeners({ activatePoll() }, { deactivatePoll() })
    }
    
    public EntityLocal getEntity() { adapter.entity }

    public void addSensor(Sensor s, Closure c) {
        Object old = polledSensors.put(s, c)
        if (old!=null) log.warn "change of provider (discouraged) for sensor ${s} on ${entity}", new Throwable("path of discouraged change");
    }

    public void addListener(Closure c) {
        polledListeners.add(c)
    }

    ScheduledTask schedule;

    protected activatePoll() {
        if (adapter.pollPeriod!=null && adapter.pollPeriod.toMilliseconds()>0) {
            if (log.isDebugEnabled()) log.debug "activating poll (period {}) for {} sensors {} (using {})", adapter.pollPeriod, adapter.entity, polledSensors.keySet(), this
            Closure pollingTaskFactory = { new BasicTask(entity: entity, { executePoll() }); }
            schedule = new ScheduledTask(period: adapter.pollPeriod, pollingTaskFactory);
            entity.executionContext.submit schedule;
        } else {
            if (log.isDebugEnabled()) log.debug "activating poll (but leaving off, as period {}) for {} sensors {} (using {})", adapter.pollPeriod, adapter.entity, polledSensors.keySet(), this
        }
    }
    protected deactivatePoll() {
        if (log.isDebugEnabled()) log.debug "deactivating poll for {} sensors {} (using {})", adapter.entity, polledSensors.keySet(), this
        if (schedule) schedule.cancel();
    }

    protected boolean isEmpty() {
        polledSensors.isEmpty() && polledListeners.isEmpty();
    }
    
    /** implementation-specific generation of AbstractSensorEvaluationContext which is then typically passed to evaluateSensorsOnPollResponse */
    protected void executePoll() {
        if (isEmpty()) return;
        if (!adapter.isActivated()) return;
        AbstractSensorEvaluationContext response;
        try {
            if (log.isTraceEnabled()) log.trace "executing poll for {} sensors {} (using {})", adapter.entity, polledSensors.keySet(), this
            response = executePollOnSuccess();
            lastWasSuccessful = true;
        } catch (Exception e) {
            if (!adapter.isConnected() || !lastWasSuccessful) {
                if (log.isDebugEnabled()) log.debug("error reading ${this} (while not connected or not yet connected): ${e}")
            } else {
                log.warn("error reading ${this}: ${e}")
                if (log.isDebugEnabled()) log.debug("trace for error reading ${this} from ${entity}: ${e}", e)
            }
            lastWasSuccessful = false;
            response = executePollOnError(e);
        }
        if (log.isTraceEnabled()) log.trace "poll for {} got: {}", adapter.entity, response
        if (response!=null) evaluateSensorsOnResponse(response)
    }

    /** method for implementations to supply to execute the poll call which drives the children */
    protected abstract AbstractSensorEvaluationContext executePollOnSuccess();

    /** optional action to take if a poll has just failed; defaults to null */
    protected AbstractSensorEvaluationContext executePollOnError(Exception e) { null }

    /** returns additional context information for errors */
    protected String getOptionalContextForErrors(AbstractSensorEvaluationContext response) { null }

    void evaluateSensorsOnResponse(AbstractSensorEvaluationContext response) {
        polledSensors.each { s, c -> evaluateSensorOnResponse(s, c, response) }
        polledListeners.each {
            Object v = response.evaluate({it})
            if (v != AbstractSensorEvaluationContext.UNSET) {
                it.call(v)
            }
        }
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
                log.debug "${this} unable to compute ${s} for ${entity}: ${e.getMessage()}"+
                        (optionalContextForErrors?"\n"+optionalContextForErrors:"")
            else
                if (log.isDebugEnabled()) log.debug "${this} unable to compute ${s} for ${entity} (when inactive): ${e.getMessage()}"+
                    (optionalContextForErrors?"\n"+optionalContextForErrors:"")
        }
        return null
    }

    public String toString() { super.toString()+"["+entity+"]" }

}

public abstract class AbstractChainablePollHelper extends AbstractPollHelper {

    public AbstractChainablePollHelper(AbstractSensorAdapter adapter) {
        super(adapter);
    }

    @Override
    void evaluateSensorsOnResponse(AbstractSensorEvaluationContext response) {
        super.evaluateSensorsOnResponse(response)
        for (AbstractChainablePollHelper sub: subPollers) {
            sub.evaluateSensorsOnResponse(response);
        }
    }

    protected boolean isEmpty() {
        super.isEmpty() && subPollers.isEmpty();
    }

    List<AbstractChainablePollHelper> subPollers = []
    void addSubPoller(AbstractChainablePollHelper poller) { subPollers << poller }

}
