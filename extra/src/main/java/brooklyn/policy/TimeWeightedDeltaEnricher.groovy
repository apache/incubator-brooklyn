package brooklyn.policy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.policy.basic.AbstractTransformingEnricher

class TimeWeightedDeltaEnricher<T extends Number> extends AbstractTransformingEnricher {
    private static final Logger LOG = LoggerFactory.getLogger(TimeWeightedDeltaEnricher.class)
    
    Number lastValue
    long lastTime
    int unitMillis
    
    // default 1 second
    public static <T extends Number> TimeWeightedDeltaEnricher getPerSecondDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target) {
        return new TimeWeightedDeltaEnricher<T>(producer, source, target, 1000)
    }
    
    public TimeWeightedDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target, int unitMillis) {
        super(producer, source, target)
        this.unitMillis = unitMillis
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        onEvent(event, event.getTimestamp())
    }
    
    public void onEvent(SensorEvent<T> event, long eventTime) {
        Number current = event.getValue() ?: 0
        
        if (eventTime > lastTime && current != lastValue) {
            double delta
            if (lastValue == null) {
                delta = current
            } else {
                double duration = lastTime == null ? unitMillis : eventTime - lastTime
                delta = (current - lastValue) / (duration / unitMillis)
            }
            entity.setAttribute(target, delta)
            LOG.trace "set $this to ${delta}, $lastValue -> $current at $eventTime" 
            lastValue = current
            lastTime = eventTime
        }
    }
}
