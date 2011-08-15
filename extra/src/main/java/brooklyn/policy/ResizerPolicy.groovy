package brooklyn.policy

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.group.DynamicCluster
import brooklyn.event.AttributeSensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.policy.trait.Suspendable;

public class ResizerPolicy<T extends Number> extends AbstractPolicy implements SensorEventListener<T>, Suspendable {
    
    // TODO Need a better approach for resume/suspend: currently DynamicCluster calls this on start/stop,
    // but other entities do not!
    
    // TODO ResizerPolicy should work with anything that is "Resizable", rather than only DynamicCluster.
    // Should not lookup DynamicCluster.SERVICE_UP or Group.getCurrentSize
    
    // TODO The onEvent and policy generics say <T extends Number>, but then it is treated as a double in calculateDesiredSize.
    // Should be documented...

    // TODO It's unfortunate we need to use an executor, and bad that we instantiate a single-threaded executor here.
    // Want a better way...
    
    // TODO Currently only does one resize at a time.
    // Imagine the threshold is set to 100. If we ramp up workrate to 450, but the policy sees events for 101 then 450, 
    // the first event will cause it to provision a single new instance. After the several minutes that this takes, it 
    // will then concurrently provision up to the correct number of instances (in this case, 5).
    
    private static final Logger LOG = LoggerFactory.getLogger(ResizerPolicy.class)

    private AttributeSensor<T> source
    private DynamicCluster dynamicCluster
    private String[] metricName
    private double metricLowerBound
    private double metricUpperBound
    private int minSize
    private int maxSize = Integer.MAX_VALUE

    private final AtomicInteger desiredSize = new AtomicInteger(0)

    /** Lock held if we are in the process of resizing. */
    private final AtomicBoolean resizing = new AtomicBoolean(false)
    private final AtomicBoolean suspended = new AtomicBoolean(false)
    
    private Executor executor = Executors.newSingleThreadExecutor() 
    private Closure resizeAction = {
        try {
            LOG.info "policy resizer performing resizing..."
            int desire = desiredSize.get()
            dynamicCluster.resize(desire)
            while (desire != desiredSize.get()) {
                LOG.info "policy resizer performing re-resizing..."
                desire = desiredSize.get()
                dynamicCluster.resize(desire)
            }
            LOG.info "policy resizer resizing complete"
        } finally {
            resizing.set(false)
        }
    }


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
    
    @Override
    public void suspend() {
        suspended.set(true)
    }

    @Override
    public void resume() {
        suspended.set(false)
    }

    private void resize() {
        if (!suspended.get() && dynamicCluster.getAttribute(DynamicCluster.SERVICE_UP) && resizing.compareAndSet(false, true)) {
            executor.execute(resizeAction)
        }
    }

    public void onEvent(SensorEvent<T> event) {
        def val = event.getValue()
        def currentSize = dynamicCluster.getCurrentSize()
        desiredSize.set(calculateDesiredSize(val))

        if (desiredSize.get() != currentSize) {
            LOG.debug "policy resizer resizing: metric={}, workrate={}, lowerBound={}, upperBound={}; currentSize={}, desiredSize={}, minSize={}, maxSize={}",
                    source, val, metricLowerBound, metricUpperBound, currentSize, desiredSize.get(), minSize, maxSize
            resize()
        } else {
            LOG.trace "policy resizer doing nothing: metric={}, workrate={}, lowerBound={}, upperBound={}; currentSize={}, minSize={}, maxSize={}",
                    source, val, metricLowerBound, metricUpperBound, currentSize, minSize, maxSize
        }
    }

    // TODO Could have throttling etc so don't repeatedly call grow; standard control theory stuff such as
    //      PID design (proportional-integral-derivative)
    // TODO Could show example of overriding this to do something smarter
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
