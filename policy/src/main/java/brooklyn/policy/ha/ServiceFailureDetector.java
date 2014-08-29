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
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.ServiceStateLogic.ComputeServiceState;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.time.Duration;

/** 
 * emits {@link HASensors#ENTITY_FAILED} whenever the parent's default logic ({@link ComputeServiceState}) would detect a problem,
 * and similarly {@link HASensors#ENTITY_RECOVERED} when recovered.
 * <p>
 * gives more control over suppressing {@link Lifecycle#ON_FIRE}, 
 * for some period of time
 * (or until another process manually sets {@link Attributes#SERVICE_STATE_ACTUAL} to {@value Lifecycle#ON_FIRE},
 * which this enricher will not clear until all problems have gone away)
 */
public class ServiceFailureDetector extends ServiceStateLogic.ComputeServiceState {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceFailureDetector.class);

    private static final long MIN_PERIOD_BETWEEN_EXECS_MILLIS = 100;

    public static final BasicNotificationSensor<FailureDescriptor> ENTITY_FAILED = HASensors.ENTITY_FAILED;

    @SetFromFlag("onlyReportIfPreviouslyUp")
    public static final ConfigKey<Boolean> ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP = ConfigKeys.newBooleanConfigKey("onlyReportIfPreviouslyUp", 
        "Prevents the policy from emitting ENTITY_FAILED if the entity fails on startup (ie has never been up)", true);
    
    public static final ConfigKey<Boolean> MONITOR_SERVICE_PROBLEMS = ConfigKeys.newBooleanConfigKey("monitorServiceProblems", 
        "Whether to monitor service problems, and emit on failures there (if set to false, this monitors only service up)", true);

    @SetFromFlag("serviceOnFireStabilizationDelay")
    public static final ConfigKey<Duration> SERVICE_ON_FIRE_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("serviceOnFire.stabilizationDelay")
            .description("Time period for which the service must be consistently down for (e.g. doesn't report down-up-down) before concluding ON_FIRE")
            .defaultValue(Duration.ZERO)
            .build();

    @SetFromFlag("entityFailedStabilizationDelay")
    public static final ConfigKey<Duration> ENTITY_FAILED_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("entityFailed.stabilizationDelay")
            .description("Time period for which the service must be consistently down for (e.g. doesn't report down-up-down) before emitting ENTITY_FAILED")
            .defaultValue(Duration.ZERO)
            .build();

    @SetFromFlag("entityRecoveredStabilizationDelay")
    public static final ConfigKey<Duration> ENTITY_RECOVERED_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("entityRecovered.stabilizationDelay")
            .description("For a failed entity, time period for which the service must be consistently up for (e.g. doesn't report up-down-up) before emitting ENTITY_RECOVERED")
            .defaultValue(Duration.ZERO)
            .build();

    protected Long firstUpTime;
    
    protected Long currentFailureStartTime = null;
    protected Long currentRecoveryStartTime = null;
    
    protected Long publishEntityFailedTime = null;
    protected Long publishEntityRecoveredTime = null;

    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;

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
    public void onEvent(SensorEvent<Object> event) {
        if (firstUpTime==null && event!=null && Attributes.SERVICE_UP.equals(event.getSensor()) && Boolean.TRUE.equals(event.getValue())) {
            firstUpTime = event.getTimestamp();
        }
        
        super.onEvent(event);
    }
    
    @Override
    protected void setActualState(Lifecycle state) {
        if (state==Lifecycle.ON_FIRE) {
            if (currentFailureStartTime==null) {
                currentFailureStartTime = System.currentTimeMillis();
                publishEntityFailedTime = currentFailureStartTime + getConfig(ENTITY_FAILED_STABILIZATION_DELAY).toMilliseconds();
            }
            // cancel any existing recovery
            currentRecoveryStartTime = null;
            publishEntityRecoveredTime = null;
            
            long now = System.currentTimeMillis();
            
            long delayBeforeCheck = currentFailureStartTime+getConfig(SERVICE_ON_FIRE_STABILIZATION_DELAY).toMilliseconds() - now;
            if (delayBeforeCheck<=0) {
                super.setActualState(state);
            } else {
                recomputeAfterDelay(delayBeforeCheck);
            }
            
            if (publishEntityFailedTime!=null) {
                delayBeforeCheck = publishEntityFailedTime - now;
                if (firstUpTime==null && getConfig(ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP)) {
                    // suppress
                    publishEntityFailedTime = null;
                } else if (delayBeforeCheck<=0) {
                    publishEntityFailedTime = null;
                    entity.emit(HASensors.ENTITY_FAILED, new HASensors.FailureDescriptor(entity, getFailureDescription(now)));
                } else {
                    recomputeAfterDelay(delayBeforeCheck);
                }
            }
            
        } else {
            if (state == Lifecycle.RUNNING) {
                if (currentFailureStartTime!=null) {
                    currentFailureStartTime = null;
                    publishEntityFailedTime = null;

                    currentRecoveryStartTime = System.currentTimeMillis();
                    publishEntityRecoveredTime = currentRecoveryStartTime + getConfig(ENTITY_RECOVERED_STABILIZATION_DELAY).toMilliseconds();
                }
            }

            super.setActualState(state);
            
            if (publishEntityRecoveredTime!=null) {
                long now = System.currentTimeMillis();
                long delayBeforeCheck = publishEntityRecoveredTime - now;
                if (delayBeforeCheck<=0) {
                    entity.emit(HASensors.ENTITY_RECOVERED, new HASensors.FailureDescriptor(entity, null));
                    publishEntityRecoveredTime = null;
                } else {
                    recomputeAfterDelay(delayBeforeCheck);
                }
            }
        }
    }

    private String getFailureDescription(long now) {
        String description = null;
        Map<String, Object> serviceProblems = entity.getAttribute(Attributes.SERVICE_PROBLEMS);
        if (serviceProblems!=null && !serviceProblems.isEmpty()) {
            Entry<String, Object> problem = serviceProblems.entrySet().iterator().next();
            description = problem.getKey()+": "+problem.getValue();
            if (serviceProblems.size()>1) {
                description = serviceProblems.size()+" service problems, including "+description;
            } else {
                description = "service problem: "+description;
            }
        } else if (Boolean.FALSE.equals(entity.getAttribute(Attributes.SERVICE_UP))) {
            description = "service not up";
        } else {
            description = "service failure detected";
        }
        if (publishEntityFailedTime!=null && currentFailureStartTime!=null && publishEntityFailedTime > currentFailureStartTime)
            description = " (stabilized for "+Duration.of(now - currentFailureStartTime, TimeUnit.MILLISECONDS)+")";
        return description;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void recomputeAfterDelay(long delay) {
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            delay = Math.max(0, Math.max(delay, (executorTime + MIN_PERIOD_BETWEEN_EXECS_MILLIS) - now));
            if (LOG.isTraceEnabled()) LOG.trace("{} scheduling publish in {}ms", this, delay);
            
            Runnable job = new Runnable() {
                @Override public void run() {
                    try {
                        executorTime = System.currentTimeMillis();
                        executorQueued.set(false);

                        onEvent(null);
                        
                    } catch (Exception e) {
                        if (isRunning()) {
                            LOG.error("Error in enricher "+this+": "+e, e);
                        } else {
                            if (LOG.isDebugEnabled()) LOG.debug("Error in enricher "+this+" (but no longer running): "+e, e);
                        }
                    } catch (Throwable t) {
                        LOG.error("Error in enricher "+this+": "+t, t);
                        throw Exceptions.propagate(t);
                    }
                }
            };
            
            ScheduledTask task = new ScheduledTask(MutableMap.of("delay", Duration.of(delay, TimeUnit.MILLISECONDS)), new BasicTask(job));
            ((EntityInternal)entity).getExecutionContext().submit(task);
        }
    }
    
}
