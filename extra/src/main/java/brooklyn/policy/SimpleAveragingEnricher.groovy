package brooklyn.policy

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicSensor
import brooklyn.policy.basic.AbstractPolicy;

class SimpleAveragingEnricher<T extends Number> extends AbstractPolicy implements EventListener<T> {
    public static final BasicAttributeSensor<Number> AVERAGE = [ Number, "enricher.average", "Enriched average" ]
    
    private LinkedList<T> values = new LinkedList<T>()
    private Entity producer
    private Sensor<T> source
    
    int maxSize
    
    public SimpleAveragingEnricher(Entity producer, Sensor<T> source, int maxSize) {
        this.producer = producer
        this.source = source
        this.maxSize = maxSize
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        subscribe(producer, source, this)
    }
    
    public Number getAverage() {
        pruneValues()
        return values.sum() / values.size()
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        values.addLast(event.getValue())
        pruneValues()
        entity.emit(AVERAGE, getAverage())
    }
    
    private void pruneValues() {
        while(maxSize > -1 && values.size() > maxSize) {
            values.removeFirst()
        }
    }
}
