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

import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.event.Sensor;
import org.apache.brooklyn.api.management.Task;
import org.apache.brooklyn.core.policy.basic.AbstractPolicy;
import org.apache.brooklyn.core.util.flags.SetFromFlag;
import org.apache.brooklyn.core.util.task.BasicTask;
import org.apache.brooklyn.core.util.task.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.reflect.TypeToken;

public abstract class AbstractFailureDetector extends AbstractPolicy {

    // TODO Remove duplication from ServiceFailureDetector, particularly for the stabilisation delays.

    private static final Logger LOG = LoggerFactory.getLogger(AbstractFailureDetector.class);

    private static final long MIN_PERIOD_BETWEEN_EXECS_MILLIS = 100;

    public static final ConfigKey<Duration> POLL_PERIOD = ConfigKeys.newDurationConfigKey(
            "failureDetector.pollPeriod", "", Duration.ONE_SECOND);

    @SetFromFlag("failedStabilizationDelay")
    public static final ConfigKey<Duration> FAILED_STABILIZATION_DELAY = ConfigKeys.newDurationConfigKey(
            "failureDetector.serviceFailedStabilizationDelay",
            "Time period for which the health check consistently fails "
                    + "(e.g. doesn't report failed-ok-faled) before concluding failure.",
            Duration.ZERO);

    @SetFromFlag("recoveredStabilizationDelay")
    public static final ConfigKey<Duration> RECOVERED_STABILIZATION_DELAY = ConfigKeys.newDurationConfigKey(
            "failureDetector.serviceRecoveredStabilizationDelay",
            "Time period for which the health check succeeds continiually " +
                    "(e.g. doesn't report ok-failed-ok) before concluding recovered",
            Duration.ZERO);

    @SuppressWarnings("serial")
    public static final ConfigKey<Sensor<FailureDescriptor>> SENSOR_FAILED = ConfigKeys.newConfigKey(new TypeToken<Sensor<FailureDescriptor>>() {},
            "failureDetector.sensor.fail", "A sensor which will indicate failure when set", HASensors.ENTITY_FAILED);

    @SuppressWarnings("serial")
    public static final ConfigKey<Sensor<FailureDescriptor>> SENSOR_RECOVERED = ConfigKeys.newConfigKey(new TypeToken<Sensor<FailureDescriptor>>() {},
            "failureDetector.sensor.recover", "A sensor which will indicate recovery from failure when set", HASensors.ENTITY_RECOVERED);

    public interface CalculatedStatus {
        boolean isHealthy();
        String getDescription();
    }

    private final class PublishJob implements Runnable {
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
    }

    private final class HealthPoller implements Runnable {
        @Override
        public void run() {
            checkHealth();
        }
    }

    private final class HealthPollingTaskFactory implements Callable<Task<?>> {
        @Override
        public Task<?> call() {
            BasicTask<Void> task = new BasicTask<Void>(new HealthPoller());
            BrooklynTaskTags.setTransient(task);
            return task;
        }
    }

    protected static class BasicCalculatedStatus implements CalculatedStatus {
        private boolean healthy;
        private String description;

        public BasicCalculatedStatus(boolean healthy, String description) {
            this.healthy = healthy;
            this.description = description;
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    public enum LastPublished {
        NONE,
        FAILED,
        RECOVERED;
    }

    protected final AtomicReference<Long> stateLastGood = new AtomicReference<Long>();
    protected final AtomicReference<Long> stateLastFail = new AtomicReference<Long>();

    protected Long currentFailureStartTime = null;
    protected Long currentRecoveryStartTime = null;

    protected LastPublished lastPublished = LastPublished.NONE;

    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;

    private Callable<Task<?>> pollingTaskFactory = new HealthPollingTaskFactory();

    private Task<?> scheduledTask;

    protected abstract CalculatedStatus calculateStatus();

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

    @SuppressWarnings("unchecked")
    protected void doStartPolling() {
        if (scheduledTask == null || scheduledTask.isDone()) {
            ScheduledTask task = new ScheduledTask(MutableMap.of("period", getPollPeriod(), "displayName", getTaskName()), pollingTaskFactory);
            scheduledTask = ((EntityInternal)entity).getExecutionContext().submit(task);
        }
    }

    private String getTaskName() {
        return getDisplayName();
    }

    protected Duration getPollPeriod() {
        return getConfig(POLL_PERIOD);
    }

    protected Duration getFailedStabilizationDelay() {
        return getConfig(FAILED_STABILIZATION_DELAY);
    }

    protected Duration getRecoveredStabilizationDelay() {
        return getConfig(RECOVERED_STABILIZATION_DELAY);
    }

    protected Sensor<FailureDescriptor> getSensorFailed() {
        return getConfig(SENSOR_FAILED);
    }

    protected Sensor<FailureDescriptor> getSensorRecovered() {
        return getConfig(SENSOR_RECOVERED);
    }

    private synchronized void checkHealth() {
        CalculatedStatus status = calculateStatus();
        boolean healthy = status.isHealthy();
        long now = System.currentTimeMillis();

        if (healthy) {
            stateLastGood.set(now);
            if (lastPublished == LastPublished.FAILED) {
                if (currentRecoveryStartTime == null) {
                    LOG.info("{} check for {}, now recovering: {}", new Object[] {this, entity, getDescription(status)});
                    currentRecoveryStartTime = now;
                    schedulePublish();
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} check for {}, continuing recovering: {}", new Object[] {this, entity, getDescription(status)});
                }
            } else {
                if (currentFailureStartTime != null) {
                    LOG.info("{} check for {}, now healthy: {}", new Object[] {this, entity, getDescription(status)});
                    currentFailureStartTime = null;
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} check for {}, still healthy: {}", new Object[] {this, entity, getDescription(status)});
                }
            }
        } else {
            stateLastFail.set(now);
            if (lastPublished != LastPublished.FAILED) {
                if (currentFailureStartTime == null) {
                    LOG.info("{} check for {}, now failing: {}", new Object[] {this, entity, getDescription(status)});
                    currentFailureStartTime = now;
                    schedulePublish();
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} check for {}, continuing failing: {}", new Object[] {this, entity, getDescription(status)});
                }
            } else {
                if (currentRecoveryStartTime != null) {
                    LOG.info("{} check for {}, now failing: {}", new Object[] {this, entity, getDescription(status)});
                    currentRecoveryStartTime = null;
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("{} check for {}, still failed: {}", new Object[] {this, entity, getDescription(status)});
                }
            }
        }
    }

    protected void schedulePublish() {
        schedulePublish(0);
    }

    @SuppressWarnings("unchecked")
    protected void schedulePublish(long delay) {
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            delay = Math.max(0, Math.max(delay, (executorTime + MIN_PERIOD_BETWEEN_EXECS_MILLIS) - now));
            if (LOG.isTraceEnabled()) LOG.trace("{} scheduling publish in {}ms", this, delay);

            Runnable job = new PublishJob();

            ScheduledTask task = new ScheduledTask(MutableMap.of("delay", Duration.of(delay, TimeUnit.MILLISECONDS)), new BasicTask<Void>(job));
            ((EntityInternal)entity).getExecutionContext().submit(task);
        }
    }

    private synchronized void publishNow() {
        if (!isRunning()) return;

        CalculatedStatus calculatedStatus = calculateStatus();
        boolean healthy = calculatedStatus.isHealthy();

        Long lastUpTime = stateLastGood.get();
        Long lastDownTime = stateLastFail.get();
        long serviceFailedStabilizationDelay = getFailedStabilizationDelay().toMilliseconds();
        long serviceRecoveredStabilizationDelay = getRecoveredStabilizationDelay().toMilliseconds();
        long now = System.currentTimeMillis();

        if (healthy) {
            if (lastPublished == LastPublished.FAILED) {
                // only publish if consistently up for serviceRecoveredStabilizationDelay
                long currentRecoveryPeriod = getTimeDiff(now, currentRecoveryStartTime);
                long sinceLastDownPeriod = getTimeDiff(now, lastDownTime);
                if (currentRecoveryPeriod > serviceRecoveredStabilizationDelay && sinceLastDownPeriod > serviceRecoveredStabilizationDelay) {
                    String description = getDescription(calculatedStatus);
                    LOG.warn("{} check for {}, publishing recovered: {}", new Object[] {this, entity, description});
                    entity.emit(getSensorRecovered(), new HASensors.FailureDescriptor(entity, description));
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
                    String description = getDescription(calculatedStatus);
                    LOG.warn("{} connectivity-check for {}, publishing failed: {}", new Object[] {this, entity, description});
                    entity.emit(getSensorFailed(), new HASensors.FailureDescriptor(entity, description));
                    lastPublished = LastPublished.FAILED;
                    currentRecoveryStartTime = null;
                } else {
                    long nextAttemptTime = Math.max(serviceFailedStabilizationDelay - currentFailurePeriod, serviceFailedStabilizationDelay - sinceLastUpPeriod);
                    schedulePublish(nextAttemptTime);
                }
            }
        }
    }

    protected String getDescription(CalculatedStatus status) {
        Long lastUpTime = stateLastGood.get();
        Long lastDownTime = stateLastGood.get();
        Duration serviceFailedStabilizationDelay = getFailedStabilizationDelay();
        Duration serviceRecoveredStabilizationDelay = getRecoveredStabilizationDelay();

        return String.format("%s; healthy=%s; timeNow=%s; lastUp=%s; lastDown=%s; lastPublished=%s; "+
                    "currentFailurePeriod=%s; currentRecoveryPeriod=%s",
                status.getDescription(),
                status.isHealthy(),
                Time.makeDateString(System.currentTimeMillis()),
                (lastUpTime != null ? Time.makeDateString(lastUpTime) : "<never>"),
                (lastDownTime != null ? Time.makeDateString(lastDownTime) : "<never>"),
                lastPublished,
                (currentFailureStartTime != null ? getTimeStringSince(currentFailureStartTime) : "<none>") + " (stabilization "+makeTimeStringRounded(serviceFailedStabilizationDelay) + ")",
                (currentRecoveryStartTime != null ? getTimeStringSince(currentRecoveryStartTime) : "<none>") + " (stabilization "+makeTimeStringRounded(serviceRecoveredStabilizationDelay) + ")");
    }

    private long getTimeDiff(Long recent, Long previous) {
        return (previous == null) ? recent : (recent - previous);
    }

    private String getTimeStringSince(Long time) {
        return time == null ? null : Time.makeTimeStringRounded(System.currentTimeMillis() - time);
    }
}
