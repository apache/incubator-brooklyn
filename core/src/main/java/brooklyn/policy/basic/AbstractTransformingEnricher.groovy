package brooklyn.policy.basic

import java.util.LinkedList;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.SensorEventListener;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicAttributeSensor;

abstract class AbstractTransformingEnricher<T> extends BaseEnricher implements SensorEventListener<T> {
    private Entity producer
    private Sensor<T> source
    protected Sensor<T> target
    
    public AbstractTransformingEnricher(Entity producer, Sensor<T> source, Sensor<T> target) {
        this.producer = producer
        this.source = source
        this.target = target
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        subscribe(producer, source, this)
    }
}
