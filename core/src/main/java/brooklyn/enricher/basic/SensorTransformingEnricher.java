package brooklyn.enricher.basic;

import groovy.lang.Closure;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.policy.Enricher;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Function;

public class SensorTransformingEnricher<T,U> extends AbstractTypeTransformingEnricher {

    private Function<? super T, ? extends U> transformation;

    public SensorTransformingEnricher(Entity producer, Sensor<T> source, Sensor<U> target, Function<? super T, ? extends U> transformation) {
        super(producer, source, target);
        this.transformation = transformation;
    }

    public SensorTransformingEnricher(Entity producer, Sensor<T> source, Sensor<U> target, Closure transformation) {
        super(producer, source, target);
        this.transformation = GroovyJavaMethods.functionFromClosure(transformation);
    }

    public SensorTransformingEnricher(Sensor<T> source, Sensor<U> target, Function<T,U> transformation) {
        super(null, source, target);
        this.transformation = transformation;
    }

    public SensorTransformingEnricher(Sensor<T> source, Sensor<U> target, Closure transformation) {
        super(null, source, target);
        this.transformation = GroovyJavaMethods.functionFromClosure(transformation);
    }

    @Override
    public void onEvent(SensorEvent event) {
        if (accept((T)event.getValue())) {
            if (target instanceof AttributeSensor)
                entity.setAttribute((AttributeSensor)target, compute((T)event.getValue()));
            else 
                entity.emit(target, compute((T)event.getValue()));
        }
    }

    protected boolean accept(T value) {
        return true;
    }

    protected U compute(T value) {
        return transformation.apply(value);
    }

    /** creates an enricher which listens to a source (from the producer), 
     * transforms it and publishes it under the target */
    public static <U,V> SensorTransformingEnricher<U,V> newInstanceTransforming(Entity producer, AttributeSensor<U> source,
            Function<U,V> transformation, AttributeSensor<V> target) {
        return new SensorTransformingEnricher<U,V>(producer, source, target, transformation);
    }

    /** as {@link #newInstanceTransforming(Entity, AttributeSensor, Function, AttributeSensor)}
     * using the same sensor as the source and the target */
    public static <T> SensorTransformingEnricher<T,T> newInstanceTransforming(Entity producer, AttributeSensor<T> sensor,
            Function<T,T> transformation) {
        return newInstanceTransforming(producer, sensor, transformation, sensor);
    }

}
