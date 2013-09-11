package brooklyn.enricher;

import groovy.lang.Closure;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.AbstractAggregatingEnricher;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEventListener;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;

/**
 * Subscribes to events from producers with a sensor of type T, aggregates them with the 
 * provided closure and emits the result on the target sensor V.
 * @param <T>
 */
public class CustomAggregatingEnricher<S,T> extends AbstractAggregatingEnricher<S,T> implements SensorEventListener<S> {
    
    private static final Logger LOG = LoggerFactory.getLogger(CustomAggregatingEnricher.class);
    
    protected final Function<Collection<S>, T> aggregator;
    
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
     * @param defaultIniitalValueForUnreportedSensors Default value to populate the collection given to aggregator, 
     * where sensors are null or not present initially, defaults to null (note however that subsequent null reports will put an explicit null)
     */
    public CustomAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator, S defaultIniitalValueForUnreportedSensors) {
        super(flags, source, target, defaultIniitalValueForUnreportedSensors);
        this.aggregator = aggregator;
    }
    
    public CustomAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator) {
        this(flags, source, target, aggregator, null);
    }
    
    public CustomAggregatingEnricher(AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator, S defaultValue) {
        this(Collections.<String,Object>emptyMap(), source, target, aggregator, defaultValue);
    }
    
    public CustomAggregatingEnricher(AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Function<Collection<S>, T> aggregator) {
        this(Collections.<String,Object>emptyMap(), source, target, aggregator, null);
    }

    /**
     * @param flags
     * @param source
     * @param target
     * @param aggregator   Should take a collection of values and return a single, aggregate value
     * @param defaultValueForUnreportedSensors
     * 
     * @see #CustomAggregatingEnricher(Map, AttributeSensor, AttributeSensor, Function, Object)
     */
    @SuppressWarnings("unchecked")
    public CustomAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target,
            Closure<?> aggregator, S defaultValueForUnreportedSensors) {
        this(flags, source, target, GroovyJavaMethods.<Collection<S>, T>functionFromClosure((Closure<T>)aggregator), defaultValueForUnreportedSensors);
    }

    public CustomAggregatingEnricher(Map<String,?> flags, AttributeSensor<? extends S> source, AttributeSensor<T> target, Closure<?> aggregator) {
        this(flags, source, target, aggregator, null);
    }

    public CustomAggregatingEnricher(AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator, S defaultValueForUnreportedSensors) {
        this(Collections.<String,Object>emptyMap(), source, target, aggregator, defaultValueForUnreportedSensors);
    }

    public CustomAggregatingEnricher(AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator) {
        this(Collections.<String,Object>emptyMap(), source, target, aggregator, null);
    }

    @Override
    public void onUpdated() {
        try {
            entity.setAttribute(target, getAggregate());
        } catch (Throwable t) {
            LOG.warn("Error calculating and setting aggregate for enricher "+this, t);
            throw Throwables.propagate(t);
        }
    }
    
    public T getAggregate() {
        synchronized (values) {
            return (T) aggregator.apply(values.values());
        }
    }

    // FIXME Clean up explosion of overloading, caused by groovy-equivalent default vals...
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags, AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator, S defaultVal) {
        return new CustomAggregatingEnricher<S,T>(flags, source, target, aggregator, defaultVal);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags, AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator) {
        return newEnricher(flags, source, target, aggregator, null);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator, S defaultVal) {
        return newEnricher(Collections.<String,Object>emptyMap(), source, target, aggregator, defaultVal);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            AttributeSensor<S> source, AttributeSensor<T> target, Closure<?> aggregator) {
        return newEnricher(Collections.<String,Object>emptyMap(), source, target, aggregator, null);
    }
    
    
    // FIXME Clean up explosion of overloading, caused by groovy-equivalent default vals...
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags, AttributeSensor<S> source, AttributeSensor<T> target, Function<Collection<S>, T> aggregator, S defaultVal) {
        return new CustomAggregatingEnricher<S,T>(flags, source, target, aggregator, defaultVal);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            Map<String,?> flags, AttributeSensor<S> source, AttributeSensor<T> target, Function<Collection<S>, T> aggregator) {
        return newEnricher(flags, source, target, aggregator, null);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            AttributeSensor<S> source, AttributeSensor<T> target, Function<Collection<S>, T> aggregator, S defaultVal) {
        return newEnricher(Collections.<String,Object>emptyMap(), source, target, aggregator, defaultVal);
    }
    public static <S,T> CustomAggregatingEnricher<S,T> newEnricher(
            AttributeSensor<S> source, AttributeSensor<T> target, Function<Collection<S>, T> aggregator) {
        return newEnricher(Collections.<String,Object>emptyMap(), source, target, aggregator, null);
    }
    
    /** creates an enricher which sums over all children/members, 
     * defaulting to excluding sensors which have not published anything (or published null), and null if there are no sensors; 
     * this behaviour can be customised, both default value for sensors, and what to report if no sensors
     */
    public static <N extends Number, T extends Number> CustomAggregatingEnricher<N,T> newSummingEnricher(
            Map<String,?> flags, AttributeSensor<N> source, final AttributeSensor<T> target, 
            final N defaultValueForUnreportedSensors, final T valueToReportIfNoSensors) {
        Function<Collection<N>, T> aggregator = new Function<Collection<N>, T>() {
            @Override public T apply(Collection<N> vals) {
                return sum(vals, defaultValueForUnreportedSensors, valueToReportIfNoSensors, target.getTypeToken());
            }
        };
        return new CustomAggregatingEnricher<N,T>(flags, source, target, aggregator, defaultValueForUnreportedSensors);
    }
    /** @see {@link #newSummingEnricher(Map, AttributeSensor, AttributeSensor, Number, Number)}  
     * @deprecated since 0.6.0 to prevent sprawl, use one of the fuller methods */ @Deprecated
    public static <N extends Number, T extends Number> CustomAggregatingEnricher<N,T> newSummingEnricher(
            Map<String,?> flags, AttributeSensor<N> source, final AttributeSensor<T> target) {
        return newSummingEnricher(flags, source, target, null, null);
    }
    /** @see {@link #newSummingEnricher(Map, AttributeSensor, AttributeSensor, Number, Number)}  
     * @deprecated since 0.6.0 to prevent sprawl, use one of the fuller methods */ @Deprecated
    public static <N extends Number> CustomAggregatingEnricher<N,N> newSummingEnricher(
            Map<String,?> flags, AttributeSensor<N> source, final AttributeSensor<N> target, N defaultValue) {
        return newSummingEnricher(flags, source, target,  
                cast(defaultValue, source.getTypeToken()),
                cast(defaultValue, target.getTypeToken()));
    }
    /** @see {@link #newSummingEnricher(Map, AttributeSensor, AttributeSensor, Number, Number)} */
    public static <N extends Number> CustomAggregatingEnricher<N,N> newSummingEnricher(
            AttributeSensor<N> source, AttributeSensor<N> target) {
        return newSummingEnricher(Collections.<String,Object>emptyMap(), source, target);
    }
    /** @see {@link #newSummingEnricher(Map, AttributeSensor, AttributeSensor, Number, Number)}  
     * @deprecated since 0.6.0 to prevent sprawl, use one of the fuller methods */ @Deprecated
    public static <N extends Number> CustomAggregatingEnricher<N,N> newSummingEnricher(
            AttributeSensor<N> source, AttributeSensor<N> target, N defaultValue) {
        return newSummingEnricher(Collections.<String,Object>emptyMap(), source, target, 
                cast(defaultValue, source.getTypeToken()),
                cast(defaultValue, target.getTypeToken()));
    }

    /** creates an enricher which averages over all children/members, 
     * defaulting to excluding sensors which have not published anything (or published null), and null if there are no sensors; 
     * this behaviour can be customised, both default value for sensors, and what to report if no sensors
     */
    public static <N extends Number> CustomAggregatingEnricher<N,Double> newAveragingEnricher(
            Map<String,?> flags, AttributeSensor<? extends N> source, final AttributeSensor<Double> target,
            final N defaultValueForUnreportedSensors, final Double valueToReportIfNoSensors) {
        
        Function<Collection<N>, Double> aggregator = new Function<Collection<N>, Double>() {
            @Override public Double apply(Collection<N> vals) {
                int count = count(vals, defaultValueForUnreportedSensors!=null);
                return (count==0) ? valueToReportIfNoSensors : 
                    (Double) ((sum(vals, defaultValueForUnreportedSensors, 0, TypeToken.of(Double.class)) / count));
            }
        };
        return new CustomAggregatingEnricher<N,Double>(flags, source, target, aggregator, defaultValueForUnreportedSensors);
    }
    
    /** @see #newAveragingEnricher(Map, AttributeSensor, AttributeSensor, Number, Double) 
     * @deprecated since 0.6.0 to prevent sprawl, use one of the fuller methods */ @Deprecated
    public static <N extends Number> CustomAggregatingEnricher<N,Double> newAveragingEnricher(
            Map<String,?> flags, AttributeSensor<? extends N> source, AttributeSensor<Double> target) {
        return newAveragingEnricher(flags, source, target, null, null);
    }

    /** @see #newAveragingEnricher(Map, AttributeSensor, AttributeSensor, Number, Double) 
     * @deprecated since 0.6.0 to prevent sprawl, use one of the fuller methods */ @Deprecated
    public static <N extends Number> CustomAggregatingEnricher<N,Double> newAveragingEnricher(
            Map<String,?> flags, AttributeSensor<? extends N> source, AttributeSensor<Double> target,
            Number defaultValue) {
        return newAveragingEnricher(flags, source, target, 
                cast(defaultValue, source.getTypeToken()),
                cast(defaultValue, target.getTypeToken()));
    }

    /** @see #newAveragingEnricher(Map, AttributeSensor, AttributeSensor, Number, Double) */
    public static <N extends Number> CustomAggregatingEnricher<N,Double> newAveragingEnricher(
            AttributeSensor<N> source, AttributeSensor<Double> target) {
        return newAveragingEnricher(Collections.<String,Object>emptyMap(), source, target);
    }

    @SuppressWarnings("unchecked")
    private static <N extends Number> N cast(Number n, TypeToken<? extends N> numberType) {
        return (N) TypeCoercions.castPrimitive(n, numberType.getRawType());
    }

    private static <N extends Number> N sum(Iterable<? extends Number> vals, Number valueIfNull, Number valueIfNone, TypeToken<N> type) {
        double result = 0d;
        int count = 0;
        if (vals!=null) {
            for (Number val : vals) { 
                if (val!=null) {
                    result += val.doubleValue();
                    count++;
                } else if (valueIfNull!=null) {
                    result += valueIfNull.doubleValue();
                    count++;
                }
            }
        }
        if (count==0) return cast(valueIfNone, type);
        return cast(result, type);
    }
    
    private static int count(Iterable<? extends Object> vals, boolean includeNullValues) {
        int result = 0;
        if (vals!=null) 
            for (Object val : vals) 
                if (val!=null || includeNullValues) result++;
        return result;
    }

}
