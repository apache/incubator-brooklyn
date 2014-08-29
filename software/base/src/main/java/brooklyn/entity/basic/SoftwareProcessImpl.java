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

import groovy.time.TimeDuration;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.Machines;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Functionals;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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

    private static final SoftwareProcessDriverLifecycleEffectorTasks LIFECYCLE_TASKS =
            new SoftwareProcessDriverLifecycleEffectorTasks();

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
    protected void initEnrichers() {
        super.initEnrichers();
        addEnricher(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
            .from(SERVICE_PROCESS_IS_RUNNING)
            .computing(Functionals.ifNotEquals(true).value("The software process for this entity does not appear to be running"))
            .build());
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
    
    protected void preStop() {
        // note asymmetry that disconnectSensors is done in the entity not the driver
        // whereas on start the *driver* calls connectSensors, before calling postStart,
        // ie waiting for the entity truly to be started before calling postStart;
        // TODO feels like that confusion could be eliminated with a single place for pre/post logic!)
        log.debug("disconnecting sensors for "+this+" in entity.preStop");
        disconnectSensors();
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
        Lifecycle state = getAttribute(SERVICE_STATE_ACTUAL);
        if (state == null || state != Lifecycle.RUNNING) {
            log.warn("On rebind of {}, not rebinding because state is {}", this, state);
            return;
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

    /** @deprecated since 0.7.0, this isn't a general test for modifiability, and was hardly ever used (now never used) */
    @Deprecated
    public void checkModifiable() {
        Lifecycle state = getAttribute(SERVICE_STATE_ACTUAL);
        if (getAttribute(SERVICE_STATE_ACTUAL) == Lifecycle.RUNNING) return;
        if (getAttribute(SERVICE_STATE_ACTUAL) == Lifecycle.STARTING) return;
        throw new IllegalStateException("Cannot configure entity "+this+" in state "+state);
    }

    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverLifecycleEffectorTasks} */
    protected final void startInLocation(Collection<? extends Location> locations) {}

    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverLifecycleEffectorTasks} */
    protected final void startInLocation(Location location) {}

    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverLifecycleEffectorTasks} */
	protected final void startInLocation(final MachineProvisioningLocation<?> location) {}
	
    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverLifecycleEffectorTasks} */
    protected final void startInLocation(MachineLocation machine) {}

    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverLifecycleEffectorTasks} */
    protected final void callStartHooks() {}
    
    protected Map<String,Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        ConfigBag result = ConfigBag.newInstance(location.getProvisioningFlags(ImmutableList.of(getClass().getName())));
        result.putAll(getConfig(PROVISIONING_PROPERTIES));
        if (result.get(CloudLocationConfig.INBOUND_PORTS) == null) {
            Collection<Integer> ports = getRequiredOpenPorts();
            if (ports != null && ports.size() > 0) result.put(CloudLocationConfig.INBOUND_PORTS, ports);
        }
        result.put(LocationConfigKeys.CALLER_CONTEXT, this);
        return result.getAllConfigMutable();
    }

    /** returns the ports that this entity wants to use;
     * default implementation returns 22 plus first value for each PortAttributeSensorAndConfigKey config key PortRange.
     */
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = MutableSet.of(22);
        for (ConfigKey k: getEntityType().getConfigKeys()) {
            if (PortRange.class.isAssignableFrom(k.getType())) {
                PortRange p = (PortRange)getConfig(k);
                if (p != null && !p.isEmpty()) ports.add(p.iterator().next());
            }
        }
        log.debug("getRequiredOpenPorts detected default {} for {}", ports, this);
        return ports;
    }

    /** @deprecated since 0.6.0 use {@link Machines#findSubnetHostname(Entity)} */ @Deprecated
    public String getLocalHostname() {
        return Machines.findSubnetHostname(this).get();
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
        while (!isRunningResult && !timer.isExpired()) {
            Time.sleep(delay);
            try {
                isRunningResult = driver.isRunning();
            } catch (Exception  e) {
                ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
                // provide extra context info, as we're seeing this happen in strange circumstances
                if (driver==null) throw new IllegalStateException(this+" concurrent start and shutdown detected");
                throw new IllegalStateException("Error detecting whether "+this+" is running: "+e, e);
            }
            if (log.isDebugEnabled()) log.debug("checked {}, is running returned: {}", this, isRunningResult);
            // slow exponential delay -- 1.1^N means after 40 tries and 50s elapsed, it reaches the max of 5s intervals
            // TODO use Repeater 
            delay = Math.min(delay*11/10, 5000);
        }
        if (!isRunningResult) {
            String msg = "Software process entity "+this+" did not pass is-running check within "+
                    "the required "+startTimeout+" limit ("+timer.getDurationElapsed().toStringRounded()+" elapsed)";
            log.warn(msg+" (throwing)");
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
            throw new IllegalStateException(msg);
        }
    }

    /**
     * If custom behaviour is required by sub-classes, consider overriding {@link #doStart(Collection)})}.
     */
    @Override
    public final void start(final Collection<? extends Location> locations) {
        if (DynamicTasks.getTaskQueuingContext() != null) {
            doStart(locations);
        } else {
            Task<?> task = Tasks.builder().name("start (sequential)").body(new Runnable() { public void run() { doStart(locations); } }).build();
            Entities.submit(this, task).getUnchecked();
        }
    }

    /**
     * If custom behaviour is required by sub-classes, consider overriding {@link #doStop()}.
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
            doStop();
        } else {
            Task<?> task = Tasks.builder().name("stop").body(new Runnable() { public void run() { doStop(); } }).build();
            Entities.submit(this, task).getUnchecked();
        }
	}

    /**
     * If custom behaviour is required by sub-classes, consider overriding {@link #doRestart()}.
     */
    @Override
    public final void restart() {
        if (DynamicTasks.getTaskQueuingContext() != null) {
            doRestart();
        } else {
            Task<?> task = Tasks.builder().name("restart").body(new Runnable() { public void run() { doRestart(); } }).build();
            Entities.submit(this, task).getUnchecked();
        }
    }
    
    /**
     * To be overridden instead of {@link #start(Collection)}; sub-classes should call {@code super.doStart(locations)} and should
     * add do additional work via tasks, executed using {@link DynamicTasks#queue(String, Callable)}.
     */
    protected void doStart(Collection<? extends Location> locations) {
        LIFECYCLE_TASKS.start(locations);
    }
    
    /**
     * To be overridden instead of {@link #stop()}; sub-classes should call {@code super.doStop()} and should
     * add do additional work via tasks, executed using {@link DynamicTasks#queue(String, Callable)}.
     */
    protected void doStop() {
        LIFECYCLE_TASKS.stop();
    }
	
    /**
     * To be overridden instead of {@link #restart()}; sub-classes should call {@code super.doRestart()} and should
     * add do additional work via tasks, executed using {@link DynamicTasks#queue(String, Callable)}.
     */
    protected void doRestart() {
        LIFECYCLE_TASKS.restart();
    }
}
