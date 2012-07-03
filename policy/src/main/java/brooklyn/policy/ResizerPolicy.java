package brooklyn.policy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.MutableMap;
import brooklyn.util.task.BasicTask;

public class ResizerPolicy<T extends Number> extends AbstractPolicy implements SensorEventListener<T> {
    
    // TODO Currently only does one resize at a time.
    // Imagine the threshold is set to 100. If we ramp up workrate to 450, but the policy sees events for 101 then 450, 
    // the first event will cause it to provision a single new instance. After the several minutes that this takes, it 
    // will then concurrently provision up to the correct number of instances (in this case, 5).
    
    private static final Logger LOG = LoggerFactory.getLogger(ResizerPolicy.class);

    private AttributeSensor<T> source;
    private Resizable resizable;
    private boolean entityStartable = false;
    private String[] metricName;
    private T metricLowerBound;
    private T metricUpperBound;
    private int minSize;
    private int maxSize = Integer.MAX_VALUE;

    private final AtomicInteger desiredSize = new AtomicInteger(0);

    /** Lock held if we are in the process of resizing. */
    private final AtomicBoolean resizing = new AtomicBoolean(false);

    private Runnable resizeAction = new Runnable() {
        public void run() {
            try {
                LOG.info("policy resizer performing resizing...");
                int desire = desiredSize.get();
                resizable.resize(desire);
                while (desire != desiredSize.get()) {
                    LOG.info("policy resizer performing re-resizing...");
                    desire = desiredSize.get();
                    resizable.resize(desire);
                }
                LOG.info("policy resizer resizing complete");
            } finally {
                resizing.set(false);
            }
        }
    };

    /**
     * @param averagingSource - A sensor that averages a relevant metric across the attaching entity
     */
    public ResizerPolicy(AttributeSensor<T> averagingSource) {
        this(MutableMap.of(), averagingSource);
    }
    public ResizerPolicy(Map properties, AttributeSensor<T> averagingSource) {
        super(properties);
        this.source = averagingSource;
    }

    @Override
    public void setEntity(EntityLocal entity) {
        assert entity instanceof Resizable : "entity="+entity+"; class="+(entity != null ? entity.getClass() : null);
        super.setEntity(entity);
        resizable = (Resizable) entity;
        if(entity instanceof Startable) {
            entityStartable = true;
        }
        subscribe(entity, source, this);
    }

    public ResizerPolicy setMetricRange(T min, T max) {
        setMetricLowerBound(min);
        setMetricUpperBound(max);
        return this;
    }

    public ResizerPolicy setMetricLowerBound(T val) {
        this.metricLowerBound = val;
        return this;
    }

    public ResizerPolicy setMetricUpperBound(T val) {
        this.metricUpperBound = val;
        return this;
    }

    public ResizerPolicy setSizeRange(int min, int max) {
        setMinSize(min);
        setMaxSize(max);
        return this;
    }
    
    public ResizerPolicy setMinSize(int val) {
        this.minSize = val;
        return this;
    }

    public ResizerPolicy setMaxSize(int val) {
        this.maxSize = val;
        return this;
    }
    

    private void resize() {
        if (isRunning() // if I'm running
            && (!entityStartable || entity.getAttribute(Startable.SERVICE_UP)) // my entity is up
            && resizing.compareAndSet(false, true)) { // and I'm not in the middle of resizing already
            ((EntityLocal)entity).getManagementContext().getExecutionContext(entity).submit(new BasicTask(resizeAction));
        }
    }

    public void onEvent(SensorEvent<T> event) {
        if (isDestroyed()) return; //swallow events when destroyed
        
        T val = event.getValue();
        int currentSize = resizable.getCurrentSize();
        desiredSize.set(calculateDesiredSize(val));

        if (desiredSize.get() != currentSize) {
            if (LOG.isDebugEnabled()) LOG.debug("policy resizer resizing: metric={}, workrate={}, lowerBound={}, upperBound={}; currentSize={}, desiredSize={}, minSize={}, maxSize={}",
                    new Object[] {source, val, metricLowerBound, metricUpperBound, currentSize, desiredSize.get(), minSize, maxSize});
            resize();
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("policy resizer doing nothing: metric={}, workrate={}, lowerBound={}, upperBound={}; currentSize={}, minSize={}, maxSize={}",
                    new Object[] {source, val, metricLowerBound, metricUpperBound, currentSize, minSize, maxSize});
        }
    }

    // TODO Could have throttling etc so don't repeatedly call grow; standard control theory stuff such as
    //      PID design (proportional-integral-derivative)
    // TODO Could show example of overriding this to do something smarter
    protected int calculateDesiredSize(T currentMetric) {
        double currentMetricD = currentMetric.doubleValue();
        double metricUpperBoundD = metricUpperBound.doubleValue();
        double metricLowerBoundD = metricLowerBound.doubleValue();
        int currentSize = resizable.getCurrentSize();
        
        int desiredSize;
        if (0 < (currentMetricD - metricUpperBoundD)) {
            // scale out
            desiredSize = currentSize + (int)Math.ceil(currentSize * ((currentMetricD - metricUpperBoundD) / metricUpperBoundD));
        } else if (0 < (metricLowerBoundD - currentMetricD)) {
            // scale back
            desiredSize = currentSize - (int)Math.ceil(currentSize * (Math.abs(currentMetricD - metricLowerBoundD) / metricLowerBoundD));
        } else {
            desiredSize = currentSize;
        }
        desiredSize = Math.max(minSize, desiredSize);
        desiredSize = Math.min(maxSize, desiredSize);
        return desiredSize;
    }
}
