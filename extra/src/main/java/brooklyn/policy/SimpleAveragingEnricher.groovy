package brooklyn.policy

import brooklyn.entity.Entity
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.policy.basic.AbstractEnricher

class SimpleAveragingEnricher<T extends Number> extends AbstractEnricher {
    private LinkedList<T> values = new LinkedList<T>()
    
    int maxSize
    
    // rolling window? average?
    public SimpleAveragingEnricher(Entity producer, Sensor<T> source, Sensor<Double> target, int maxSize) {
        super(producer, source, target)
        this.maxSize = maxSize
    }
    
    public Number getAverage() {
        pruneValues()
        return values.sum() / values.size()
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        values.addLast(event.getValue())
        pruneValues()
        entity.setAttribute(target, getAverage())
    }
    
    private void pruneValues() {
        while(maxSize > -1 && values.size() > maxSize) {
            values.removeFirst()
        }
    }
}
