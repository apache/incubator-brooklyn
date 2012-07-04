package brooklyn.enricher.basic;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;

/**
 * Convenience base for transforming a single sensor into a single new sensor of the same type
 */
public abstract class AbstractTransformingEnricher<T> extends AbstractTypeTransformingEnricher<T,T> {

    public AbstractTransformingEnricher(Entity producer, Sensor<T> source, Sensor<T> target) {
        super(producer, source, target);
    }
    
}
