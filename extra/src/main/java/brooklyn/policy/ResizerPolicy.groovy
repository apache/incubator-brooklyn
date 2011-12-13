package brooklyn.policy

import java.util.Map
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.trait.Resizable
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.util.task.BasicTask

public class ResizerPolicy<T extends Number> extends AbstractPolicy implements SensorEventListener<T> {
    
    // TODO Currently only does one resize at a time.
    // Imagine the threshold is set to 100. If we ramp up workrate to 450, but the policy sees events for 101 then 450, 
    // the first event will cause it to provision a single new instance. After the several minutes that this takes, it 
    // will then concurrently provision up to the correct number of instances (in this case, 5).
    
    private static final Logger LOG = LoggerFactory.getLogger(ResizerPolicy.class)

    private AttributeSensor<T> source
    private Resizable resizable
    private boolean entityStartable = false
    private String[] metricName
    private T metricLowerBound
    private T metricUpperBound
    private int minSize
    private int maxSize = Integer.MAX_VALUE

    private final AtomicInteger desiredSize = new AtomicInteger(0)

    /** Lock held if we are in the process of resizing. */
    private final AtomicBoolean resizing = new AtomicBoolean(false)
    
    private Closure resizeAction = {
        try {
            LOG.info "policy resizer performing resizing..."
            int desire = desiredSize.get()
            resizable.resize(desire)
            while (desire != desiredSize.get()) {
                LOG.info "policy resizer performing re-resizing..."
                desire = desiredSize.get()
                resizable.resize(desire)
            }
            LOG.info "policy resizer resizing complete"
        } finally {
            resizing.set(false)
        }
    }

    /**
     * @param averagingSource - A sensor that averages a relevant metric across the attaching entity
     */
    public ResizerPolicy(Map properties = [:], AttributeSensor<T> averagingSource) {
        super(properties)
        this.source = averagingSource
    }

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity)
        assert entity instanceof Resizable
        resizable = entity
        if(entity instanceof Startable) {
            entityStartable = true
        }
        subscribe(entity, source, this)
    }

    public ResizerPolicy setMetricRange(T min, T max) {
        setMetricLowerBound(min)
        setMetricUpperBound(max)
    }

    public ResizerPolicy setMetricLowerBound(T val) {
        this.metricLowerBound = val
        this
    }

    public ResizerPolicy setMetricUpperBound(T val) {
        this.metricUpperBound = val
        this
    }

    public ResizerPolicy setSizeRange(int min, int max) {
        setMinSize(min)
        setMaxSize(max)
    }
    
    public ResizerPolicy setMinSize(int val) {
        this.minSize = val
        this
    }

    public ResizerPolicy setMaxSize(int val) {
        this.maxSize = val
        this
    }
    

    private void resize() {
        if (isRunning() // if I'm running
            && (!entityStartable || entity.getAttribute(Startable.SERVICE_UP)) // my entity is up
            && resizing.compareAndSet(false, true)) { // and I'm not in the middle of resizing already
            ((EntityLocal)entity).getManagementContext().getExecutionContext(entity).submit(new BasicTask(resizeAction))
        }
    }

    public void onEvent(SensorEvent<T> event) {
        if (isDestroyed()) return //swallow events when destroyed
        
        T val = event.getValue()
        int currentSize = resizable.getCurrentSize()
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
    protected int calculateDesiredSize(T currentMetric) {
        int currentSize = resizable.getCurrentSize()
        int desiredSize
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
