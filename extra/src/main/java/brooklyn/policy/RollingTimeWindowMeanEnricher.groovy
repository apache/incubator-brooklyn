package brooklyn.policy

import brooklyn.entity.Entity
import brooklyn.event.SensorEvent
import brooklyn.policy.basic.AbstractEnricher;
import brooklyn.event.AttributeSensor

class RollingTimeWindowMeanEnricher<T extends Number> extends AbstractEnricher {
    // FIXME What if no values yet? Document what it will return, e.g. -1
    
    public static class ConfidenceQualifiedNumber {
        final Number value
        final double confidence
        
        public ConfidenceQualifiedNumber(Number value, double confidence) {
            this.value = value
            this.confidence = confidence
        }
    }
    
    private LinkedList<T> values = new LinkedList<T>()
    private LinkedList<Long> timestamps = new LinkedList<Long>()
    ConfidenceQualifiedNumber lastAverage = [0,0]
    
    long timePeriod
    boolean extrapolateFromLast
    
    public RollingTimeWindowMeanEnricher(Entity producer, AttributeSensor<T> source, AttributeSensor<Double> target,
            long timePeriod) {
        super(producer, source, target)
        this.timePeriod = timePeriod
        this.extrapolateFromLast = extrapolateFromLast
    }

    public void onEvent(SensorEvent<T> event) {
        onEvent(event, System.currentTimeMillis())
    }
    
    public void onEvent(SensorEvent<T> event, long eventTime) {
        values.addLast(event.getValue())
        timestamps.addLast(eventTime)
        pruneValues(eventTime)
        entity.setAttribute(target, getAverage(eventTime).value)
    }
    
    public ConfidenceQualifiedNumber getAverage() {
        return getAverage(System.currentTimeMillis())
    }
    
    public ConfidenceQualifiedNumber getAverage(long now) {
        pruneValues(now)
        if(timestamps.size() == 0) {
            return lastAverage = [lastAverage.value, 0]
        }
        
        Double confidence = (timePeriod - (now - timestamps.last())) / timePeriod
        Long start = now - timePeriod
        Long end
        Double weightedAverage = 0
        
        timestamps.eachWithIndex { timestamp, i ->
            end = timestamp
            weightedAverage += ((end - start) / timePeriod) * values[i]
            start = timestamp
        }
        end = now
        
        weightedAverage += ((end - start) / timePeriod) *  values.last()
        
        return lastAverage = [weightedAverage, confidence]
    }
    
    private void pruneValues(long now) {
        while(timestamps.size() > 0 && timestamps.first() < (now - timePeriod)) {
            timestamps.removeFirst()
            values.removeFirst()
        }
    }
}
