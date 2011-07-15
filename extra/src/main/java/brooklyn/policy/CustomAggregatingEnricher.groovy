package brooklyn.policy

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.management.SubscriptionHandle
import brooklyn.policy.basic.AbstractPolicy

/**
 * Subscribes to events from producers with a sensor of type T, aggregates them with the 
 * provided closure and emits the result on the target sensor.
 * @param <T>
 */
class CustomAggregatingEnricher<T> extends AbstractPolicy implements EventListener<T> {
    
    private Sensor<T> source
    protected Sensor<?> target
    private Closure aggegator
    private T defaultValue
    
    private Map<Entity, T> values = new HashMap<Entity, T>()
    
    /**
     * @param aggregator Should take a list of values and return a single, aggregate value
     * @param defaultValue Default value to populate the list given to aggregator, defaults to null
     */
    public CustomAggregatingEnricher(List<Entity> producer, Sensor<T> source, Sensor<?> target, 
            Closure aggregator, T defaultValue=null) {
        producer.each { values.put(it, defaultValue) }
        this.source = source
        this.target = target
        this.aggegator = aggregator
        this.defaultValue = defaultValue
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        values.each { subscribe(it.key, source, this) }
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        values.put(event.getSource(), event.getValue())
        entity.emit(target, getAggregate())
    }
    
    public Object getAggregate() {
        return aggegator.call(values.values())
    }
    
    public void addProducer(Entity producer) {
        values.put(producer, defaultValue)
        subscribe(producer, source, this)
    }
    
    public T removeProducer(Entity producer) {
        unsubscribe(producer)
        values.remove(producer)
    }
    
    public static <R extends Number> CustomAggregatingEnricher<R> getSummingEnricher(
            List<Entity> producer, Sensor<R> source, Sensor<R> target) {
        return new CustomAggregatingEnricher<R>(producer, source, target, { it.sum(0) }, 0)
    }

    public static <R extends Number> CustomAggregatingEnricher<R> getAveragingEnricher(
            List<Entity> producer, Sensor<R> source, Sensor<Double> target) {
        return new CustomAggregatingEnricher<R>(producer, source, target, {
            it.isEmpty() ? 0d : (Double) it.sum() / it.size()
        }, 0d)
    }

}
