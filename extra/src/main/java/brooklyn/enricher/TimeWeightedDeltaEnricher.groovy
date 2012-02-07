package brooklyn.enricher

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.AbstractTransformingEnricher;
import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent

/**
 * Converts an absolute sensor into a delta sensor (i.e. the diff between the current and previous value),
 * presented as a units/timeUnit based on the event timing
 */
public class TimeWeightedDeltaEnricher<T extends Number> extends AbstractTransformingEnricher {
    private static final Logger LOG = LoggerFactory.getLogger(TimeWeightedDeltaEnricher.class)
    
    Number lastValue
    long lastTime
    int unitMillis
    Closure postProcessor
    
    // default 1 second
    public static <T extends Number> TimeWeightedDeltaEnricher getPerSecondDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target) {
        return new TimeWeightedDeltaEnricher<T>(producer, source, target, 1000)
    }

    public TimeWeightedDeltaEnricher(Entity producer, Sensor<T> source, Sensor<Double> target, int unitMillis, Closure postProcessor={it}) {
        super(producer, source, target)
        this.unitMillis = unitMillis
        this.postProcessor = postProcessor
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        onEvent(event, event.getTimestamp())
    }
    
    public void onEvent(SensorEvent<T> event, long eventTime) {
        Number current = event.getValue() ?: 0
        
        if (eventTime > lastTime) {
            double delta
            if (lastValue == null) {
                delta = current
            } else {
                double duration = lastTime == null ? unitMillis : eventTime - lastTime
                delta = (current - lastValue) / (duration / unitMillis)
            }
            double deltaPostProcessed = postProcessor.call(delta)
            entity.setAttribute(target, deltaPostProcessed)
            LOG.trace "set $this to ${deltaPostProcessed}, $lastValue -> $current at $eventTime" 
            lastValue = current
            lastTime = eventTime
        }
    }
}
