package org.overpaas.policy

import java.util.logging.Level
import java.util.logging.Logger

import org.overpaas.activity.Event
import org.overpaas.activity.EventFilters
import org.overpaas.activity.EventListener

public class ResizerPolicy<T extends Comparable<T>> implements Policy, EventListener {
    
    private static final Logger LOG = Logger.getLogger(ResizerPolicy.class.getName())
    
    private ResizableEntity entity
    private String[] metricName
    private T metricLowerBound
    private T metricUpperBound
    private int minSize
    private int maxSize = Integer.MAX_VALUE
    
    public ResizerPolicy() {
    }
    
    public void setEntity(ResizableEntity entity) {
        this.entity = entity
    }
    
    public void setMetricName(String[] val) {
        this.metricName = val
    }
    
    public void setMetricLowerBound(T val) {
        this.metricLowerBound = val
    }
    
    public void setMetricUpperBound(T val) {
        this.metricUpperBound = val
    }
    
    public void setMinSize(int val) {
        this.minSize = val
    }
    
    public void setMaxSize(int val) {
        this.maxSize = val
    }
    
    public void postConstruct() {
        entity.subscribe(EventFilters.newMetricFilter(metricName), this)
    }
    
    public void onEvent(Event event) {
        def val = event.getMetrics().getRaw(metricName)
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
    protected Object calculateDesiredSize(val) {
        def currentSize = entity.getCurrentSize()
        def desiredSize
        if (metricUpperBound.compareTo(val) < 0) {
            desiredSize = currentSize+1 // scale out
        } else if (metricLowerBound.compareTo(val) > 0) {
            desiredSize = currentSize-1 // scale back
        } else {
            desiredSize = currentSize
        }
        desiredSize = Math.max(minSize, desiredSize)
        desiredSize = Math.min(maxSize, desiredSize)
        return desiredSize
    }
}
