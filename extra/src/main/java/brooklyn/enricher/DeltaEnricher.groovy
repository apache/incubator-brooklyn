package brooklyn.enricher

import java.util.LinkedList

import brooklyn.enricher.basic.BaseTransformingEnricher;
import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicAttributeSensor


/**
 * Converts an absolute sensor into a delta sensor (i.e. the diff between the current and previous value)
 */
public class DeltaEnricher<T extends Number> extends BaseTransformingEnricher {
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
