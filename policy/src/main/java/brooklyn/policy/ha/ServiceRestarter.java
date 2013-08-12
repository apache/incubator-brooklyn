package brooklyn.policy.ha;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.ha.HASensors;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Time;

import com.google.common.base.Preconditions;

/** attaches to a SoftwareProcess (or anything Startable, emitting ENTITY_FAILED or other configurable sensor),
 * and invokes restart on failure; 
 * if there is a subsequent failure within a configurable time interval, or if the restart fails,
 * this gives up and emits {@link #ENTITY_RESTART_FAILED} 
 */
public class ServiceRestarter extends AbstractPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceRestarter.class);

    public static final BasicNotificationSensor<FailureDescriptor> ENTITY_RESTART_FAILED = new BasicNotificationSensor<FailureDescriptor>(
            FailureDescriptor.class, "ha.entityFailed.restart", "Indicates that an entity restart attempt has failed");

    /** skips retry if a failure re-occurs within this time interval */
    @SetFromFlag(defaultVal=""+(3*60*1000))
    private long failOnRecurringFailuresInThisDuration;

    @SetFromFlag(defaultVal="true")
    protected boolean setOnFireOnFailure;

    @SuppressWarnings("rawtypes")
    public static final ConfigKey<Sensor> FAILURE_SENSOR_TO_MONITOR = new BasicConfigKey<Sensor>(Sensor.class, "failureSensorToMonitor"); 
    
    /** monitors this sensor, by default ENTITY_FAILED */
    // FIXME shouldn't set flags set in constructor ?  that might get overwritten by this value
    @SetFromFlag
    private Sensor<?> failureSensorToMonitor = HASensors.ENTITY_FAILED;
    
    public ServiceRestarter() {
        this(new ConfigBag());
    }
    
    public ServiceRestarter(Map<String,?> flags) {
        this(new ConfigBag().putAll(flags));
    }
    
    public ServiceRestarter(ConfigBag configBag) {
        // TODO hierarchy should use ConfigBag, and not change flags
        super(configBag.getAllConfigRaw());
    }
    
    public ServiceRestarter(Sensor<?> failureSensorToMonitor) {
        this(new ConfigBag().configure(FAILURE_SENSOR_TO_MONITOR, failureSensorToMonitor));
    }

    @Override
    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof Startable, "Restarter must take a Startable, not "+entity);
        
        super.setEntity(entity);
        
        subscribe(entity, failureSensorToMonitor, new SensorEventListener<Object>() {
                @Override public void onEvent(SensorEvent<Object> event) {
                    onDetectedFailure(event);
                }
            });
    }
    
    AtomicReference<Long> lastFailureTime = new AtomicReference<Long>();
    
    // TODO semaphores would be better to allow at-most-one-blocking behaviour
    // FIXME as this is called in message-dispatch (single threaded) we should do most of this in a new submitted task
    // (as has been done in ServiceReplacer)
    protected synchronized void onDetectedFailure(SensorEvent<Object> event) {
        LOG.warn("ServiceRestarter acting on failure detected at "+entity+" ("+event.getValue()+")");
        long current = System.currentTimeMillis();
        Long last = lastFailureTime.getAndSet(current);
        long elapsed = last==null ? -1 : current-last;
        if (elapsed>=0 && elapsed <= failOnRecurringFailuresInThisDuration) {
            onRestartFailed("Restart failure (failed again after "+Time.makeTimeStringRounded(elapsed)+") at "+entity+": "+event.getValue());
            return;
        }
        try {
            entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.STARTING);
            Entities.invokeEffector(entity, entity, Startable.RESTART).get();
        } catch (Exception e) {
            onRestartFailed("Restart failure (error "+e+") at "+entity+": "+event.getValue());
        }
    }

    protected void onRestartFailed(String msg) {
        LOG.warn("ServiceRestarter failed. "+msg);
        if (setOnFireOnFailure)
            entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
        entity.emit(ENTITY_RESTART_FAILED, new FailureDescriptor(entity, msg));
    }
    
}
