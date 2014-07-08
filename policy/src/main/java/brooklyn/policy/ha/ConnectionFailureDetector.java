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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.management.Task;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Networking;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.net.HostAndPort;

/**
 * Monitors a given {@link HostAndPort}, to emit HASensors.CONNECTION_FAILED and HASensors.CONNECTION_RECOVERED 
 * if the connection is lost/restored.
 */
public class ConnectionFailureDetector extends AbstractPolicy {

    // TODO Remove duplication from ServiceFailureDetector, particularly for the stabilisation delays.

    public enum LastPublished {
        NONE,
        FAILED,
        RECOVERED;
    }

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionFailureDetector.class);

    private static final long MIN_PERIOD_BETWEEN_EXECS_MILLIS = 100;

    public static final ConfigKey<HostAndPort> ENDPOINT = ConfigKeys.newConfigKey(HostAndPort.class, "connectionFailureDetector.endpoint");
    
    public static final ConfigKey<Duration> POLL_PERIOD = ConfigKeys.newConfigKey(Duration.class, "connectionFailureDetector.pollPeriod", "", Duration.ONE_SECOND);
    
    public static final BasicNotificationSensor<FailureDescriptor> CONNECTION_FAILED = HASensors.CONNECTION_FAILED;

    public static final BasicNotificationSensor<FailureDescriptor> CONNECTION_RECOVERED = HASensors.CONNECTION_RECOVERED;

    @SetFromFlag("connectionFailedStabilizationDelay")
    public static final ConfigKey<Duration> CONNECTION_FAILED_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("connectionFailureDetector.serviceFailedStabilizationDelay")
            .description("Time period for which the connection must be consistently down for "
                    + "(e.g. doesn't report down-up-down) before concluding failure. "
                    + "Note that long TCP timeouts mean there can be long (e.g. 70 second) "
                    + "delays in noticing a connection refused condition.")
            .defaultValue(Duration.ZERO)
            .build();

    @SetFromFlag("connectionRecoveredStabilizationDelay")
    public static final ConfigKey<Duration> CONNECTION_RECOVERED_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("connectionFailureDetector.serviceRecoveredStabilizationDelay")
            .description("For a failed connection, time period for which the connection must be consistently up for (e.g. doesn't report up-down-up) before concluding recovered")
            .defaultValue(Duration.ZERO)
            .build();

    protected final AtomicReference<Long> connectionLastUp = new AtomicReference<Long>();
    protected final AtomicReference<Long> connectionLastDown = new AtomicReference<Long>();
    
    protected Long currentFailureStartTime = null;
    protected Long currentRecoveryStartTime = null;

    protected LastPublished lastPublished = LastPublished.NONE;

    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;

    private Callable<Task<?>> pollingTaskFactory;

    private Task<?> scheduledTask;
    
    public ConnectionFailureDetector() {
    }
    
    @Override
    public void init() {
        getRequiredConfig(ENDPOINT); // just to confirm it's set, failing fast

        pollingTaskFactory = new Callable<Task<?>>() {
            @Override public Task<?> call() {
                BasicTask<Void> task = new BasicTask<Void>(new Runnable() {
                    @Override public void run() {
                        checkHealth();
                    }});
                BrooklynTaskTags.setTransient(task);
                return task;
            }
        };
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);

        if (isRunning()) {
            doStartPolling();
        }
    }

    @Override
    public void suspend() {
        scheduledTask.cancel(true);
        super.suspend();
    }
    
    @Override
    public void resume() {
        currentFailureStartTime = null;
        currentRecoveryStartTime = null;
        lastPublished = LastPublished.NONE;
        executorQueued.set(false);
        executorTime = 0;
        
        super.resume();
        doStartPolling();
    }
    
    protected void doStartPolling() {
        if (scheduledTask == null || scheduledTask.isDone()) {
            ScheduledTask task = new ScheduledTask(MutableMap.of("period", getConfig(POLL_PERIOD)), pollingTaskFactory);
            scheduledTask = ((EntityInternal)entity).getExecutionContext().submit(task);
        }
    }
    
    private Duration getConnectionFailedStabilizationDelay() {
        return getConfig(CONNECTION_FAILED_STABILIZATION_DELAY);
    }

    private Duration getConnectionRecoveredStabilizationDelay() {
        return getConfig(CONNECTION_RECOVERED_STABILIZATION_DELAY);
    }

    private synchronized void checkHealth() {
        CalculatedStatus status = calculateStatus();
        boolean connected = status.connected;
        long now = System.currentTimeMillis();
        
        if (connected) {
            connectionLastUp.set(now);
            if (lastPublished == LastPublished.FAILED) {
                if (currentRecoveryStartTime == null) {
                    LOG.info("{} connectivity-check for {}, now recovering: {}", new Object[] {this, entity, status.getDescription()});
                    currentRecoveryStartTime = now;
                    schedulePublish();
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} connectivity-check for {}, continuing recovering: {}", new Object[] {this, entity, status.getDescription()});
                }
            } else {
                if (currentFailureStartTime != null) {
                    LOG.info("{} connectivity-check for {}, now healthy: {}", new Object[] {this, entity, status.getDescription()});
                    currentFailureStartTime = null;
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} connectivity-check for {}, still healthy: {}", new Object[] {this, entity, status.getDescription()});
                }
            }
        } else {
            connectionLastDown.set(now);
            if (lastPublished != LastPublished.FAILED) {
                if (currentFailureStartTime == null) {
                    LOG.info("{} connectivity-check for {}, now failing: {}", new Object[] {this, entity, status.getDescription()});
                    currentFailureStartTime = now;
                    schedulePublish();
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} connectivity-check for {}, continuing failing: {}", new Object[] {this, entity, status.getDescription()});
                }
            } else {
                if (currentRecoveryStartTime != null) {
                    LOG.info("{} connectivity-check for {}, now failing: {}", new Object[] {this, entity, status.getDescription()});
                    currentRecoveryStartTime = null;
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} connectivity-check for {}, still failed: {}", new Object[] {this, entity, status.getDescription()});
                }
            }
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
                            LOG.error("Problem resizing: "+e, e);
                        } else {
                            if (LOG.isDebugEnabled()) LOG.debug("Problem resizing, but no longer running: "+e, e);
                        }
                    } catch (Throwable t) {
                        LOG.error("Problem in service-failure-detector: "+t, t);
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
        boolean connected = calculatedStatus.connected;
        
        Long lastUpTime = connectionLastUp.get();
        Long lastDownTime = connectionLastDown.get();
        long serviceFailedStabilizationDelay = getConnectionFailedStabilizationDelay().toMilliseconds();
        long serviceRecoveredStabilizationDelay = getConnectionRecoveredStabilizationDelay().toMilliseconds();
        long now = System.currentTimeMillis();
        
        if (connected) {
            if (lastPublished == LastPublished.FAILED) {
                // only publish if consistently up for serviceRecoveredStabilizationDelay
                long currentRecoveryPeriod = getTimeDiff(now, currentRecoveryStartTime);
                long sinceLastDownPeriod = getTimeDiff(now, lastDownTime);
                if (currentRecoveryPeriod > serviceRecoveredStabilizationDelay && sinceLastDownPeriod > serviceRecoveredStabilizationDelay) {
                    String description = calculatedStatus.getDescription();
                    LOG.warn("{} connectivity-check for {}, publishing recovered: {}", new Object[] {this, entity, description});
                    entity.emit(CONNECTION_RECOVERED, new HASensors.FailureDescriptor(entity, description));
                    lastPublished = LastPublished.RECOVERED;
                    currentFailureStartTime = null;
                } else {
                    long nextAttemptTime = Math.max(serviceRecoveredStabilizationDelay - currentRecoveryPeriod, serviceRecoveredStabilizationDelay - sinceLastDownPeriod);
                    schedulePublish(nextAttemptTime);
                }
            }
        } else {
            if (lastPublished != LastPublished.FAILED) {
                // only publish if consistently down for serviceFailedStabilizationDelay
                long currentFailurePeriod = getTimeDiff(now, currentFailureStartTime);
                long sinceLastUpPeriod = getTimeDiff(now, lastUpTime);
                if (currentFailurePeriod > serviceFailedStabilizationDelay && sinceLastUpPeriod > serviceFailedStabilizationDelay) {
                    String description = calculatedStatus.getDescription();
                    LOG.warn("{} connectivity-check for {}, publishing failed: {}", new Object[] {this, entity, description});
                    entity.emit(CONNECTION_FAILED, new HASensors.FailureDescriptor(entity, description));
                    lastPublished = LastPublished.FAILED;
                    currentRecoveryStartTime = null;
                } else {
                    long nextAttemptTime = Math.max(serviceFailedStabilizationDelay - currentFailurePeriod, serviceFailedStabilizationDelay - sinceLastUpPeriod);
                    schedulePublish(nextAttemptTime);
                }
            }
        }
    }

    public class CalculatedStatus {
        public final boolean connected;
        
        public CalculatedStatus() {
            HostAndPort endpoint = getConfig(ENDPOINT);
            connected = Networking.isReachable(endpoint);
        }
        
        public String getDescription() {
            Long lastUpTime = connectionLastUp.get();
            Long lastDownTime = connectionLastDown.get();
            Duration serviceFailedStabilizationDelay = getConnectionFailedStabilizationDelay();
            Duration serviceRecoveredStabilizationDelay = getConnectionRecoveredStabilizationDelay();

            return String.format("endpoint=%s; connected=%s; timeNow=%s; lastUp=%s; lastDown=%s; lastPublished=%s; "+
                        "currentFailurePeriod=%s; currentRecoveryPeriod=%s",
                    getConfig(ENDPOINT), 
                    connected,
                    Time.makeDateString(System.currentTimeMillis()),
                    (lastUpTime != null ? Time.makeDateString(lastUpTime) : "<never>"),
                    (lastDownTime != null ? Time.makeDateString(lastDownTime) : "<never>"),
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
}
