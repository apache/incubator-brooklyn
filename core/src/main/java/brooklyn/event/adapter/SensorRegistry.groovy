package brooklyn.event.adapter

import groovy.lang.Closure

import java.util.List
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.adapter.legacy.ValueProvider


/**
 * This class manages the periodic polling of a set of sensors, to update the attribute values 
 * of a particular {@link Entity}.
 */
@SuppressWarnings("deprecation")
public class SensorRegistry {
    static final Logger log = LoggerFactory.getLogger(SensorRegistry.class);
 
    final EntityLocal entity

    /**
     * @deprecated in 0.4. use new SensorAdapter model.
     */
	@Deprecated
    final Map<?, ?> properties  = [
            period : 500,
            connectDelay : 1000
        ]

    /**
     * @deprecated in 0.4. use new SensorAdapter model.
     */
	@Deprecated
    private ScheduledExecutorService _exec = null;
    synchronized ScheduledExecutorService getExec() {
        if (_exec==null) {
            log.warn("using legacy executor service sensor model in $entity -- class should be updated to use adapters.");
            _exec = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        }
        return _exec;
    }
    
    /**
     * @deprecated in 0.4. use new SensorAdapter model.
     */
	@Deprecated
    private final Map<AttributeSensor, ValueProvider> providers = [:]

    /**
     * @deprecated in 0.4. use new SensorAdapter model.
     */
    @Deprecated
    private final Map<AttributeSensor, ScheduledFuture> scheduled = [:]

	private final Set<AbstractSensorAdapter> adapters = []
	
    public SensorRegistry(EntityLocal entity, Map properties = [:]) {
        this.entity = entity
        this.properties << properties
    }

	/** records an adapter that has been created for use with this registry;
	 * implementations may return a compatible adapter if one is already registered */
	public <T extends AbstractSensorAdapter> T register(T adapter) {
		if (!adapters.add(adapter)) /* already known */ return;
		adapter.register(this)
		return adapter
	}

	// TODO might be useful to have a lookup mechanism, or register ignore duplicates
	//	sensorRegistry.adapters.find({ it in OldJmxSensorAdapter })?.connect(block: true, publish: (getEntityClass().hasSensor(JMX_URL)))
	
	boolean activated = false;
	
	private List<Closure> activationListeners = []
	private List<Closure> deactivationListeners = []
	void addActivationLifecycleListeners(Closure onUp, Closure onDown) {
		activationListeners << onUp
		deactivationListeners << onDown
	}
	public void activateAdapters() {
		if (log.isDebugEnabled()) log.debug "activating adapters at sensor registry for {}", this, entity
		activated = true;
		activationListeners.each { it.call() }
	}
	public void deactivateAdapters() {
		if (log.isDebugEnabled()) log.debug "deactivating adapters at sensor registry for {}", this, entity
		deactivationListeners.each { it.call() }
	}

	public void close() {
		activated = false;
		deactivateAdapters();
		if (_exec) exec.shutdownNow()
		scheduled.each { key, ScheduledFuture future -> future.cancel(true) }
	}

    /**
     * @deprecated in 0.4. use new SensorAdapter model.
    */
	@Deprecated
    public <T> void addSensor(AttributeSensor<T> sensor, ValueProvider<? extends T> provider) {
        addSensor(sensor, provider, properties.period)
    }

    /**
     * @deprecated in 0.4. use new SensorAdapter model.
     */
	@Deprecated
    public <T> void addSensor(AttributeSensor<T> sensor, ValueProvider<? extends T> provider, long period) {
        if (log.isDebugEnabled()) log.debug "adding calculated sensor {} with delay {} to {}", sensor.name, period, entity
        providers.put(sensor, provider)
        
        Closure safeCalculate = {
            try {
                T newValue = provider.compute()
                entity.setAttribute(sensor, newValue)
            } catch (Exception e) {
				if (activated)
                	log.error "Error calculating value for sensor ${sensor} on entity ${entity}", e
				else 
                    if (log.isDebugEnabled()) log.debug "Error (post deactivation) calculating value for sensor ${sensor} on entity ${entity}", e
            }
        }
        
        scheduled[sensor] = exec.scheduleWithFixedDelay(safeCalculate, 0L, period, TimeUnit.MILLISECONDS)
    }

    /**
      * @deprecated in 0.4. use new SensorAdapter model.
      */
	@Deprecated
    public <T> void removeSensor(AttributeSensor<T> sensor) {
        if (log.isDebugEnabled()) log.debug "removing sensor {} from {}", sensor.name, entity
        providers.remove(sensor)
        scheduled.remove(sensor)?.cancel(true)
    }

    /**
      * @deprecated in 0.4. use new SensorAdapter model.
      */
	@Deprecated
    private void updateAll() {
        if (log.isDebugEnabled()) log.debug "updating all sensors for {}", entity
        providers.each {
                AttributeSensor sensor, ValueProvider provider ->
                def newValue = provider.compute()
                if (log.isDebugEnabled()) log.debug "update for attribute {} to {}", sensor.name, newValue
                entity.setAttribute(sensor, newValue)
            }
    }

    /**
      * @deprecated in 0.4. use new SensorAdapter model.
      */
	@Deprecated
    private void update(Sensor sensor) {
        if (!providers.containsKey(sensor)) throw new IllegalStateException("Sensor ${sensor.name} not found");
        ValueProvider<?> provider = providers.get(sensor)
        def newValue = provider.compute()
        if (log.isDebugEnabled()) log.debug "update for attribute {} on {}: new value {}", sensor.name, entity, newValue
        entity.setAttribute(sensor, newValue)
    }
}
