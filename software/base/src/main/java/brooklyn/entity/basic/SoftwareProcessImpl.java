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
package brooklyn.entity.basic;

import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import groovy.time.TimeDuration;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.policy.EnricherSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Lifecycle.Transition;
import brooklyn.entity.basic.ServiceStateLogic.ServiceNotUpLogic;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

/**
 * An {@link Entity} representing a piece of software which can be installed, run, and controlled.
 * A single such entity can only run on a single {@link MachineLocation} at a time (you can have multiple on the machine). 
 * It typically takes config keys for suggested versions, filesystem locations to use, and environment variables to set.
 * <p>
 * It exposes sensors for service state (Lifecycle) and status (String), and for host info, log file location.
 */
public abstract class SoftwareProcessImpl extends AbstractEntity implements SoftwareProcess, DriverDependentEntity {
    private static final Logger log = LoggerFactory.getLogger(SoftwareProcessImpl.class);
    
    private transient SoftwareProcessDriver driver;

    /** @see #connectServiceUpIsRunning() */
    private volatile FunctionFeed serviceProcessIsRunning;

    protected boolean connectedSensors = false;
    
    public SoftwareProcessImpl() {
        super(MutableMap.of(), null);
    }
    public SoftwareProcessImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public SoftwareProcessImpl(Map properties) {
        this(properties, null);
    }
    public SoftwareProcessImpl(Map properties, Entity parent) {
        super(properties, parent);
    }

    protected void setProvisioningLocation(MachineProvisioningLocation val) {
        if (getAttribute(PROVISIONING_LOCATION) != null) throw new IllegalStateException("Cannot change provisioning location: existing="+getAttribute(PROVISIONING_LOCATION)+"; new="+val);
        setAttribute(PROVISIONING_LOCATION, val);
    }
    
    protected MachineProvisioningLocation getProvisioningLocation() {
        return getAttribute(PROVISIONING_LOCATION);
    }
    
    @Override
    public SoftwareProcessDriver getDriver() {
        return driver;
    }

    protected SoftwareProcessDriver newDriver(MachineLocation loc){
        EntityDriverManager entityDriverManager = getManagementContext().getEntityDriverManager();
        return (SoftwareProcessDriver)entityDriverManager.build(this, loc);
    }

    protected MachineLocation getMachineOrNull() {
        return Iterables.get(Iterables.filter(getLocations(), MachineLocation.class), 0, null);
    }

    @Override
    public void init() {
        super.init();
        getLifecycleEffectorTasks().attachLifecycleEffectors(this);
    }
    
    @Override
    protected void initEnrichers() {
        super.initEnrichers();
        ServiceNotUpLogic.updateNotUpIndicator(this, SERVICE_PROCESS_IS_RUNNING, "No information yet on whether this service is running");
        // add an indicator above so that if is_running comes through, the map is cleared and an update is guaranteed
        addEnricher(EnricherSpec.create(UpdatingNotUpFromServiceProcessIsRunning.class).uniqueTag("service-process-is-running-updating-not-up"));
        addEnricher(EnricherSpec.create(ServiceNotUpDiagnosticsCollector.class).uniqueTag("service-not-up-diagnostics-collector"));
    }
    
    /**
     * @since 0.8.0
     */
    protected static class ServiceNotUpDiagnosticsCollector extends AbstractEnricher implements SensorEventListener<Object> {
        public ServiceNotUpDiagnosticsCollector() {
        }
        
        @Override
        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            if (!(entity instanceof SoftwareProcess)) {
                throw new IllegalArgumentException("Expected SoftwareProcess, but got entity "+entity);
            }
            subscribe(entity, Attributes.SERVICE_UP, this);
            onUpdated();
        }

        @Override
        public void onEvent(SensorEvent<Object> event) {
            onUpdated();
        }

        protected void onUpdated() {
            Boolean up = entity.getAttribute(SERVICE_UP);
            if (up == null || up) {
                entity.setAttribute(ServiceStateLogic.SERVICE_NOT_UP_DIAGNOSTICS, ImmutableMap.<String, Object>of());
            } else {
                ((SoftwareProcess)entity).populateServiceNotUpDiagnostics();
            }
        }
    }
    
    @Override
    public void populateServiceNotUpDiagnostics() {
        if (getDriver() == null) {
            ServiceStateLogic.updateMapSensorEntry(this, ServiceStateLogic.SERVICE_NOT_UP_DIAGNOSTICS, "driver", "No driver");
            return;
        }

        Location loc = getDriver().getLocation();
        if (loc instanceof SshMachineLocation) {
            if (!((SshMachineLocation)loc).isSshable()) {
                ServiceStateLogic.updateMapSensorEntry(
                        this, 
                        ServiceStateLogic.SERVICE_NOT_UP_DIAGNOSTICS, 
                        "sshable", 
                        "The machine for this entity does not appear to be sshable");
            }
            return;
        }

        boolean processIsRunning = getDriver().isRunning();
        if (!processIsRunning) {
            ServiceStateLogic.updateMapSensorEntry(
                    this, 
                    ServiceStateLogic.SERVICE_NOT_UP_DIAGNOSTICS, 
                    SERVICE_PROCESS_IS_RUNNING.getName(), 
                    "The software process for this entity does not appear to be running");
        }
    }

    /** subscribes to SERVICE_PROCESS_IS_RUNNING and SERVICE_UP; the latter has no effect if the former is set,
     * but to support entities which set SERVICE_UP directly we want to make sure that the absence of 
     * SERVICE_PROCESS_IS_RUNNING does not trigger any not-up indicators */
    protected static class UpdatingNotUpFromServiceProcessIsRunning extends AbstractEnricher implements SensorEventListener<Object> {
        public UpdatingNotUpFromServiceProcessIsRunning() {}
        
        @Override
        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            subscribe(entity, SERVICE_PROCESS_IS_RUNNING, this);
            subscribe(entity, Attributes.SERVICE_UP, this);
            onUpdated();
        }

        @Override
        public void onEvent(SensorEvent<Object> event) {
            onUpdated();
        }

        protected void onUpdated() {
            Boolean isRunning = entity.getAttribute(SERVICE_PROCESS_IS_RUNNING);
            if (Boolean.FALSE.equals(isRunning)) {
                ServiceNotUpLogic.updateNotUpIndicator(entity, SERVICE_PROCESS_IS_RUNNING, "The software process for this entity does not appear to be running");
                return;
            }
            if (Boolean.TRUE.equals(isRunning)) {
                ServiceNotUpLogic.clearNotUpIndicator(entity, SERVICE_PROCESS_IS_RUNNING);
                return;
            }
            // no info on "isRunning"
            Boolean isUp = entity.getAttribute(Attributes.SERVICE_UP);
            if (Boolean.TRUE.equals(isUp)) {
                // if service explicitly set up, then don't apply our rule
                ServiceNotUpLogic.clearNotUpIndicator(entity, SERVICE_PROCESS_IS_RUNNING);
                return;
            }
            // service not up, or no info
            ServiceNotUpLogic.updateNotUpIndicator(entity, SERVICE_PROCESS_IS_RUNNING, "No information on whether this service is running");
        }
    }
    
    /**
     * Called before driver.start; guarantees the driver will exist, and locations will have been set.
     */
    protected void preStart() {
    }
    
    /**
     * Called after driver.start(). Default implementation is to wait to confirm the driver 
     * definitely started the process.
     */
    protected void postDriverStart() {
        waitForEntityStart();
    }

    /**
     * For binding to the running app (e.g. connecting sensors to registry). Will be called
     * on start() and on rebind().
     * <p>
     * Implementations should be idempotent (ie tell whether sensors already connected),
     * though the framework is pretty good about not calling when already connected. 
     * TODO improve the framework's feed system to detect duplicate additions
     */
    protected void connectSensors() {
        connectedSensors = true;
    }

    /**
     * For connecting the {@link #SERVICE_UP} sensor to the value of the {@code getDriver().isRunning()} expression.
     * <p>
     * Should be called inside {@link #connectSensors()}.
     *
     * @see #disconnectServiceUpIsRunning()
     */
    protected void connectServiceUpIsRunning() {
        serviceProcessIsRunning = FunctionFeed.builder()
                .entity(this)
                .period(Duration.FIVE_SECONDS)
                .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_PROCESS_IS_RUNNING)
                        .suppressDuplicates(true)
                        .onException(Functions.constant(Boolean.FALSE))
                        .callable(new Callable<Boolean>() {
                            public Boolean call() {
                                return getDriver().isRunning();
                            }
                        }))
                .build();
    }

    /**
     * For disconnecting the {@link #SERVICE_UP} feed.
     * <p>
     * Should be called from {@link #disconnectSensors()}.
     *
     * @see #connectServiceUpIsRunning()
     */
    protected void disconnectServiceUpIsRunning() {
        if (serviceProcessIsRunning != null) serviceProcessIsRunning.stop();
        // set null so the SERVICE_UP enricher runs (possibly removing it), then remove so everything is removed
        // TODO race because the is-running check may be mid-task
        setAttribute(SERVICE_PROCESS_IS_RUNNING, null);
        removeAttribute(SERVICE_PROCESS_IS_RUNNING);
    }

    /**
     * Called after the rest of start has completed (after {@link #connectSensors()} and {@link #waitForServiceUp()})
     */
    protected void postStart() {
    }
    
    protected void preStopConfirmCustom() {
    }
    
    protected void preStop() {
        // note asymmetry that disconnectSensors is done in the entity not the driver
        // whereas on start the *driver* calls connectSensors, before calling postStart,
        // ie waiting for the entity truly to be started before calling postStart;
        // TODO feels like that confusion could be eliminated with a single place for pre/post logic!)
        log.debug("disconnecting sensors for "+this+" in entity.preStop");
        disconnectSensors();
        
        // Must set the serviceProcessIsRunning explicitly to false - we've disconnected the sensors
        // so nothing else will.
        // Otherwise, if restarted, there will be no change to serviceProcessIsRunning, so the
        // serviceUpIndicators will not change, so serviceUp will not be reset.
        // TODO Is there a race where disconnectSensors could leave a task of the feeds still running
        // which could set serviceProcessIsRunning to true again before the task completes and the feed
        // is fully terminated?
        setAttribute(SoftwareProcess.SERVICE_PROCESS_IS_RUNNING, false);
    }

    /**
     * Called after the rest of stop has completed (after VM deprovisioned, but before state set to STOPPED)
     */
    protected void postStop() {
    }

    /**
     * Called before driver.restart; guarantees the driver will exist, and locations will have been set.
     */
    protected void preRestart() {
    }

    protected void postRestart() {
    }

    /**
     * For disconnecting from the running app. Will be called on stop.
     */
    protected void disconnectSensors() {
        connectedSensors = false;
    }

    /**
     * Called after this entity is fully rebound (i.e. it is fully managed).
     */
    protected void postRebind() {
    }
    
    protected void callRebindHooks() {
        Duration configuredMaxDelay = getConfig(MAXIMUM_REBIND_SENSOR_CONNECT_DELAY);
        if (configuredMaxDelay == null || Duration.ZERO.equals(configuredMaxDelay)) {
            connectSensors();
        } else {
            long delay = (long) (Math.random() * configuredMaxDelay.toMilliseconds());
            log.debug("Scheduled reconnection of sensors on {} in {}ms", this, delay);
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override public void run() {
                    try {
                        if (getManagementSupport().isNoLongerManaged()) {
                            log.debug("Entity {} no longer managed; ignoring scheduled connect sensors on rebind", SoftwareProcessImpl.this);
                            return;
                        }
                        connectSensors();
                    } catch (Throwable e) {
                        log.warn("Problem connecting sensors on rebind of "+SoftwareProcessImpl.this, e);
                        Exceptions.propagateIfFatal(e);
                    }
                }
            }, delay);
        }
        // don't wait here - it may be long-running, e.g. if remote entity has died, and we don't want to block rebind waiting or cause it to fail
        // the service will subsequently show service not up and thus failure
//        waitForServiceUp();
    }

    @Override 
    public void onManagementStarting() {
        super.onManagementStarting();
        
        Lifecycle state = getAttribute(SERVICE_STATE_ACTUAL);
        if (state == null || state == Lifecycle.CREATED) {
            // Expect this is a normal start() sequence (i.e. start() will subsequently be called)
            setAttribute(SERVICE_UP, false);
            ServiceStateLogic.setExpectedState(this, Lifecycle.CREATED);
            // force actual to be created because this is expected subsequently
            setAttribute(SERVICE_STATE_ACTUAL, Lifecycle.CREATED);
        }
    }
    
    @Override 
    public void onManagementStarted() {
        super.onManagementStarted();
        
        Lifecycle state = getAttribute(SERVICE_STATE_ACTUAL);
        if (state != null && state != Lifecycle.CREATED) {
            postRebind();
        }
    }
    
    @Override
    public void rebind() {
        //SERVICE_STATE_ACTUAL might be ON_FIRE due to a temporary condition (problems map non-empty)
        //Only if the expected state is ON_FIRE then the entity has permanently failed.
        Transition expectedState = getAttribute(SERVICE_STATE_EXPECTED);
        if (expectedState == null || expectedState.getState() != Lifecycle.RUNNING) {
            log.warn("On rebind of {}, not calling software process rebind hooks because expected state is {}", this, expectedState);
            return;
        }

        Lifecycle actualState = getAttribute(SERVICE_STATE_ACTUAL);
        if (actualState == null || actualState != Lifecycle.RUNNING) {
            log.warn("Rebinding entity {}, even though actual state is {}. Expected state is {}", new Object[] {this, actualState, expectedState});
        }

        // e.g. rebinding to a running instance
        // FIXME For rebind, what to do about things in STARTING or STOPPING state?
        // FIXME What if location not set?
        log.info("Rebind {} connecting to pre-running service", this);
        
        MachineLocation machine = getMachineOrNull();
        if (machine != null) {
            initDriver(machine);
            driver.rebind();
            if (log.isDebugEnabled()) log.debug("On rebind of {}, re-created driver {}", this, driver);
        } else {
            log.info("On rebind of {}, no MachineLocation found (with locations {}) so not generating driver",
                    this, getLocations());
        }
        
        callRebindHooks();
    }
    
    public void waitForServiceUp() {
        Duration timeout = getConfig(BrooklynConfigKeys.START_TIMEOUT);
        waitForServiceUp(timeout);
    }
    public void waitForServiceUp(Duration duration) {
        Entities.waitForServiceUp(this, duration);
    }
    public void waitForServiceUp(TimeDuration duration) {
        waitForServiceUp(duration.toMilliseconds(), TimeUnit.MILLISECONDS);
    }
    public void waitForServiceUp(long duration, TimeUnit units) {
        Entities.waitForServiceUp(this, Duration.of(duration, units));
    }

    protected Map<String,Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        ConfigBag result = ConfigBag.newInstance(location.getProvisioningFlags(ImmutableList.of(getClass().getName())));
        result.putAll(getConfig(PROVISIONING_PROPERTIES));
        if (result.get(CloudLocationConfig.INBOUND_PORTS) == null) {
            Collection<Integer> ports = getRequiredOpenPorts();
            Object requiredPorts = result.get(CloudLocationConfig.ADDITIONAL_INBOUND_PORTS);
            if (requiredPorts instanceof Integer) {
                ports.add((Integer) requiredPorts);
            } else if (requiredPorts instanceof Iterable) {
                for (Object o : (Iterable<?>) requiredPorts) {
                    if (o instanceof Integer) ports.add((Integer) o);
                }
            }
            if (ports != null && ports.size() > 0) result.put(CloudLocationConfig.INBOUND_PORTS, ports);
        }
        result.put(LocationConfigKeys.CALLER_CONTEXT, this);
        return result.getAllConfigMutable();
    }

    /** returns the ports that this entity wants to use;
     * default implementation returns {@link SoftwareProcess#REQUIRED_OPEN_LOGIN_PORTS} plus first value 
     * for each {@link brooklyn.event.basic.PortAttributeSensorAndConfigKey} config key {@link PortRange}
     * plus any ports defined with a config keys ending in {@code .port}.
     */
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = MutableSet.copyOf(getConfig(REQUIRED_OPEN_LOGIN_PORTS));
        Map<ConfigKey<?>, ?> allConfig = config().getBag().getAllConfigAsConfigKeyMap();
        Set<ConfigKey<?>> configKeys = Sets.newHashSet(allConfig.keySet());
        configKeys.addAll(getEntityType().getConfigKeys());

        /* TODO: This won't work if there's a port collision, which will cause the corresponding port attribute
           to be incremented until a free port is found. In that case the entity will use the free port, but the
           firewall will open the initial port instead. Mostly a problem for SameServerEntity, localhost location.
         */
        for (ConfigKey<?> k: configKeys) {
            Object value;
            if (PortRange.class.isAssignableFrom(k.getType()) || k.getName().matches(".*\\.port")) {
                value = config().get(k);
            } else {
                // config().get() will cause this to block until all config has been resolved
                // using config().getRaw(k) means that we won't be able to use e.g. 'http.port: $brooklyn:component("x").attributeWhenReady("foo")'
                // but that's unlikely to be used
                Maybe<Object> maybeValue = config().getRaw(k);
                value = maybeValue.isPresent() ? maybeValue.get() : null;
            }

            Maybe<PortRange> maybePortRange = TypeCoercions.tryCoerce(value, new TypeToken<PortRange>() {});

            if (maybePortRange.isPresentAndNonNull()) {
                PortRange p = maybePortRange.get();
                if (p != null && !p.isEmpty()) ports.add(p.iterator().next());
            }
        }        
        
        log.debug("getRequiredOpenPorts detected default {} for {}", ports, this);
        return ports;
    }

    protected void initDriver(MachineLocation machine) {
        SoftwareProcessDriver newDriver = doInitDriver(machine);
        if (newDriver == null) {
            throw new UnsupportedOperationException("cannot start "+this+" on "+machine+": no driver available");
        }
        driver = newDriver;
    }

    /**
     * Creates the driver (if does not already exist or needs replaced for some reason). Returns either the existing driver
     * or a new driver. Must not return null.
     */
    protected SoftwareProcessDriver doInitDriver(MachineLocation machine) {
        if (driver!=null) {
            if ((driver instanceof AbstractSoftwareProcessDriver) && machine.equals(((AbstractSoftwareProcessDriver)driver).getLocation())) {
                return driver; //just reuse
            } else {
                log.warn("driver/location change is untested for {} at {}; changing driver and continuing", this, machine);
                return newDriver(machine);
            }
        } else {
            return newDriver(machine);
        }
    }
    
    // TODO Find a better way to detect early death of process.
    public void waitForEntityStart() {
        if (log.isDebugEnabled()) log.debug("waiting to ensure {} doesn't abort prematurely", this);
        Duration startTimeout = getConfig(START_TIMEOUT);
        CountdownTimer timer = startTimeout.countdownTimer();
        boolean isRunningResult = false;
        long delay = 100;
        Exception firstFailure = null;
        while (!isRunningResult && !timer.isExpired()) {
            Time.sleep(delay);
            try {
                isRunningResult = driver.isRunning();
                if (log.isDebugEnabled()) log.debug("checked {}, 'is running' returned: {}", this, isRunningResult);
            } catch (Exception  e) {
                Exceptions.propagateIfFatal(e);

                isRunningResult = false;
                if (driver != null) {
                    String msg = "checked " + this + ", 'is running' threw an exception; logging subsequent exceptions at debug level";
                    if (firstFailure == null) {
                        log.error(msg, e);
                    } else {
                        log.debug(msg, e);
                    }
                } else {
                    // provide extra context info, as we're seeing this happen in strange circumstances
                    log.error(this+" concurrent start and shutdown detected", e);
                }
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
            // slow exponential delay -- 1.1^N means after 40 tries and 50s elapsed, it reaches the max of 5s intervals
            // TODO use Repeater 
            delay = Math.min(delay*11/10, 5000);
        }
        if (!isRunningResult) {
            String msg = "Software process entity "+this+" did not pass is-running check within "+
                    "the required "+startTimeout+" limit ("+timer.getDurationElapsed().toStringRounded()+" elapsed)";
            if (firstFailure != null) {
                msg += "; check failed at least once with exception: " + firstFailure.getMessage() + ", see logs for details";
            }
            log.warn(msg+" (throwing)");
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
            throw new IllegalStateException(msg, firstFailure);
        }
    }

    /**
     * If custom behaviour is required by sub-classes, consider overriding {@link #preStart()} or {@link #postStart()})}.
     * Also consider adding additional work via tasks, executed using {@link DynamicTasks#queue(String, Callable)}.
     */
    @Override
    public final void start(final Collection<? extends Location> locations) {
        if (DynamicTasks.getTaskQueuingContext() != null) {
            getLifecycleEffectorTasks().start(locations);
        } else {
            Task<?> task = Tasks.builder().name("start (sequential)").body(new Runnable() { public void run() { getLifecycleEffectorTasks().start(locations); } }).build();
            Entities.submit(this, task).getUnchecked();
        }
    }

    /**
     * If custom behaviour is required by sub-classes, consider overriding  {@link #preStop()} or {@link #postStop()}.
     * Also consider adding additional work via tasks, executed using {@link DynamicTasks#queue(String, Callable)}.
     */
    @Override
    public final void stop() {
        // TODO There is a race where we set SERVICE_UP=false while sensor-adapter threads may still be polling.
        // The other thread might reset SERVICE_UP to true immediately after we set it to false here.
        // Deactivating adapters before setting SERVICE_UP reduces the race, and it is reduced further by setting
        // SERVICE_UP to false at the end of stop as well.
        
        // Perhaps we should wait until all feeds have completed here, 
        // or do a SERVICE_STATE check before setting SERVICE_UP to true in a feed (?).

        if (DynamicTasks.getTaskQueuingContext() != null) {
            getLifecycleEffectorTasks().stop(ConfigBag.EMPTY);
        } else {
            Task<?> task = Tasks.builder().name("stop").body(new Runnable() { public void run() { getLifecycleEffectorTasks().stop(ConfigBag.EMPTY); } }).build();
            Entities.submit(this, task).getUnchecked();
        }
    }

    /**
     * If custom behaviour is required by sub-classes, consider overriding {@link #preRestart()} or {@link #postRestart()}.
     * Also consider adding additional work via tasks, executed using {@link DynamicTasks#queue(String, Callable)}.
     */
    @Override
    public final void restart() {
        if (DynamicTasks.getTaskQueuingContext() != null) {
            getLifecycleEffectorTasks().restart(ConfigBag.EMPTY);
        } else {
            Task<?> task = Tasks.builder().name("restart").body(new Runnable() { public void run() { getLifecycleEffectorTasks().restart(ConfigBag.EMPTY); } }).build();
            Entities.submit(this, task).getUnchecked();
        }
    }
    
    protected SoftwareProcessDriverLifecycleEffectorTasks getLifecycleEffectorTasks() {
        return getConfig(LIFECYCLE_EFFECTOR_TASKS);
    }

}
