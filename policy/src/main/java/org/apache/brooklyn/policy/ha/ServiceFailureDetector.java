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
package org.apache.brooklyn.policy.ha;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.event.SensorEvent;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.util.config.ConfigBag;
import org.apache.brooklyn.core.util.flags.SetFromFlag;
import org.apache.brooklyn.core.util.task.BasicTask;
import org.apache.brooklyn.core.util.task.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.ServiceStateLogic.ComputeServiceState;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;

import org.apache.brooklyn.policy.ha.HASensors.FailureDescriptor;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

/** 
 * Emits {@link HASensors#ENTITY_FAILED} whenever the parent's default logic ({@link ComputeServiceState}) would detect a problem,
 * and similarly {@link HASensors#ENTITY_RECOVERED} when recovered.
 * <p>
 * gives more control over suppressing {@link Lifecycle#ON_FIRE}, 
 * for some period of time
 * (or until another process manually sets {@link Attributes#SERVICE_STATE_ACTUAL} to {@value Lifecycle#ON_FIRE},
 * which this enricher will not clear until all problems have gone away)
 */
//@Catalog(name="Service Failure Detector", description="HA policy for deteting failure of a service")
public class ServiceFailureDetector extends ServiceStateLogic.ComputeServiceState {

    // TODO Remove duplication between this and MemberFailureDetectionPolicy.
    // The latter could be re-written to use this. Or could even be deprecated
    // in favour of this.

    public enum LastPublished {
        NONE,
        FAILED,
        RECOVERED;
    }

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

    @SetFromFlag("entityFailedRepublishTime")
    public static final ConfigKey<Duration> ENTITY_FAILED_REPUBLISH_TIME = BasicConfigKey.builder(Duration.class)
            .name("entityFailed.republishTime")
            .description("Publish failed state periodically at the specified intervals, null to disable.")
            .build();

    protected Long firstUpTime;
    
    protected Long currentFailureStartTime = null;
    protected Long currentRecoveryStartTime = null;
    
    protected Long publishEntityFailedTime = null;
    protected Long publishEntityRecoveredTime = null;
    protected Long setEntityOnFireTime = null;
    
    protected LastPublished lastPublished = LastPublished.NONE;

    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;

    /**
     * TODO Really don't want this mutex!
     * ServiceStateLogic.setExpectedState() will call into `onEvent(null)`, so could get concurrent calls.
     * How to handle that? I don't think `ServiceStateLogic.setExpectedState` should be making the call, but
     * presumably that is their to remove a race condition so it is set before method returns. Caller shouldn't
     * rely on that though.
     * e.g. see `ServiceFailureDetectorTest.testNotifiedOfFailureOnStateOnFire`, where we get two notifications.
     */
    private final Object mutex = new Object();
    
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
        if (firstUpTime==null) {
            if (event!=null && Attributes.SERVICE_UP.equals(event.getSensor()) && Boolean.TRUE.equals(event.getValue())) {
                firstUpTime = event.getTimestamp();
            } else if (event == null && Boolean.TRUE.equals(entity.getAttribute(Attributes.SERVICE_UP))) {
                // If this enricher is registered after the entity is up, then we'll get a "synthetic" onEvent(null) 
                firstUpTime = System.currentTimeMillis();
            }
        }
        
        super.onEvent(event);
    }
    
    @Override
    protected void setActualState(Lifecycle state) {
        long now = System.currentTimeMillis();

        synchronized (mutex) {
            if (state==Lifecycle.ON_FIRE) {
                if (lastPublished == LastPublished.FAILED) {
                    if (currentRecoveryStartTime != null) {
                        if (LOG.isDebugEnabled()) LOG.debug("{} health-check for {}, component was recovering, now failing: {}", new Object[] {this, entity, getExplanation(state)});
                        currentRecoveryStartTime = null;
                        publishEntityRecoveredTime = null;
                    } else {
                        if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, component still failed: {}", new Object[] {this, entity, getExplanation(state)});
                    }
                } else {
                    if (firstUpTime == null && getConfig(ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP)) {
                        // suppress; won't publish
                    } else if (currentFailureStartTime == null) {
                        if (LOG.isDebugEnabled()) LOG.debug("{} health-check for {}, component now failing: {}", new Object[] {this, entity, getExplanation(state)});
                        currentFailureStartTime = now;
                        publishEntityFailedTime = currentFailureStartTime + getConfig(ENTITY_FAILED_STABILIZATION_DELAY).toMilliseconds();
                    } else {
                        if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, component continuing failing: {}", new Object[] {this, entity, getExplanation(state)});
                    }
                }
                if (setEntityOnFireTime == null) {
                    setEntityOnFireTime = now + getConfig(SERVICE_ON_FIRE_STABILIZATION_DELAY).toMilliseconds();
                }
                currentRecoveryStartTime = null;
                publishEntityRecoveredTime = null;
                
            } else if (state == Lifecycle.RUNNING) {
                if (lastPublished == LastPublished.FAILED) {
                    if (currentRecoveryStartTime == null) {
                        if (LOG.isDebugEnabled()) LOG.debug("{} health-check for {}, component now recovering: {}", new Object[] {this, entity, getExplanation(state)});
                        currentRecoveryStartTime = now;
                        publishEntityRecoveredTime = currentRecoveryStartTime + getConfig(ENTITY_RECOVERED_STABILIZATION_DELAY).toMilliseconds();
                    } else {
                        if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, component continuing recovering: {}", new Object[] {this, entity, getExplanation(state)});
                    }
                } else {
                    if (currentFailureStartTime != null) {
                        if (LOG.isDebugEnabled()) LOG.debug("{} health-check for {}, component was failing, now healthy: {}", new Object[] {this, entity, getExplanation(state)});
                    } else {
                        if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, component still healthy: {}", new Object[] {this, entity, getExplanation(state)});
                    }
                }
                currentFailureStartTime = null;
                publishEntityFailedTime = null;
                setEntityOnFireTime = null;
                
            } else {
                if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, in unconfirmed sate: {}", new Object[] {this, entity, getExplanation(state)});
            }

            long recomputeIn = Long.MAX_VALUE; // For whether to call recomputeAfterDelay
            
            if (publishEntityFailedTime != null) {
                long delayBeforeCheck = publishEntityFailedTime - now;
                if (delayBeforeCheck<=0) {
                    if (LOG.isDebugEnabled()) LOG.debug("{} publishing failed (state={}; currentFailureStartTime={}; now={}", 
                            new Object[] {this, state, Time.makeDateString(currentFailureStartTime), Time.makeDateString(now)});
                    Duration republishDelay = getConfig(ENTITY_FAILED_REPUBLISH_TIME);
                    if (republishDelay == null) {
                        publishEntityFailedTime = null;
                    } else {
                        publishEntityFailedTime = now + republishDelay.toMilliseconds();
                        recomputeIn = Math.min(recomputeIn, republishDelay.toMilliseconds());
                    }
                    lastPublished = LastPublished.FAILED;
                    entity.emit(HASensors.ENTITY_FAILED, new HASensors.FailureDescriptor(entity, getFailureDescription(now)));
                } else {
                    recomputeIn = Math.min(recomputeIn, delayBeforeCheck);
                }
            } else if (publishEntityRecoveredTime != null) {
                long delayBeforeCheck = publishEntityRecoveredTime - now;
                if (delayBeforeCheck<=0) {
                    if (LOG.isDebugEnabled()) LOG.debug("{} publishing recovered (state={}; currentRecoveryStartTime={}; now={}", 
                            new Object[] {this, state, Time.makeDateString(currentRecoveryStartTime), Time.makeDateString(now)});
                    publishEntityRecoveredTime = null;
                    lastPublished = LastPublished.RECOVERED;
                    entity.emit(HASensors.ENTITY_RECOVERED, new HASensors.FailureDescriptor(entity, null));
                } else {
                    recomputeIn = Math.min(recomputeIn, delayBeforeCheck);
                }
            }
            
            if (setEntityOnFireTime != null) {
                long delayBeforeCheck = setEntityOnFireTime - now;
                if (delayBeforeCheck<=0) {
                    if (LOG.isDebugEnabled()) LOG.debug("{} setting on-fire, now that deferred period has passed (state={})", 
                            new Object[] {this, state});
                    setEntityOnFireTime = null;
                    super.setActualState(state);
                } else {
                    recomputeIn = Math.min(recomputeIn, delayBeforeCheck);
                }
            } else {
                super.setActualState(state);
            }
            
            if (recomputeIn < Long.MAX_VALUE) {
                recomputeAfterDelay(recomputeIn);
            }
        }
    }

    protected String getExplanation(Lifecycle state) {
        Duration serviceFailedStabilizationDelay = getConfig(ENTITY_FAILED_STABILIZATION_DELAY);
        Duration serviceRecoveredStabilizationDelay = getConfig(ENTITY_RECOVERED_STABILIZATION_DELAY);

        return String.format("location=%s; status=%s; lastPublished=%s; timeNow=%s; "+
                    "currentFailurePeriod=%s; currentRecoveryPeriod=%s",
                entity.getLocations(), 
                (state != null ? state : "<unreported>"),
                lastPublished,
                Time.makeDateString(System.currentTimeMillis()),
                (currentFailureStartTime != null ? getTimeStringSince(currentFailureStartTime) : "<none>") + " (stabilization "+Time.makeTimeStringRounded(serviceFailedStabilizationDelay) + ")",
                (currentRecoveryStartTime != null ? getTimeStringSince(currentRecoveryStartTime) : "<none>") + " (stabilization "+Time.makeTimeStringRounded(serviceRecoveredStabilizationDelay) + ")");
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
    
    private String getTimeStringSince(Long time) {
        return time == null ? null : Time.makeTimeStringRounded(System.currentTimeMillis() - time);
    }
}
