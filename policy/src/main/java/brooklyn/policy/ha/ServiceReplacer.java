package brooklyn.policy.ha;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Preconditions;

/** attaches to a DynamicCluster and replaces a failed member in response to HASensors.ENTITY_FAILED or other sensor;
 * if this fails, it sets the Cluster state to on-fire */
public class ServiceReplacer extends AbstractPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceReplacer.class);

    // TODO if there are multiple failures perhaps we should abort quickly
    
    @SuppressWarnings("rawtypes")
    public static final ConfigKey<Sensor> FAILURE_SENSOR_TO_MONITOR = new BasicConfigKey<Sensor>(Sensor.class, "failureSensorToMonitor"); 
    
    /** monitors this sensor, by default ENTITY_RESTART_FAILED */
    // FIXME shouldn't set flags set in constructor ?  that might get overwritten by this value
    @SetFromFlag
    private Sensor<?> failureSensorToMonitor = ServiceRestarter.ENTITY_RESTART_FAILED;
    
    public ServiceReplacer() {
        this(new ConfigBag());
    }
    
    public ServiceReplacer(Map<String,?> flags) {
        this(new ConfigBag().putAll(flags));
    }
    
    public ServiceReplacer(ConfigBag configBag) {
        // TODO hierarchy should use ConfigBag, and not change flags
        super(configBag.getAllConfigRaw());
    }
    
    public ServiceReplacer(Sensor<?> failureSensorToMonitor) {
        this(new ConfigBag().configure(FAILURE_SENSOR_TO_MONITOR, failureSensorToMonitor));
    }

    @Override
    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof DynamicCluster, "Replacer must take a DynamicCluster, not "+entity);
        
        super.setEntity(entity);

        subscribeToMembers((Group)entity, failureSensorToMonitor, new SensorEventListener<Object>() {
                @Override public void onEvent(SensorEvent<Object> event) {
                    onDetectedFailure(event);
                }
            });
    }
    
    // TODO semaphores would be better to allow at-most-one-blocking behaviour
    protected synchronized void onDetectedFailure(final SensorEvent<Object> event) {
        LOG.warn("ServiceReplacer acting on failure detected at "+event.getSource()+" ("+event.getValue()+", child of "+entity+")");
        ((EntityInternal)entity).getManagementSupport().getExecutionContext().submit(MutableMap.of(), new Runnable() {

            @Override
            public void run() {
                try {
                    Entities.invokeEffectorWithArgs(entity, entity, DynamicCluster.REPLACE_MEMBER, event.getSource().getId()).get();
                } catch (Exception e) {
                    // FIXME replaceMember fails if stop fails on the old node; should resolve that more gracefully than this
                    if (e.toString().contains("stopping") && e.toString().contains(event.getSource().getId())) {
                        LOG.info("ServiceReplacer: ignoring error reported from stopping failed node "+event.getSource());
                        return;
                    }

                    onReplacementFailed("Replace failure (error "+e+") at "+entity+": "+event.getValue());
                }

            }

        });
    }

    protected void onReplacementFailed(String msg) {
        LOG.warn("ServiceReplacer failed. "+msg);
        entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
    }
    
}
