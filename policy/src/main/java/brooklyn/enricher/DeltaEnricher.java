package brooklyn.enricher;

import static brooklyn.util.GroovyJavaMethods.elvis;
import brooklyn.enricher.basic.AbstractTransformingEnricher;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.util.flags.TypeCoercions;

/**
 * Converts an absolute sensor into a delta sensor (i.e. the diff between the current and previous value)
 */
public class DeltaEnricher<T extends Number> extends AbstractTransformingEnricher<T> {
    Number last = 0;
    
    public DeltaEnricher(Entity producer, Sensor<T> source, AttributeSensor<T> target) {
        super(producer, source, target);
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        Number current = elvis(event.getValue(), 0);
        double newVal = current.doubleValue() - last.doubleValue();
        entity.setAttribute((AttributeSensor<T>)target, TypeCoercions.coerce(newVal, target.getTypeToken()));
        last = current;
    }
}
