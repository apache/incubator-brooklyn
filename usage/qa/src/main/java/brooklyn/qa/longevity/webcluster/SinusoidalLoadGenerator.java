package brooklyn.qa.longevity.webcluster;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.log.Log;

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Throwables;

/**
 * Periodically publishes values in the range of 0 to #amplitude. 
 * The value varies sinusoidally over time.
 */
public class SinusoidalLoadGenerator extends AbstractEnricher {

    private final long publishPeriodMs;
    private final long sinPeriodMs;
    private final double amplitude;

    private final AttributeSensor<Double> target;
    private final ScheduledExecutorService executor;
    
    public SinusoidalLoadGenerator(AttributeSensor<Double> target, long publishPeriodMs, long sinPeriodMs, double sinAmplitude) {
        this.target = target;
        this.publishPeriodMs = publishPeriodMs;
        this.sinPeriodMs = sinPeriodMs;
        this.amplitude = sinAmplitude;
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }
    
    public void setEntity(final EntityLocal entity) {
        super.setEntity(entity);
        
        executor.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                try {
                    long time = System.currentTimeMillis();
                    double val = amplitude * (1 + Math.sin( (1.0*time) / sinPeriodMs * Math.PI * 2  - Math.PI/2 )) / 2;
                    entity.setAttribute(target, val);
                } catch (Throwable t) {
                    Log.warn("Error generating sinusoidal-load metric", t);
                    throw Throwables.propagate(t);
                }
            }
        }, 0, publishPeriodMs, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
