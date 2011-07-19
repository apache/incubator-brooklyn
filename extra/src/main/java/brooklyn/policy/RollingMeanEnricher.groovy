package brooklyn.policy

import brooklyn.entity.Entity
import brooklyn.event.SensorEvent
import brooklyn.policy.basic.AbstractEnricher
import brooklyn.event.AttributeSensor

class RollingMeanEnricher<T extends Number> extends AbstractEnricher {
    private LinkedList<T> values = new LinkedList<T>()
    
    int maxSize
    
    // rolling window? average?
    public RollingMeanEnricher(Entity producer, AttributeSensor<T> source, AttributeSensor<Double> target,
            int maxSize) {
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
