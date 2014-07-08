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

import static brooklyn.util.time.Time.makeTimeStringRounded;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.management.SubscriptionHandle;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

/** attaches to a SoftwareProcess (or anything emitting SERVICE_UP and SERVICE_STATE)
 * and emits HASensors.ENTITY_FAILED and ENTITY_RECOVERED as appropriate
 * @see MemberFailureDetectionPolicy
 */
public class ServiceFailureDetector extends AbstractPolicy {

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

    // TODO delay before reporting failure (give it time to fix itself, e.g. transient failures)
    
    @SetFromFlag("onlyReportIfPreviouslyUp")
    public static final ConfigKey<Boolean> ONLY_REPORT_IF_PREVIOUSLY_UP = ConfigKeys.newBooleanConfigKey("onlyReportIfPreviouslyUp", "", true);
    
    @SetFromFlag("useServiceStateRunning")
    public static final ConfigKey<Boolean> USE_SERVICE_STATE_RUNNING = ConfigKeys.newBooleanConfigKey("useServiceStateRunning", "", true);

    @SetFromFlag("setOnFireOnFailure")
    public static final ConfigKey<Boolean> SET_ON_FIRE_ON_FAILURE = ConfigKeys.newBooleanConfigKey("setOnFireOnFailure", "", true);

    @SetFromFlag("serviceFailedStabilizationDelay")
    public static final ConfigKey<Duration> SERVICE_FAILED_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("serviceRestarter.serviceFailedStabilizationDelay")
            .description("Time period for which the service must be consistently down for (e.g. doesn't report down-up-down) before concluding failure")
            .defaultValue(Duration.ZERO)
            .build();

    @SetFromFlag("serviceRecoveredStabilizationDelay")
    public static final ConfigKey<Duration> SERVICE_RECOVERED_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("serviceRestarter.serviceRecoveredStabilizationDelay")
            .description("For a failed entity, time period for which the service must be consistently up for (e.g. doesn't report up-down-up) before concluding recovered")
            .defaultValue(Duration.ZERO)
            .build();

    protected final AtomicReference<Boolean> serviceIsUp = new AtomicReference<Boolean>();
    protected final AtomicReference<Lifecycle> serviceState = new AtomicReference<Lifecycle>();
    protected final AtomicReference<Long> serviceLastUp = new AtomicReference<Long>();
    protected final AtomicReference<Long> serviceLastDown = new AtomicReference<Long>();
    
    protected Long currentFailureStartTime = null;
    protected Long currentRecoveryStartTime = null;

    protected LastPublished lastPublished = LastPublished.NONE;
    protected boolean weSetItOnFire = false;

    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;

    private List<SubscriptionHandle> subscriptionHandles = Lists.newCopyOnWriteArrayList();

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
        doSubscribe();
        onMemberAdded();
    }

    @Override
    public void suspend() {
        super.suspend();
        doUnsubscribe();
    }
    
    @Override
    public void resume() {
        serviceIsUp.set(null);
        serviceState.set(null);
        serviceLastUp.set(null);
        serviceLastDown.set(null);
        currentFailureStartTime = null;
        currentRecoveryStartTime = null;
        lastPublished = LastPublished.NONE;
        weSetItOnFire = false;
        executorQueued.set(false);
        executorTime = 0;

        super.resume();
        doSubscribe();
        onMemberAdded();
    }

    protected void doSubscribe() {
        if (subscriptionHandles.isEmpty()) {
            if (getConfig(USE_SERVICE_STATE_RUNNING)) {
                SubscriptionHandle handle = subscribe(entity, Attributes.SERVICE_STATE, new SensorEventListener<Lifecycle>() {
                    @Override public void onEvent(SensorEvent<Lifecycle> event) {
                        onServiceState(event.getValue());
                    }
                });
                subscriptionHandles.add(handle);
            }
            
            SubscriptionHandle handle = subscribe(entity, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
                @Override public void onEvent(SensorEvent<Boolean> event) {
                    onServiceUp(event.getValue());
                }
            });
            subscriptionHandles.add(handle);
        }
    }
    
    protected void doUnsubscribe() {
        // TODO Could be more defensive with synchronization, but things shouldn't be calling resume + suspend concurrently
        for (SubscriptionHandle handle : subscriptionHandles) {
            unsubscribe(entity, handle);
        }
        subscriptionHandles.clear();
    }
    
    private Duration getServiceFailedStabilizationDelay() {
        return getConfig(SERVICE_FAILED_STABILIZATION_DELAY);
    }

    private Duration getServiceRecoveredStabilizationDelay() {
        return getConfig(SERVICE_RECOVERED_STABILIZATION_DELAY);
    }

    private synchronized void onServiceUp(Boolean isNowUp) {
        if (isNowUp != null) {
            Boolean old = serviceIsUp.getAndSet(isNowUp);
            if (isNowUp) {
                serviceLastUp.set(System.currentTimeMillis());
            } else {
                serviceLastDown.set(System.currentTimeMillis());
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
        CalculatedStatus status = calculateStatus();
        boolean failed = status.failed;
        boolean healthy = status.healthy;
        long now = System.currentTimeMillis();
        
        if (healthy) {
            if (lastPublished == LastPublished.FAILED) {
                if (currentRecoveryStartTime == null) {
                    LOG.info("{} health-check for {}, component now recovering: {}", new Object[] {this, entity, status.getDescription()});
                    currentRecoveryStartTime = now;
                    schedulePublish();
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, component continuing recovering: {}", new Object[] {this, entity, status.getDescription()});
                }
            } else {
                if (currentFailureStartTime != null) {
                    LOG.info("{} health-check for {}, component now healthy: {}", new Object[] {this, entity, status.getDescription()});
                    currentFailureStartTime = null;
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, component still healthy: {}", new Object[] {this, entity, status.getDescription()});
                }
            }
        } else if (failed) {
            if (lastPublished != LastPublished.FAILED) {
                if (currentFailureStartTime == null) {
                    LOG.info("{} health-check for {}, component now failing: {}", new Object[] {this, entity, status.getDescription()});
                    currentFailureStartTime = now;
                    schedulePublish();
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, component continuing failing: {}", new Object[] {this, entity, status.getDescription()});
                }
            } else {
                if (currentRecoveryStartTime != null) {
                    LOG.info("{} health-check for {}, component now failing: {}", new Object[] {this, entity, status.getDescription()});
                    currentRecoveryStartTime = null;
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, component still failed: {}", new Object[] {this, entity, status.getDescription()});
                }
            }
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} health-check for {}, in unconfirmed sate: {}", new Object[] {this, entity, status.getDescription()});
        }
    }
    
    protected CalculatedStatus calculateStatus() {
        return new CalculatedStatus();
    }

    protected void schedulePublish() {
        schedulePublish(0);
    }
    
    protected void schedulePublish(long delay) {
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            delay = Math.max(0, Math.max(delay, (executorTime + MIN_PERIOD_BETWEEN_EXECS_MILLIS) - now));
            if (LOG.isTraceEnabled()) LOG.trace("{} scheduling publish in {}ms", this, delay);
            
            Runnable job = new Runnable() {
                @Override public void run() {
                    try {
                        executorTime = System.currentTimeMillis();
                        executorQueued.set(false);

                        publishNow();
                        
                    } catch (Exception e) {
                        if (isRunning()) {
                            LOG.error("Error resizing: "+e, e);
                        } else {
                            if (LOG.isDebugEnabled()) LOG.debug("Error resizing, but no longer running: "+e, e);
                        }
                    } catch (Throwable t) {
                        LOG.error("Error in service-failure-detector: "+t, t);
                        throw Exceptions.propagate(t);
                    }
                }
            };
            
            ScheduledTask task = new ScheduledTask(MutableMap.of("delay", Duration.of(delay, TimeUnit.MILLISECONDS)), new BasicTask(job));
            ((EntityInternal)entity).getExecutionContext().submit(task);
        }
    }
    
    private synchronized void publishNow() {
        if (!isRunning()) return;
        
        CalculatedStatus calculatedStatus = calculateStatus();
        
        Long lastUpTime = serviceLastUp.get();
        Long lastDownTime = serviceLastDown.get();
        Boolean isUp = serviceIsUp.get();
        Lifecycle status = serviceState.get();
        boolean failed = calculatedStatus.failed;
        boolean healthy = calculatedStatus.healthy;
        long serviceFailedStabilizationDelay = getServiceFailedStabilizationDelay().toMilliseconds();
        long serviceRecoveredStabilizationDelay = getServiceRecoveredStabilizationDelay().toMilliseconds();
        long now = System.currentTimeMillis();
        
        if (failed) {
            if (lastPublished != LastPublished.FAILED) {
                // only publish if consistently down for serviceFailedStabilizationDelay
                long currentFailurePeriod = getTimeDiff(now, currentFailureStartTime);
                long sinceLastUpPeriod = getTimeDiff(now, lastUpTime);
                if (currentFailurePeriod > serviceFailedStabilizationDelay && sinceLastUpPeriod > serviceFailedStabilizationDelay) {
                    String description = calculatedStatus.getDescription();
                    LOG.warn("{} health-check for {}, publishing component failed: {}", new Object[] {this, entity, description});
                    if (getConfig(USE_SERVICE_STATE_RUNNING) && getConfig(SET_ON_FIRE_ON_FAILURE) && status != Lifecycle.ON_FIRE) {
                        weSetItOnFire = true;
                        entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
                    }
                    entity.emit(HASensors.ENTITY_FAILED, new HASensors.FailureDescriptor(entity, description));
                    lastPublished = LastPublished.FAILED;
                    currentRecoveryStartTime = null;
                } else {
                    long nextAttemptTime = Math.max(serviceFailedStabilizationDelay - currentFailurePeriod, serviceFailedStabilizationDelay - sinceLastUpPeriod);
                    schedulePublish(nextAttemptTime);
                }
            }
        } else if (healthy) {
            if (lastPublished == LastPublished.FAILED) {
                // only publish if consistently up for serviceRecoveredStabilizationDelay
                long currentRecoveryPeriod = getTimeDiff(now, currentRecoveryStartTime);
                long sinceLastDownPeriod = getTimeDiff(now, lastDownTime);
                if (currentRecoveryPeriod > serviceRecoveredStabilizationDelay && sinceLastDownPeriod > serviceRecoveredStabilizationDelay) {
                    String description = calculatedStatus.getDescription();
                    LOG.warn("{} health-check for {}, publishing component recovered: {}", new Object[] {this, entity, description});
                    if (weSetItOnFire) {
                        if (status == Lifecycle.ON_FIRE) {
                            entity.setAttribute(Attributes.SERVICE_STATE, Lifecycle.RUNNING);
                        }
                        weSetItOnFire = false;
                    }
                    entity.emit(HASensors.ENTITY_RECOVERED, new HASensors.FailureDescriptor(entity, description));
                    lastPublished = LastPublished.RECOVERED;
                    currentFailureStartTime = null;
                } else {
                    long nextAttemptTime = Math.max(serviceRecoveredStabilizationDelay - currentRecoveryPeriod, serviceRecoveredStabilizationDelay - sinceLastDownPeriod);
                    schedulePublish(nextAttemptTime);
                }
            }
        }
    }

    public class CalculatedStatus {
        public final boolean failed;
        public final boolean healthy;
        
        public CalculatedStatus() {
            Long lastUpTime = serviceLastUp.get();
            Boolean isUp = serviceIsUp.get();
            Lifecycle status = serviceState.get();

            failed = 
                    (getConfig(USE_SERVICE_STATE_RUNNING) && status == Lifecycle.ON_FIRE && !weSetItOnFire) ||
                    (Boolean.FALSE.equals(isUp) &&
                            (getConfig(USE_SERVICE_STATE_RUNNING) ? status == Lifecycle.RUNNING : true) && 
                            (getConfig(ONLY_REPORT_IF_PREVIOUSLY_UP) ? lastUpTime != null : true));
            healthy = 
                    (getConfig(USE_SERVICE_STATE_RUNNING) ? (status == Lifecycle.RUNNING || (weSetItOnFire && status == Lifecycle.ON_FIRE)) : 
                        true) && 
                    Boolean.TRUE.equals(isUp);
        }
        
        public String getDescription() {
            Long lastUpTime = serviceLastUp.get();
            Boolean isUp = serviceIsUp.get();
            Lifecycle status = serviceState.get();
            Duration serviceFailedStabilizationDelay = getServiceFailedStabilizationDelay();
            Duration serviceRecoveredStabilizationDelay = getServiceRecoveredStabilizationDelay();

            return String.format("location=%s; isUp=%s; status=%s; timeNow=%s; lastReportedUp=%s; lastPublished=%s; "+
                        "currentFailurePeriod=%s; currentRecoveryPeriod=%s",
                    entity.getLocations(), 
                    (isUp != null ? isUp : "<unreported>"),
                    (status != null ? status : "<unreported>"),
                    Time.makeDateString(System.currentTimeMillis()),
                    (lastUpTime != null ? Time.makeDateString(lastUpTime) : "<never>"),
                    lastPublished,
                    (currentFailureStartTime != null ? getTimeStringSince(currentFailureStartTime) : "<none>") + " (stabilization "+makeTimeStringRounded(serviceFailedStabilizationDelay) + ")",
                    (currentRecoveryStartTime != null ? getTimeStringSince(currentRecoveryStartTime) : "<none>") + " (stabilization "+makeTimeStringRounded(serviceRecoveredStabilizationDelay) + ")");
        }
    }
    
    private long getTimeDiff(Long recent, Long previous) {
        return (previous == null) ? recent : (recent - previous);
    }
    
    private String getTimeStringSince(Long time) {
        return time == null ? null : Time.makeTimeStringRounded(System.currentTimeMillis() - time);
    }
    
    private String getTimeStringSince(AtomicReference<Long> timeRef) {
        return getTimeStringSince(timeRef.get());
    }
}
