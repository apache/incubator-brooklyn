package brooklyn.enricher

import groovy.lang.Closure

import java.util.Collection
import java.util.List
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.AbstractAggregatingEnricher
import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.SensorEventListener

import com.google.common.base.Function

/**
 * Subscribes to events from producers with a sensor of type T, aggregates them with the 
 * provided closure and emits the result on the target sensor V.
 * @param <T>
 */
public class CustomAggregatingEnricher<S,T> extends AbstractAggregatingEnricher<S,T> implements SensorEventListener<S> {
    
    private static final Logger LOG = LoggerFactory.getLogger(CustomAggregatingEnricher.class)
    
    protected final Function<List<S>, T> aggregator
    
    /**
     * The valid keys for the flags are:
     * - producers: a collection of entities to be aggregated
     * - allMembers: indicates that should track members of the entity that the aggregator is associated with,
     *               to aggregate across all those members.
     * - filter:     a Predicate or Closure, indicating which entities to include
     * 
     * @param flags
     * @param source
     * @param target
     * @param aggregator   Aggregates a collection of values, to return a single value for the target sensor
     * @param defaultValue Default value to populate the collection given to aggregator, defaults to null
     */
    public CustomAggregatingEnricher(Map<String,?> flags=[:], Sensor<S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator, S defaultValue=null) {
        super(flags, source, target, defaultValue)
        this.aggregator = aggregator
    }

    /**
     * @param flags
     * @param source
     * @param target
     * @param aggregator   Should take a collection of values and return a single, aggregate value
     * @param defaultValue
     * 
     * @see #CustomAggregatingEnricher(Map<String,?>, Sensor<S>, AttributeSensor<T> target, Function<Collection<S>, T> aggregator, S defaultValue)
     */
    public CustomAggregatingEnricher(Map<String,?> flags=[:], Sensor<S> source, AttributeSensor<T> target,
            Closure aggregator, S defaultValue=null) {
        this(flags, source, target, aggregator as Function<Collection<S>, T>, defaultValue)
    }

    /**
     * @deprecated will be deleted in 0.5. Use CustomAggregatingEnricher(source, target, aggregator, deafultValue, producers:producer)
     */
    public CustomAggregatingEnricher(List<Entity> producer, Sensor<S> source, AttributeSensor<T> target, 
            Closure aggregator, S defaultValue=null) {
        this(producer, source, target, aggregator as Function, defaultValue)
    }

    /**
     * @deprecated will be deleted in 0.5. Use CustomAggregatingEnricher(source, target, aggregator, deafultValue, producers:producer)
     */
    public CustomAggregatingEnricher(List<Entity> producer, Sensor<S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator, S defaultValue=null) {
        super(producer, source, target, defaultValue)
        this.aggregator = aggregator
    }

    @Override
    public void onUpdated() {
        try {
            entity.setAttribute(target, getAggregate())
        } catch (Throwable t) {
            LOG.warn("Error calculating and setting aggregate for enricher $this", t)
            throw t
        }
    }
    
    public T getAggregate() {
        synchronized (values) {
            return (T) aggregator.apply(values.values())
        }
    }

    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags=[:], Sensor<S> source, AttributeSensor<T> target, Closure aggregator, T defaultVal=null) {
        return newEnricher(flags, source, target, aggregator as Function, defaultVal)
    }
            
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags=[:], Sensor<S> source, AttributeSensor<T> target, Function<List<S>, T> aggregator, T defaultVal=null) {
        return new CustomAggregatingEnricher<S,T>(flags, source, target, aggregator, defaultVal)
    }
            
    public static <N extends Number> CustomAggregatingEnricher<N,N> newSummingEnricher(
            Map<String,?> flags=[:], Sensor<N> source, AttributeSensor<N> target) {
        return new CustomAggregatingEnricher<N,N>(flags, source, target, { it?.sum(0, {it ?: 0}) ?: 0 }, 0)
    }

    /**
     * @deprecated will be deleted in 0.5. Use newAveragingEnricher(source, target, producers:producer, allMembers:true)
     */
    @Deprecated
    public static <N extends Number> CustomAggregatingEnricher<N,N> getSummingEnricher(
            List<Entity> producer, Sensor<N> source, AttributeSensor<N> target) {
        return newSummingEnricher(source, target, producers:producer, allMembers:true)
    }

    public static <N extends Number> CustomAggregatingEnricher<N,Double> newAveragingEnricher(
            Map<String,?> flags=[:], Sensor<N> source, AttributeSensor<Double> target) {
        return new CustomAggregatingEnricher<N,Double>(flags, source, target,
                { (it == null || it.isEmpty()) ? 0d : ((Double) it.sum(0, {it ?: 0})) / it.size() },
                0d)
    }

    /**
     * @deprecated will be deleted in 0.5. Use newAveragingEnricher(source, target, producers:producer, allMembers:true)
     */
    @Deprecated
    public static <N extends Number> CustomAggregatingEnricher<N,Double> getAveragingEnricher(
            List<Entity> producer, Sensor<N> source, AttributeSensor<Double> target) {
        return newAveragingEnricher(source, target, producers:producer, allMembers:true)
    }
    
}
