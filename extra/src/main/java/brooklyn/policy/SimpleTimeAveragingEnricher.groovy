package brooklyn.policy

import java.util.LinkedList

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.policy.basic.AbstractPolicy

class SimpleTimeAveragingEnricher<T extends Number> extends AbstractPolicy implements EventListener<T> {
    
    // FIXME What if no values yet? Document what it will return, e.g. -1
    // TODO Do we want a hasValues? or Size? but what about pruning?
    // TODO Do we want to retain oldest value, if not older than some threshold? 
    //      Otherwise if heard nothing since 1.1 seconds ago then we'd report per-second avarege is -1.
    
    // TODO next...
    // 1. unit tests
    // 2. want a version of this / a way for if raw data is absolute count of msgs-processed
    //    could have a class explicitly for that;
    //    or an intermediate enricher that creates a "workrate" attribute by subtracting the latest from last 
    
    public static final BasicAttributeSensor<Number> AVERAGE = [ Number, "enricher.average", "Enriched time aware average" ]
    
    private LinkedList<T> values = new LinkedList<T>()
    private LinkedList<Long> timestamps = new LinkedList<Long>()
    private Entity producer
    private Sensor<T> source
    
    long timePeriod
    
    public SimpleTimeAveragingEnricher(Entity producer, Sensor<T> source, long timePeriod) {
        this.producer = producer
        this.source = source
        this.timePeriod = timePeriod
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        subscribe(producer, source, this)
    }

    public void onEvent(SensorEvent<T> event) {
        long now = System.currentTimeMillis()
        onEvent(event, now)
    }
    
    public void onEvent(SensorEvent<T> event, long eventTime) {
        values.addLast(event.getValue())
        timestamps.addLast(eventTime)
        pruneValues(eventTime)
        entity.emit(AVERAGE, getAverage(eventTime))
    }
    
    public Number getAverage() {
        return getAverage(System.currentTimeMillis())
    }
    
    public Number getAverage(long now) {
        pruneValues(now)
        
        long start = now - timePeriod
        long end
        double weightedAverage
        
        timestamps.eachWithIndex { timestamp, i ->
            end = timestamp
            weightedAverage += ((end - start) / timePeriod) * values[i]
            start = timestamp
        }
        end = now
        
        // FIXME Don't extrapolate
        weightedAverage += ((end - start) / timePeriod) * values.last()
        
        return weightedAverage
    }
    
    private void pruneValues(long now) {
        // FIXME Instead perhaps keep a window of size timePeriod (e.g. if we haven't received anything recently then don't prune older stuff yet).
        while(timestamps.first() < (now - timePeriod)) {
            timestamps.removeFirst()
            values.removeFirst()
        }
    }
}
