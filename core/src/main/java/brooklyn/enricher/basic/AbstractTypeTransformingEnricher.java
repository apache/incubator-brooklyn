package brooklyn.enricher.basic;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;

/**
 * Convenience base for transforming a single sensor into a single new sensor of the same type
 */
public abstract class AbstractTypeTransformingEnricher<T,U> extends AbstractEnricher implements SensorEventListener<T> {
    private Entity producer;
    private Sensor<T> source;
    protected Sensor<U> target;
    
    public AbstractTypeTransformingEnricher(Entity producer, Sensor<T> source, Sensor<U> target) {
        this.producer = producer;
        this.source = source;
        this.target = target;
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        if (producer==null) producer = entity;
        subscribe(producer, source, this);
        
        if (source instanceof AttributeSensor) {
            Object value = producer.getAttribute((AttributeSensor)source);
            // TODO Aled didn't you write a convenience to "subscribeAndRunIfSet" ? (-Alex)
            if (value!=null)
                onEvent(new BasicSensorEvent(source, producer, value));
        }
    }
}
