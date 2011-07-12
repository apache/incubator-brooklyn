package brooklyn.policy.basic

import java.util.LinkedList;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicAttributeSensor;


abstract class AbstractEnricher<T> extends AbstractPolicy implements EventListener<T> {
    private Entity producer
    private Sensor<T> source
    protected Sensor<Double> target
    
    public AbstractEnricher(Entity producer, Sensor<T> source, Sensor<?> target) {
        this.producer = producer
        this.source = source
        this.target = target
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        subscribe(producer, source, this)
    }
}
