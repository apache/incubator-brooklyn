package brooklyn.enricher;

import java.util.LinkedList;

import brooklyn.enricher.basic.AbstractTransformingEnricher;
import brooklyn.enricher.basic.AbstractTypeTransformingEnricher;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.util.flags.TypeCoercions;


/**
* Transforms a sensor into a rolling average based on a fixed window size. This is useful for smoothing sample type metrics, 
* such as latency or CPU time
*/
class RollingMeanEnricher<T extends Number> extends AbstractTypeTransformingEnricher<T,Double> {
    private LinkedList<T> values = new LinkedList<T>();
    
    int windowSize;
    
    public RollingMeanEnricher(Entity producer, AttributeSensor<T> source, AttributeSensor<Double> target,
            int windowSize) {
        super(producer, source, target);
        this.windowSize = windowSize;
    }
    
    /** @returns null when no data has been received or windowSize is 0 */
    public Double getAverage() {
        pruneValues();
        return values.size() == 0 ? null : sum(values) / values.size();
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        values.addLast(event.getValue());
        pruneValues();
        entity.setAttribute((AttributeSensor<Double>)target, getAverage());
    }
    
    private void pruneValues() {
        while(windowSize > -1 && values.size() > windowSize) {
            values.removeFirst();
        }
    }
    
    private double sum(Iterable<? extends Number> vals) {
        double result = 0;
        for (Number val : vals) {
            result += val.doubleValue();
        }
        return result;
    }
}
