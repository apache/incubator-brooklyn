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
        values.addLast(event.getValue())
        timestamps.addLast(now)
        pruneValues(now)
        entity.emit(AVERAGE, getAverage(now))
    }
    
    public Number getAverage() {
        return getAverage(System.currentTimeMillis())
    }
    
    public Number getAverage(long now) {
        pruneValues(now)
        
        long start = now - timePeriod
        long end
        double weightedTotal
        
        timePeriod.eachWithIndex { timestamp, i ->
            end = timestamp
            weightedTotal += ((end - start) / timePeriod) * values[i]
            start = timestamp
        }
        end = now
        weightedTotal += ((end - start) / timePeriod) * values.last()
        return weightedTotal/values.size()
    }
    
    private void pruneValues(long now) {
        while(timestamps.first() < now - timePeriod) {
            timestamps.removeFirst()
            values.removeFirst()
        }
    }
}
