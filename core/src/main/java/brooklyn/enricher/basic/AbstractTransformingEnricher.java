package brooklyn.enricher.basic;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;

/**
 * Convenience base for transforming a single sensor into a single new sensor
 */
public abstract class AbstractTransformingEnricher<T> extends AbstractEnricher implements SensorEventListener<T> {
    private Entity producer;
    private Sensor<T> source;
    protected Sensor<T> target;
    
    public AbstractTransformingEnricher(Entity producer, Sensor<T> source, Sensor<T> target) {
        this.producer = producer;
        this.source = source;
        this.target = target;
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        subscribe(producer, source, this);
    }
}
