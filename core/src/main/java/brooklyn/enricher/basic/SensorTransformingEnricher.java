package brooklyn.enricher.basic;

import groovy.lang.Closure;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Function;

public class SensorTransformingEnricher<T,U> extends AbstractTypeTransformingEnricher {

    private Function<T, U> transformation;

    public SensorTransformingEnricher(Entity producer, Sensor<T> source, Sensor<U> target, Function<T,U> transformation) {
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

}
