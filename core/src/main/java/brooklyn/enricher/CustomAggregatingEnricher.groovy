package brooklyn.enricher

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.AbstractAggregatingEnricher;
import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener;

/**
 * Subscribes to events from producers with a sensor of type T, aggregates them with the 
 * provided closure and emits the result on the target sensor V.
 * @param <T>
 */
class CustomAggregatingEnricher<S,T> extends AbstractAggregatingEnricher<S,T> implements SensorEventListener<S> {
    
    private static final Logger LOG = LoggerFactory.getLogger(CustomAggregatingEnricher.class)
    
    protected Closure aggegator
    
    /**
     * @param aggregator Should take a list of values and return a single, aggregate value
     * @param defaultValue Default value to populate the list given to aggregator, defaults to null
     */
    public CustomAggregatingEnricher(List<Entity> producer, Sensor<S> source, Sensor<T> target, 
            Closure aggregator, S defaultValue=null) {
        super(producer, source, target, defaultValue)
        this.aggegator = aggregator
    }
    
    @Override
    public void onEvent(SensorEvent<S> event) {
        try {
            assert event.getSource() in AttributeSensor : "Enricher $this only applicable to AttributeSensors, not $event"
            values.put(event.getSource(), event.getValue())
            entity.setAttribute(target, getAggregate())
        } catch (Throwable t) {
            t.printStackTrace();
            throw t
        }
    }
    
    public T getAggregate() {
        synchronized (values) {
            return (T) aggegator.call(values.values())
        }
    }
    
    public static <N extends Number> CustomAggregatingEnricher<N,N> getSummingEnricher(
            List<Entity> producer, Sensor<N> source, Sensor<N> target) {
        return new CustomAggregatingEnricher<N,N>(producer, source, target, { it?.sum(0, {it ?: 0}) ?: 0 }, 0)
    }

    public static <N extends Number> CustomAggregatingEnricher<N,Double> getAveragingEnricher(
            List<Entity> producer, Sensor<N> source, Sensor<Double> target) {
        return new CustomAggregatingEnricher<N,Double>(producer, source, target, {
            (it == null || it.isEmpty()) ? 0d : ((Double) it.sum(0, {it ?: 0})) / it.size()
        }, 0d)
    }

}
