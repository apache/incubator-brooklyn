package brooklyn.policy.wip;

import java.util.LinkedList;
import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.policy.wip.BufferEvent;
import brooklyn.policy.basic.AbstractPolicy;

class AggregatorEnricher<T> extends AbstractPolicy implements EventListener<BufferEvent>{
    private List<BufferingEnricher<T>> buffers = new LinkedList<BufferingEnricher<T>>()
    
    public BufferingEnricher(Entity owner, Entity producer, Sensor<T> source) {
        super.setEntity(owner)
        super.subscribe(producer, source, this)
    }
    
    public void addSensor(Entity producer, Sensor<T> sensor) {
        buffers.add(new BufferingEnricher<T>(this, producer, sensor))
    }
    
    @Override
    public void onEvent(SensorEvent<BufferEvent> event) {
        // TODO Auto-generated method stub
        
    }
}
