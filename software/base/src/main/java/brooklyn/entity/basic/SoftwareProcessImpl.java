package brooklyn.entity.basic;

import groovy.time.TimeDuration;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.PortRange;
import brooklyn.location.basic.HasSubnetHostname;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.Repeater;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

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
    private volatile FunctionFeed serviceUp;

    private static final SoftwareProcessDriverLifecycleEffectorTasks LIFECYCLE_TASKS =
            new SoftwareProcessDriverLifecycleEffectorTasks();
    
    public static final Effector<Void> START = LIFECYCLE_TASKS.newStartEffector();
    public static final Effector<Void> RESTART = LIFECYCLE_TASKS.newRestartEffector();
    public static final Effector<Void> STOP = LIFECYCLE_TASKS.newStopEffector();
    
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
     */
    protected void connectSensors() {
    }

    /**
     * For connecting the {@link #SERVICE_UP} sensor to the value of the {@code getDriver().isRunning()} expression.
     * <p>
     * Should be called inside {@link #connectSensors()}.
     *
     * @see #disconnectServiceUpIsRunning()
     */
    protected void connectServiceUpIsRunning() {
        serviceUp = FunctionFeed.builder()
                .entity(this)
                .period(5000)
                .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_UP)
                        .onException(Functions.constant(Boolean.FALSE))
                        .callable(new Callable<Boolean>() {
                            public Boolean call() {
                                return getDriver().isRunning();
                            }
                        }))
                .build();
    }

    /**
     * For disconneting the {@link #SERVICE_UP} feed.
     * <p>
     * Should be called from {@link #disconnectSensors()}.
     *
     * @see #connectServiceUpIsRunning()
     */
    protected void disconnectServiceUpIsRunning() {
        if (serviceUp != null && serviceUp.isActivated()) serviceUp.stop();
    }

    /**
     * Called after the rest of start has completed.
     */
    protected void postStart() {
    }
    
    protected void postDriverRestart() {
        waitForEntityStart();
    }
    
    protected void postRestart() {
        waitForServiceUp();
    }
    
    protected void preStop() {
        disconnectSensors();
    }

    /**
     * For disconneting from the running app. Will be called on stop.
     */
    protected void disconnectSensors() {
    }

    /**
     * Called after this entity is fully rebound (i.e. it is fully managed).
     */
    protected void postRebind() {
    }
    
    protected void callRebindHooks() {
        connectSensors();
        waitForServiceUp();
    }

    @Override 
    public void onManagementStarting() {
        super.onManagementStarting();
        
        Lifecycle state = getAttribute(SERVICE_STATE);
        if (state == Lifecycle.RUNNING) {
            rebind();
        } else if (state != null && state != Lifecycle.CREATED) {
            log.warn("On start-up of {}, not (re)binding because state is {}", this, state);
    	} else {
            // Expect this is a normal start() sequence (i.e. start() will subsequently be called)
            setAttribute(SERVICE_UP, false);
            setAttribute(SERVICE_STATE, Lifecycle.CREATED);
    	}
    }
	
    @Override 
    public void onManagementStarted() {
        super.onManagementStarted();
        
        Lifecycle state = getAttribute(SERVICE_STATE);
        if (state != null && state != Lifecycle.CREATED) {
            postRebind();
        }
    }
    
    protected void rebind() {
        // e.g. rebinding to a running instance
        // FIXME For rebind, what to do about things in STARTING or STOPPING state?
        // FIXME What if location not set?
        log.info("Connecting to pre-running service: {}", this);
        
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
        Integer timeout = getConfig(ConfigKeys.START_TIMEOUT);
        waitForServiceUp(timeout, TimeUnit.SECONDS);
    }
    public void waitForServiceUp(Duration duration) {
        waitForServiceUp(duration.toMilliseconds(), TimeUnit.MILLISECONDS);
    }
    public void waitForServiceUp(TimeDuration duration) {
        waitForServiceUp(duration.toMilliseconds(), TimeUnit.MILLISECONDS);
    }
    public void waitForServiceUp(long duration, TimeUnit units) {
        String description = "Waiting for SERVICE_UP on "+this;
        Tasks.setBlockingDetails(description);
        if (!Repeater.create(ImmutableMap.of("timeout", units.toMillis(duration), "description", description))
                .rethrowException().repeat().every(1, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    public Boolean call() {
                        return getAttribute(SERVICE_UP);
                    }})
                .run()) {
            throw new IllegalStateException("Timeout waiting for SERVICE_UP from "+this);
        }
        Tasks.resetBlockingDetails();
        log.debug("Detected SERVICE_UP for software {}", this);
    }

    public void checkModifiable() {
        Lifecycle state = getAttribute(SERVICE_STATE);
        if (getAttribute(SERVICE_STATE) == Lifecycle.RUNNING) return;
        if (getAttribute(SERVICE_STATE) == Lifecycle.STARTING) return;
        // TODO this check may be redundant or even inappropriate
        throw new IllegalStateException("Cannot configure entity "+this+" in state "+state);
    }

    @Override
	public final void start(Collection<? extends Location> locations) {
        invoke(START, ConfigBag.newInstance().configure(SoftwareProcessDriverLifecycleEffectorTasks.LOCATIONS, locations).getAllConfig())
            .getUnchecked();
    }
    
    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverStartEffectorTask} */
    protected final void startInLocation(Collection<? extends Location> locations) {}

    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverStartEffectorTask} */
    protected final void startInLocation(Location location) {}

    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverStartEffectorTask} */
	protected final void startInLocation(final MachineProvisioningLocation<?> location) {}
	
    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverStartEffectorTask} */
    protected final void startInLocation(MachineLocation machine) {}

    /** @deprecated since 0.6.0 use/override method in {@link SoftwareProcessDriverStartEffectorTask} */
    protected final void callStartHooks() {}
    
    protected Map<String,Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        Map<String,Object> result = Maps.newLinkedHashMap(location.getProvisioningFlags(ImmutableList.of(getClass().getName())));
        result.putAll(getConfig(PROVISIONING_PROPERTIES));
        if (result.get("inboundPorts") == null) {
            Collection<Integer> ports = getRequiredOpenPorts();
            if (ports != null && ports.size() > 0) result.put("inboundPorts", ports);
        }
        result.put(LocationConfigKeys.CALLER_CONTEXT.getName(), this);
        return result;
    }
    
    /** @deprecated in 0.4.0. use obtainProvisioningFlags. 
     * introduced in a branch which duplicates changes in master where it is called "obtainPF".
     * will remove as soon as those uses are updated. */
    protected final Map<String,Object> getProvisioningFlags(MachineProvisioningLocation location) {
        return obtainProvisioningFlags(location);
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

    public String getLocalHostname() {
        Location where = Iterables.getFirst(getLocations(), null);
	    String hostname = null;
        if (where instanceof HasSubnetHostname) {
            hostname = ((HasSubnetHostname) where).getSubnetHostname();
        }
        if (hostname == null && where instanceof MachineLocation) {
            InetAddress addr = ((MachineLocation) where).getAddress();
            if (addr != null) hostname = addr.getHostAddress();
        }
        log.debug("computed hostname {} for {}", hostname, this);
        if (hostname == null)
            throw new IllegalStateException("Cannot find hostname for "+this+" at location "+where);
        return hostname;
	}

    void initDriver(MachineLocation machine) {
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
		long startTime = System.currentTimeMillis();
		long waitTime = startTime + 75000; // FIXME magic number; should be config key with default value?
		boolean isRunningResult = false;
		while (!isRunningResult && System.currentTimeMillis() < waitTime) {
		    Time.sleep(1000); // FIXME magic number; should be config key with default value?
            try {
                isRunningResult = driver.isRunning();
            } catch (Exception  e) {
                setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
                // provide extra context info, as we're seeing this happen in strange circumstances
                if (driver==null) throw new IllegalStateException(this+" concurrent start and shutdown detected");
                throw new IllegalStateException("Error detecting whether "+this+" is running: "+e, e);
            }
			if (log.isDebugEnabled()) log.debug("checked {}, is running returned: {}", this, isRunningResult);
		}
		if (!isRunningResult) {
            String msg = "Software process entity "+this+" did not appear to start within "+
                    Time.makeTimeString(System.currentTimeMillis()-startTime)+
                    "; setting state to indicate problem and throwing; consult logs for more details";
            log.warn(msg);
			setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
            throw new IllegalStateException(msg);
		}
	}

	public void stop() {
	    // TODO There is a race where we set SERVICE_UP=false while sensor-adapter threads may still be polling.
        // The other thread might reset SERVICE_UP to true immediately after we set it to false here.
        // Deactivating adapters before setting SERVICE_UP reduces the race, and it is reduced further by setting
        // SERVICE_UP to false at the end of stop as well.
	    
	    // Perhaps we should wait until all feeds have completed here, 
	    // or do a SERVICE_STATE check before setting SERVICE_UP to true in a feed (?).
        
        invoke(STOP, MutableMap.<String,Object>of()).getUnchecked();
	}

    public void restart() {
        invoke(RESTART, MutableMap.<String,Object>of()).getUnchecked();
    }
}
