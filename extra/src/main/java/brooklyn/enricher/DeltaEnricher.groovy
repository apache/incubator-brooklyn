package brooklyn.enricher

import java.util.LinkedList

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.policy.basic.AbstractTransformingEnricher

class DeltaEnricher<T extends Number> extends AbstractTransformingEnricher {
    Number last = 0
    
    public DeltaEnricher(Entity producer, Sensor<T> source, Sensor<T> target) {
        super(producer, source, target)
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        Number current = event.getValue() ?: 0
        entity.setAttribute(target, current - last)
        last = current
    }
}
