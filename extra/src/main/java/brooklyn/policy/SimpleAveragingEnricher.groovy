package brooklyn.policy

import brooklyn.event.EventListener;
import brooklyn.event.SensorEvent;
import brooklyn.policy.basic.AbstractPolicy;

class SimpleAveragingEnricher<T extends Number> extends AbstractPolicy implements EventListener<T> {
    private LinkedList<T> values = new LinkedList<T>()
    
    public Number getAverage() {
        return values.sum() / values.size()
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        values.addLast(event.getValue())
    }
}
