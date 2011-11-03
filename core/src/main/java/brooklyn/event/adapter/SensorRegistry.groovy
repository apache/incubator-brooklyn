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
public class SensorRegistry {
    static final Logger log = LoggerFactory.getLogger(SensorRegistry.class);
 
    final EntityLocal entity
	
	@Deprecated
    final Map<?, ?> properties  = [
            period : 500,
            connectDelay : 1000
        ]
 
	@Deprecated
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors())
 
	@Deprecated
    private final Map<AttributeSensor, ValueProvider> providers = [:]
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
	
	private List<Closure> activationListeners = []
	private List<Closure> deactivationListeners = []
	void addActivationLifecycleListeners(Closure onUp, Closure onDown) {
		activationListeners << onUp
		deactivationListeners << onDown
	}
	public void activateAdapters() {
		log.debug "activating adapters at sensor registry for {}", this, entity
		activationListeners.each { it.call() }
	}
	public void deactivateAdapters() {
		log.debug "deactivating adapters at sensor registry for {}", this, entity
		deactivationListeners.each { it.call() }
	}

	public void close() {
		deactivateAdapters();
		exec.shutdownNow()
		scheduled.each { key, ScheduledFuture future -> future.cancel(true) }
	}

	@Deprecated
    public <T> void addSensor(AttributeSensor<T> sensor, ValueProvider<? extends T> provider) {
        addSensor(sensor, provider, properties.period)
    }

	@Deprecated
    public <T> void addSensor(AttributeSensor<T> sensor, ValueProvider<? extends T> provider, long period) {
        log.debug "adding calculated sensor {} with delay {} to {}", sensor.name, period, entity
        providers.put(sensor, provider)
        
        Closure safeCalculate = {
            try {
                T newValue = provider.compute()
                entity.setAttribute(sensor, newValue)
            } catch (Exception e) {
                log.error "Error calculating value for sensor ${sensor} on entity ${entity}", e
            }
        }
        
        scheduled[sensor] = exec.scheduleWithFixedDelay(safeCalculate, 0L, period, TimeUnit.MILLISECONDS)
    }

	@Deprecated
    public <T> void removeSensor(AttributeSensor<T> sensor) {
        log.debug "removing sensor {} from {}", sensor.name, entity
        providers.remove(sensor)
        scheduled.remove(sensor)?.cancel(true)
    }

	@Deprecated
    private void updateAll() {
        log.debug "updating all sensors for {}", entity
        providers.each {
                AttributeSensor sensor, ValueProvider provider ->
                def newValue = provider.compute()
                log.debug "update for attribute {} to {}", sensor.name, newValue
                entity.setAttribute(sensor, newValue)
            }
    }
    
	@Deprecated
    private void update(Sensor sensor) {
        if (!providers.containsKey(sensor)) throw new IllegalStateException("Sensor ${sensor.name} not found");
        ValueProvider<?> provider = providers.get(sensor)
        def newValue = provider.compute()
        log.debug "update for attribute {} on {}: new value {}", sensor.name, entity, newValue
        entity.setAttribute(sensor, newValue)
    }
}
