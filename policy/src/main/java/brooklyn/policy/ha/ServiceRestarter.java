/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.policy.ha;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;
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
    @SetFromFlag("failOnRecurringFailuresInThisDuration")
    public static final ConfigKey<Duration> FAIL_ON_RECURRING_FAILURES_IN_THIS_DURATION = ConfigKeys.newConfigKey(
            Duration.class, 
            "failOnRecurringFailuresInThisDuration", 
            "Reports entity as failed if it fails two or more times in this time window", 
            Duration.minutes(3));

    @SetFromFlag("setOnFireOnFailure")
    public static final ConfigKey<Boolean> SET_ON_FIRE_ON_FAILURE = ConfigKeys.newBooleanConfigKey("setOnFireOnFailure", "", true);

    /** monitors this sensor, by default ENTITY_FAILED */
    @SetFromFlag("failureSensorToMonitor")
    @SuppressWarnings("rawtypes")
    public static final ConfigKey<Sensor<?>> FAILURE_SENSOR_TO_MONITOR = (ConfigKey) ConfigKeys.newConfigKey(Sensor.class, "failureSensorToMonitor", "", HASensors.ENTITY_FAILED); 
    
    protected final AtomicReference<Long> lastFailureTime = new AtomicReference<Long>();

    public ServiceRestarter() {
        this(new ConfigBag());
    }
    
    public ServiceRestarter(Map<String,?> flags) {
        this(new ConfigBag().putAll(flags));
    }
    
    public ServiceRestarter(ConfigBag configBag) {
        // TODO hierarchy should use ConfigBag, and not change flags
        super(configBag.getAllConfigMutable());
    }
    
    public ServiceRestarter(Sensor<?> failureSensorToMonitor) {
        this(new ConfigBag().configure(FAILURE_SENSOR_TO_MONITOR, failureSensorToMonitor));
    }

    @Override
    public void setEntity(final EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof Startable, "Restarter must take a Startable, not "+entity);
        
        super.setEntity(entity);
        
        subscribe(entity, getConfig(FAILURE_SENSOR_TO_MONITOR), new SensorEventListener<Object>() {
                @Override public void onEvent(final SensorEvent<Object> event) {
                    // Must execute in another thread - if we called entity.restart in the event-listener's thread
                    // then we'd block all other events being delivered to this entity's other subscribers.
                    // Relies on synchronization of `onDetectedFailure`.
                    // See same pattern used in ServiceReplacer.

                    // TODO Could use BasicExecutionManager.setTaskSchedulerForTag to prevent race of two
                    // events being received in rapid succession, and onDetectedFailure being executed out-of-order
                    // for them; or could write events to a blocking queue and have onDetectedFailure read from that.
                    
                    if (isRunning()) {
                        LOG.info("ServiceRestarter notified; dispatching job for "+entity+" ("+event.getValue()+")");
                        ((EntityInternal)entity).getExecutionContext().submit(MutableMap.of(), new Runnable() {
                            @Override public void run() {
                                onDetectedFailure(event);
                            }});
                    } else {
                        LOG.warn("ServiceRestarter not running, so not acting on failure detected at "+entity+" ("+event.getValue()+")");
                    }
                }
            });
    }
    
    // TODO semaphores would be better to allow at-most-one-blocking behaviour
    // FIXME as this is called in message-dispatch (single threaded) we should do most of this in a new submitted task
    // (as has been done in ServiceReplacer)
    protected synchronized void onDetectedFailure(SensorEvent<Object> event) {
        if (isSuspended()) {
            LOG.warn("ServiceRestarter suspended, so not acting on failure detected at "+entity+" ("+event.getValue()+")");
            return;
        }

        LOG.warn("ServiceRestarter acting on failure detected at "+entity+" ("+event.getValue()+")");
        long current = System.currentTimeMillis();
        Long last = lastFailureTime.getAndSet(current);
        long elapsed = last==null ? -1 : current-last;
        if (elapsed>=0 && elapsed <= getConfig(FAIL_ON_RECURRING_FAILURES_IN_THIS_DURATION).toMilliseconds()) {
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
        LOG.warn("ServiceRestarter failed for "+entity+": "+msg);
        if (getConfig(SET_ON_FIRE_ON_FAILURE)) {
            entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
        }
        entity.emit(ENTITY_RESTART_FAILED, new FailureDescriptor(entity, msg));
    }
}
