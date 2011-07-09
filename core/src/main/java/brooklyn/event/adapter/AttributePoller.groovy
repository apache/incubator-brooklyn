package brooklyn.event.adapter

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor

/**
 * This class manages the periodic polling of a set of sensors, to update the attribute values 
 * of a particular {@link Entity}.
 */
public class AttributePoller {
    static final Logger log = LoggerFactory.getLogger(AttributePoller.class);
 
    final EntityLocal entity
    final Map<?, ?> properties  = [
            period : 500,
            connectDelay : 1000
        ]   
 
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors())
 
    private final Map<AttributeSensor, ValueProvider> providers = [:]
    private final Map<AttributeSensor, ScheduledFuture> scheduled = [:]
    
    public AttributePoller(EntityLocal entity, Map properties = [:]) {
        this.entity = entity
        this.properties << properties
    }

    public <T> void addSensor(AttributeSensor<T> sensor, ValueProvider<? extends T> provider) {
        addSensor(sensor, provider, properties.period)
    }
    
    public <T> void addSensor(AttributeSensor<T> sensor, ValueProvider<? extends T> provider, long period) {
        log.debug "adding calculated sensor {} with delay {}", sensor.name, period
        providers.put(sensor, provider)
        
        Closure safeCalculate = {
            try {
                T newValue = provider.compute()
                entity.setAttribute(sensor, newValue)
            } catch (Exception e) {
                log.error "Error calculating value for sensor $sensor on entity $entity", e
            }
        }
        
        scheduled[sensor.getName()] = exec.scheduleWithFixedDelay(safeCalculate, 0L, period, TimeUnit.MILLISECONDS)
    }

    public <T> void removeSensor(AttributeSensor<T> sensor) {
        log.debug "removing sensor", sensor.name
        providers.remove(sensor)
        scheduled.remove(sensor).cancel(true)
    }

    public void close() {
        exec.shutdownNow()
        scheduled.each { key, ScheduledFuture future -> future.cancel(true) }
    }

    private void updateAll() {
        log.debug "updating all jmx sensors"
        providers.entrySet() each { Map.Entry<AttributeSensor,ValueProvider> e ->
                AttributeSensor sensor = e.getKey()
                ValueProvider provider = e.getValue()
                def newValue = provider.compute()
                log.debug "update for attribute {} to {}", sensor.name, newValue
                entity.setAttribute(sensor, newValue)
            }
    }
    
    private void update(Sensor sensor) {
        if (!providers.containsKey(sensor)) throw new IllegalStateException("Sensor $sensor.name not found");
        ValueProvider<?> provider = providers.get(sensor)
        def newValue = provider.compute()
        log.debug "update for attribute {} to {}", sensor.name, newValue
        entity.setAttribute(sensor, newValue)
    }
}
