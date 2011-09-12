package brooklyn.enricher

import brooklyn.entity.Entity
import brooklyn.event.SensorEvent
import brooklyn.policy.basic.AbstractTransformingEnricher
import brooklyn.event.AttributeSensor


/**
* Transforms a sensor into a rolling average based on a fixed window size. This is useful for smoothing sample type metrics, 
* such as latency or CPU time
*/
class RollingMeanEnricher<T extends Number> extends AbstractTransformingEnricher {
    private LinkedList<T> values = new LinkedList<T>()
    
    int windowSize
    
    public RollingMeanEnricher(Entity producer, AttributeSensor<T> source, AttributeSensor<Double> target,
            int windowSize) {
        super(producer, source, target)
        this.windowSize = windowSize
    }
    
    /** @returns null when no data has been received or windowSize is 0 */
    public Double getAverage() {
        pruneValues()
        return values.size() == 0 ? null : values.sum() / values.size()
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        values.addLast(event.getValue())
        pruneValues()
        entity.setAttribute(target, getAverage())
    }
    
    private void pruneValues() {
        while(windowSize > -1 && values.size() > windowSize) {
            values.removeFirst()
        }
    }
}
