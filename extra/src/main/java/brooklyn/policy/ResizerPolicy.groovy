package brooklyn.policy

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.group.DynamicCluster
import brooklyn.event.AttributeSensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy

public class ResizerPolicy<T extends Number> extends AbstractPolicy implements SensorEventListener<T> {
    private static final Logger LOG = LoggerFactory.getLogger(ResizerPolicy.class)

    private DynamicCluster dynamicCluster
    private String[] metricName
    private double metricLowerBound
    private double metricUpperBound
    private int minSize
    private int maxSize = Integer.MAX_VALUE
    
    private Executor executor = Executors.newSingleThreadExecutor()
    private Closure resizeAction = {
        int desire = desiredSize.get()
        dynamicCluster.resize(desire)
        while (desire != desiredSize.get()) {
            desire = desiredSize.get()
            dynamicCluster.resize(desire)
        }
    }

    private final AtomicInteger desiredSize = new AtomicInteger(0)
    private final AtomicBoolean suspended = new AtomicBoolean(false)

    AttributeSensor<T> source

    /**
     * @param averagingSource - A sensor that averages a relevant metric across the attaching entity
     */
    public ResizerPolicy(AttributeSensor<T> averagingSource) {
        this.source = averagingSource
    }

    @Override
    public void setEntity(Entity entity) {
        super.setEntity(entity)
        assert entity instanceof DynamicCluster
        this.dynamicCluster = entity
        subscribe(entity, source, this)
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
    
    public void suspend() {
        suspended.set(true)
    }
    
    public void resume() {
        suspended.set(false)
    }

    private void resize() {
        executor.execute(resizeAction)
    }

    public void onEvent(SensorEvent<T> event) {
        def val = event.getValue()
        def currentSize = dynamicCluster.getCurrentSize()
        desiredSize.set(calculateDesiredSize(val))

        if (desiredSize.get() != currentSize) {
            LOG.info "policy resizer resizing: metric={}, workrate={}, lowerBound={}, upperBound={}; currentSize={}, desiredSize={}, minSize={}, maxSize={}",
                    Arrays.toString(metricName), val, metricLowerBound, metricUpperBound, currentSize, desiredSize.get(), minSize, maxSize
            resize()
        } else {
            LOG.debug "policy resizer doing nothing: metric={}, workrate={}, lowerBound={}, upperBound={}; currentSize={}, minSize={}, maxSize={}",
                    Arrays.toString(metricName), val, metricLowerBound, metricUpperBound, currentSize, minSize, maxSize
        }
    }

    // TODO Could have throttling etc so don't repeatedly call grow; standard control theory stuff such as
    //      PID design (proportional-integral-derivative)
    // TODO Could show example of overriding this to do something smarter. For example, if metric is a number then
    //      grow/shrink by some scale, e.g. grow by (metric <= lowerBound ? 0 : (metric < lowerBound*2 ? 1 : (metric < lowerBound*4 ? 2 : 3)))
    protected int calculateDesiredSize(double currentMetric) {
        def currentSize = dynamicCluster.getCurrentSize()
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
