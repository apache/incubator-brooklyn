package brooklyn.policy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.group.DynamicCluster
import brooklyn.event.AttributeSensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy

public class ResizerPolicy<T extends Number> extends AbstractPolicy implements Policy, SensorEventListener {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResizerPolicy.class)
    
    private DynamicCluster entity
    private String[] metricName
    private double metricLowerBound
    private double metricUpperBound
    private int minSize
    private int maxSize = Integer.MAX_VALUE
    
    Entity producer
    AttributeSensor<T> source
    
    public ResizerPolicy(Entity producer, AttributeSensor<T> source) {
        this.producer = producer
        this.source = source
    }
    
    public void setEntity(DynamicCluster entity) {
        super.setEntity(entity)
        subscribe(producer, source, this)
    }
    
    public ResizerPolicy setMetricLowerBound(double val) {
        this.metricLowerBound = val
        this
    }
    
    public ResizerPolicy setMetricUpperBound(double val) {
        this.metricUpperBound = val
        this
    }
    
    public ResizerPolicy setMinSize(int val) {
        this.minSize = val
        this
    }
    
    public ResizerPolicy setMaxSize(int val) {
        this.maxSize = val
        this
    }
    
    public void onEvent(SensorEvent<T> event) {
        def val = event.getValue()
        def currentSize = entity.getCurrentSize()
        def desiredSize = calculateDesiredSize(val)
        
        if (desiredSize != currentSize) {
            LOG.info(String.format("policy resizer resizing: metric=%s, workrate=%s, lowerBound=%s, upperBound=%s; currentSize=%d, desiredSize=%d, minSize=%d, maxSize=%d", 
                    Arrays.toString(metricName), val, metricLowerBound, metricUpperBound, currentSize, desiredSize, minSize, maxSize))
            entity.resize(desiredSize)
        } else {
            if (LOG.isLoggable(Level.FINER)) LOG.finer(String.format("policy resizer doing nothing: metric=%s, workrate=%s, lowerBound=%s, upperBound=%s; currentSize=%d, minSize=%d, maxSize=%d", 
                    Arrays.toString(metricName), val, metricLowerBound, metricUpperBound, currentSize, minSize, maxSize))
        }
    }
    
    // TODO Could have throttling etc so don't repeatedly call grow; standard control theory stuff such as
    //      PID design (proportional-integral-derivative)
    // TODO Could show example of overriding this to do something smarter. For example, if metric is a number then
    //      grow/shrink by some scale, e.g. grow by (metric <= lowerBound ? 0 : (metric < lowerBound*2 ? 1 : (metric < lowerBound*4 ? 2 : 3))) 
    protected int calculateDesiredSize(double currentMetric) {
        def currentSize = entity.getCurrentSize()
        def desiredSize
        if (0 < currentMetric - metricUpperBound) {
            desiredSize = currentSize+Math.ceil(currentSize * ((currentMetric - metricUpperBound) / metricUpperBound))// scale out
        } else if (0 < metricLowerBound - currentMetric) {
            desiredSize = currentSize-Math.ceil(currentSize * (Math.abs(currentMetric - metricLowerBound) / metricLowerBound)) // scale back
        } else {
            desiredSize = currentSize
        }
        desiredSize = Math.max(minSize, desiredSize)
        desiredSize = Math.min(maxSize, desiredSize)
        return desiredSize
    }
}
