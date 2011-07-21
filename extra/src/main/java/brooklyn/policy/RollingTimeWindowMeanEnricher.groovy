package brooklyn.policy

import brooklyn.entity.Entity
import brooklyn.event.SensorEvent
import brooklyn.policy.basic.AbstractTransformingEnricher;
import brooklyn.event.AttributeSensor

/**
 * Transforms a sensor into a rolling average based on a time window- all values within the window are weighted or discarded 
 * based on the timestamps associated with them (discards occur when a new value is added or an average is requested)
 * 
 * This will not extrapolate figures- it is assumed a value is valid and correct for the entire time period between it and
 * the previous value. Normally, the average attribute is only updated when a new value arrives so it can give a fully informed
 * average, but there is a danger of this going stale
 * 
 * When an average is requested, it is likely there will be a segment of the window for which there isn't a value. 
 * Instead of extrapolating a value and providing different extrapolation techniques, instead the average is reported with 
 * a confidence value which reflects the fraction of the time window for which the values were valid
 * 
 * Consumers of the average may ignore the confidence value and just use the last known average. They could multiply the returned 
 * value by the confidence value to get a decay-type behavior as the window empties. A third alternative is to, at a certain
 * confidence threshold, report that the average is no longer meaningful
 * 
 * The default average when no data has been received is 0, with a confidence of 0
 */

class RollingTimeWindowMeanEnricher<T extends Number> extends AbstractTransformingEnricher {
    
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
    
    public RollingTimeWindowMeanEnricher(Entity producer, AttributeSensor<T> source, 
        AttributeSensor<ConfidenceQualifiedNumber> target, long timePeriod) {
        super(producer, source, target)
        this.timePeriod = timePeriod
    }

    @Override
    public void onEvent(SensorEvent<T> event) {
        onEvent(event, event.getTimestamp())
    }
    
    public void onEvent(SensorEvent<T> event, long eventTime) {
        values.addLast(event.getValue())
        timestamps.addLast(eventTime)
        pruneValues(eventTime)
        entity.setAttribute(target, getAverage(eventTime)) //TODO this can potentially go stale... maybe we need to timestamp as well?
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
        
        if(confidence == 0) {
            return lastAverage = [lastAverage.value, 0]
        }
        
        Long start = now - timePeriod
        Long end
        Double weightedAverage = 0
        
        timestamps.eachWithIndex { timestamp, i ->
            end = timestamp
            weightedAverage += ((end - start) / (confidence * timePeriod)) * values[i]
            start = timestamp
        }
        
        return lastAverage = [weightedAverage, confidence]
    }
    
    private void pruneValues(long now) {
        while(timestamps.size() > 0 && timestamps.first() < (now - timePeriod)) {
            timestamps.removeFirst()
            values.removeFirst()
        }
    }
}
