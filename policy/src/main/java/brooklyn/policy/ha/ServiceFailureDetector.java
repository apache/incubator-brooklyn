package brooklyn.policy.ha;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;

/** attaches to a SoftwareProcess (or anything emitting SERVICE_UP and SERVICE_STATE)
 * and emits HASensors.ENTITY_FAILED and ENTITY_RECOVERED as appropriate
 * @see MemberFailureDetectionPolicy
 */
public class ServiceFailureDetector extends AbstractPolicy {

    // TODO Remove duplication between this and MemberFailureDetectionPolicy.
    // The latter could be re-written to use this. Or could even be deprecated
    // in favour of this.
    
    private static final Logger LOG = LoggerFactory.getLogger(ServiceFailureDetector.class);

    public static final BasicNotificationSensor<FailureDescriptor> ENTITY_FAILED = HASensors.ENTITY_FAILED;

    // TODO delay before reporting failure (give it time to fix itself, e.g. transient failures)
    
    @SetFromFlag("onlyReportIfPreviouslyUp")
    public static final ConfigKey<Boolean> ONLY_REPORT_IF_PREVIOUSLY_UP = ConfigKeys.newBooleanConfigKey("onlyReportIfPreviouslyUp", "", true);
    
    @SetFromFlag("useServiceStateRunning")
    public static final ConfigKey<Boolean> USE_SERVICE_STATE_RUNNING = ConfigKeys.newBooleanConfigKey("useServiceStateRunning", "", true);

    @SetFromFlag("setOnFireOnFailure")
    public static final ConfigKey<Boolean> SET_ON_FIRE_ON_FAILURE = ConfigKeys.newBooleanConfigKey("setOnFireOnFailure", "", true);

    protected final AtomicReference<Boolean> serviceIsUp = new AtomicReference<Boolean>();
    protected final AtomicReference<Long> serviceLastUp = new AtomicReference<Long>();
    protected final AtomicReference<Lifecycle> serviceState = new AtomicReference<Lifecycle>();
    
    protected final AtomicReference<Long> currentFailureStartTime = new AtomicReference<Long>();

    protected boolean weSetItOnFire = false;

    
    public ServiceFailureDetector() {
        this(new ConfigBag());
    }
    
    public ServiceFailureDetector(Map<String,?> flags) {
        this(new ConfigBag().putAll(flags));
    }
    
    public ServiceFailureDetector(ConfigBag configBag) {
        // TODO hierarchy should use ConfigBag, and not change flags
        super(configBag.getAllConfigMutable());
    }

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        
        if (getConfig(USE_SERVICE_STATE_RUNNING)) {
            subscribe(entity, Attributes.SERVICE_STATE, new SensorEventListener<Lifecycle>() {
                @Override public void onEvent(SensorEvent<Lifecycle> event) {
                    onServiceState(event.getValue());
                }
            });
        }
        
        subscribe(entity, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                onServiceUp(event.getValue());
            }
        });
        
        onMemberAdded();
    }
    
    private synchronized void onServiceUp(Boolean isNowUp) {
        if (isNowUp != null) {
            Boolean old = serviceIsUp.getAndSet(isNowUp);
            if (isNowUp) {
                serviceLastUp.set(System.currentTimeMillis());
            }
            if (!Objects.equal(old, serviceIsUp)) {
                checkHealth();
            }
        }
    }
    
    private synchronized void onServiceState(Lifecycle status) {
        if (status != null) {
            Lifecycle old = serviceState.getAndSet(status);
            if (!Objects.equal(old, status)) {
                checkHealth();
            }
        }
    }
    
    private synchronized void onMemberAdded() {
        if (getConfig(USE_SERVICE_STATE_RUNNING)) {
            Lifecycle status = entity.getAttribute(Attributes.SERVICE_STATE);
            onServiceState(status);
        }
        
        Boolean isUp = entity.getAttribute(Startable.SERVICE_UP);
        onServiceUp(isUp);
    }
    
    private synchronized void checkHealth() {
        Long lastUpTime = serviceLastUp.get();
        Boolean isUp = serviceIsUp.get();
        Lifecycle status = serviceState.get();
        boolean failed = 
                (getConfig(USE_SERVICE_STATE_RUNNING) && status == Lifecycle.ON_FIRE && !weSetItOnFire) ||
                (Boolean.FALSE.equals(isUp) &&
                        (getConfig(USE_SERVICE_STATE_RUNNING) ? status == Lifecycle.RUNNING : true) && 
                        (getConfig(ONLY_REPORT_IF_PREVIOUSLY_UP) ? lastUpTime != null : true));
        boolean healthy = 
                (getConfig(USE_SERVICE_STATE_RUNNING) ? (status == Lifecycle.RUNNING || (weSetItOnFire && status == Lifecycle.ON_FIRE)) : 
                    true) && 
                Boolean.TRUE.equals(isUp);

        String description = String.format("location=%s; isUp=%s; status=%s; lastReportedUp=%s; timeNow=%s", 
                entity.getLocations(), 
                (isUp != null ? isUp : "<unreported>"),
                (status != null ? status : "<unreported>"),
                (lastUpTime != null ? Time.makeDateString(lastUpTime) : "<never>"),
                Time.makeDateString(System.currentTimeMillis()));

        if (currentFailureStartTime.get()!=null) {
            if (healthy) {
                LOG.info("{} health-check for {}, component recovered (from failure at {}): {}", 
                        new Object[] {this, entity, Time.makeTimeStringRounded(System.currentTimeMillis() - currentFailureStartTime.get()), description});
                if (weSetItOnFire) {
                    if (status == Lifecycle.ON_FIRE)
                        entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.RUNNING);
                    weSetItOnFire = false;
                }
                entity.emit(HASensors.ENTITY_RECOVERED, new HASensors.FailureDescriptor(entity, description));
                currentFailureStartTime.set(null);
            } else if (failed) {
                if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, confirmed still failed: {}", new Object[] {this, entity, description});
            } else {
                if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, in unconfirmed sate (previously failed): {}", new Object[] {this, entity, description});
            }
        } else if (failed) {
            LOG.info("{} health-check for {}, component failed: {}", new Object[] {this, entity, description});
            currentFailureStartTime.set(System.currentTimeMillis());
            if (getConfig(USE_SERVICE_STATE_RUNNING) && getConfig(SET_ON_FIRE_ON_FAILURE) && status != Lifecycle.ON_FIRE) {
                weSetItOnFire = true;
                entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
            }
            entity.emit(HASensors.ENTITY_FAILED, new HASensors.FailureDescriptor(entity, description));
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, either healthy or insufficient data: {}", new Object[] {this, entity, description});
        }
    }
}
