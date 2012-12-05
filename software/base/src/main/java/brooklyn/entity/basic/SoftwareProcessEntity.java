package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;
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
import brooklyn.entity.Entity;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.adapter.ConfigSensorAdapter;
import brooklyn.event.adapter.SensorRegistry;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.MutableSet;
import brooklyn.util.Time;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.internal.Repeater;
import brooklyn.util.task.Tasks;

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
public abstract class SoftwareProcessEntity extends AbstractEntity implements Startable, DriverDependentEntity {
	private static final Logger log = LoggerFactory.getLogger(SoftwareProcessEntity.class);
    
    @SetFromFlag("startLatch")
    public static final ConfigKey<Boolean> START_LATCH = ConfigKeys.START_LATCH;
    
    @SetFromFlag("installLatch")
    public static final ConfigKey<Boolean> INSTALL_LATCH = ConfigKeys.INSTALL_LATCH;
    
    @SetFromFlag("customizeLatch")
    public static final ConfigKey<Boolean> CUSTOMIZE_LATCH = ConfigKeys.CUSTOMIZE_LATCH;
    
    @SetFromFlag("launchLatch")
    public static final ConfigKey<Boolean> LAUNCH_LATCH = ConfigKeys.LAUNCH_LATCH;
    
	@SetFromFlag("version")
	public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION;
	
    @SetFromFlag("installDir")
	public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR;
	
    @SetFromFlag("runDir")
	public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR;

	@SetFromFlag("env")
	public static final ConfigKey<Map> SHELL_ENVIRONMENT = new BasicConfigKey(
	        Map.class, "shell.env", "Map of environment variables to pass to the runtime shell", MutableMap.of());

    @SetFromFlag("provisioningProperties")
    public static final ConfigKey<Map<String,Object>> PROVISIONING_PROPERTIES = new BasicConfigKey(
            Map.class, "provisioning.properties", 
            "Custom properties to be passed in when provisioning a new machine", MutableMap.of());
    
	public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
	public static final AttributeSensor<String> ADDRESS = Attributes.ADDRESS;

    public static final AttributeSensor<MachineProvisioningLocation> PROVISIONING_LOCATION = new BasicAttributeSensor<MachineProvisioningLocation>(
            MachineProvisioningLocation.class, "softwareservice.provisioningLocation", "Location used to provision a machine where this is running");
        
	public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
	
	private transient SoftwareProcessDriver driver;
	protected transient SensorRegistry sensorRegistry;

	public SoftwareProcessEntity() {
        super(MutableMap.of(), null);
    }
    public SoftwareProcessEntity(Entity owner) {
        this(MutableMap.of(), owner);
    }
    public SoftwareProcessEntity(Map properties) {
        this(properties, null);
    }
	public SoftwareProcessEntity(Map properties, Entity owner) {
		super(properties, owner);
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
        return getManagementContext().getEntityDriverFactory().build(this,(Location)loc);
    }

    protected MachineLocation getMachineOrNull() {
        return Iterables.get(Iterables.filter(getLocations(), MachineLocation.class), 0, null);
    }
    
  	/**
  	 * Called before driver.start; guarantees the driver will exist, locations will have been set
  	 * and sensorRegistry will be set (but not yet activated).
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
    }

    /**
     * Called after this entity is fully rebound (i.e. it is fully managed).
     */
    protected void postRebind() {
    }
    
    protected void callStartHooks() {
        preStart();
        driver.start();
        postDriverStart();
        connectSensors();
        waitForServiceUp();
        postStart();
    }
    
    protected void callRebindHooks() {
        connectSensors();
        waitForServiceUp();
    }

    @Override 
    public void onManagementStarting() {
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
        
        if (sensorRegistry == null) sensorRegistry = new SensorRegistry(this);
        
        callRebindHooks();
    }
    
    public void waitForServiceUp() {
        waitForServiceUp(60, TimeUnit.SECONDS);
    }
    public void waitForServiceUp(TimeDuration duration) {
        waitForServiceUp(duration.toMilliseconds(), TimeUnit.MILLISECONDS);
    }
    public void waitForServiceUp(long duration, TimeUnit units) {
        if (!Repeater.create(ImmutableMap.of("timeout", units.toMillis(duration), "description", "Waiting for SERVICE_UP on "+this))
                .rethrowException().repeat().every(1, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    public Boolean call() {
                        return getAttribute(SERVICE_UP);
                    }})
                .run()) {
            throw new IllegalStateException("Timeout waiting for SERVICE_UP from "+this);
        }
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
	public void start(Collection<? extends Location> locations) {
        checkNotNull(locations, "locations");
		setAttribute(SERVICE_STATE, Lifecycle.STARTING);
        try {
    		startInLocation(locations);
    		
    		if (getAttribute(SERVICE_STATE) == Lifecycle.STARTING) 
                setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
        } catch (Throwable t) {
            setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
	}

    protected void startInLocation(Collection<? extends Location> locations) {
        if (locations.isEmpty()) locations = getLocations();
        if (locations.size() != 1 || Iterables.getOnlyElement(locations)==null)
            throw new IllegalArgumentException("Expected one non-null location when starting "+this+", but given "+locations);
            
        startInLocation( Iterables.getOnlyElement(locations) );
    }

    protected void startInLocation(Location location) {
        if (location instanceof MachineProvisioningLocation) {
            startInLocation((MachineProvisioningLocation<? extends MachineLocation>)location);
        } else if (location instanceof MachineLocation) {
            startInLocation((MachineLocation)location);
        } else {
            throw new IllegalArgumentException("Unsupported location "+location+", when starting "+this);
        }
    }

    protected Map<String,Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        Map<String,Object> result = Maps.newLinkedHashMap(location.getProvisioningFlags(ImmutableList.of(getClass().getName())));
        result.putAll(getConfig(PROVISIONING_PROPERTIES));
        if (result.get("inboundPorts") == null) {
            Collection<Integer> ports = getRequiredOpenPorts();
            if (ports != null && ports.size() > 0) result.put("inboundPorts", ports);
        }
        result.put("callerContext", ""+this);
        return result;
    }
    
    /** @deprecated in 0.4.0. use obtainProvisioningFlags. 
     * introduced in a branch which duplicates changes in master where it is called "obtainPF".
     * will remove as soon as those uses are updated. */
    protected Map<String,Object> getProvisioningFlags(MachineProvisioningLocation location) {
        return obtainProvisioningFlags(location);
    }
    
	protected void startInLocation(final MachineProvisioningLocation<?> location) {
		final Map<String,Object> flags = obtainProvisioningFlags(location);
        if (!(location instanceof LocalhostMachineProvisioningLocation))
            log.info("Starting {}, obtaining a new location instance in {} with ports {}", new Object[] {this, location, flags.get("inboundPorts")});
		setAttribute(PROVISIONING_LOCATION, location);
        MachineLocation machine;
        try {
            machine = Tasks.withBlockingDetails("Provisioning machine in "+location, new Callable<MachineLocation>() {
                public MachineLocation call() throws NoMachinesAvailableException {
                    return location.obtain(flags);
                }});
            if (machine == null) throw new NoMachinesAvailableException(location);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        
		if (log.isDebugEnabled())
		    log.debug("While starting {}, obtained new location instance {}", this, 
		            (machine instanceof SshMachineLocation ? 
		                    machine+", details "+((SshMachineLocation)machine).getUser()+":"+Entities.sanitize(((SshMachineLocation)machine).getConfig()) 
		                    : machine));
        if (!(location instanceof LocalhostMachineProvisioningLocation))
            log.info("While starting {}, obtained a new location instance {}, now preparing process there", this, machine);
        
		startInLocation(machine);
	}

    /** returns the ports that this entity wants to use;
     * default implementation returns 22 plus first value for each PortAttributeSensorAndConfigKey config key PortRange.
     */
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = MutableSet.of(22);
        for (ConfigKey k: getEntityType().getConfigKeys()) {
            if (PortRange.class.isAssignableFrom(k.getType())) {
                PortRange p = getConfig(k);
                if (p != null && !p.isEmpty()) ports.add(p.iterator().next());
            }
        }
        log.debug("getRequiredOpenPorts detected default {} for {}", ports, this);
        return ports;
    }

    public String getLocalHostname() {
        Location where = Iterables.getFirst(getLocations(), null);
	    String hostname = null;
        if (where instanceof JcloudsSshMachineLocation) {
            hostname = ((JcloudsSshMachineLocation) where).getSubnetHostname();
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

    protected void startInLocation(MachineLocation machine) {
        log.info("Starting {} on machine {}", this, machine);
        addLocations(ImmutableList.of((Location)machine));
        
        initDriver(machine);
        
        // Note: must only apply config-sensors after adding to locations and creating driver; 
        // otherwise can't do things like acquire free port from location, or allowing driver to set up ports
        if (sensorRegistry == null) sensorRegistry = new SensorRegistry(this);
        ConfigSensorAdapter.apply(this);
        
		setAttribute(HOSTNAME, machine.getAddress().getHostName());
		setAttribute(ADDRESS, machine.getAddress().getHostAddress());

        // Opportunity to block startup until other dependent components are available
        Object val = getConfig(START_LATCH);
        if (val != null) log.debug("{} finished waiting for start-latch; continuing...", this, val);

        callStartHooks();
	}

    private void initDriver(MachineLocation machine) {
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
        
        if (getAttribute(SERVICE_STATE)==Lifecycle.STOPPED) {
            log.warn("Skipping stop of software process entity "+this+" when already stopped");
            return;
        }
        log.info("Stopping {} in {}", this, getLocations());
		setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
		if (sensorRegistry!=null) sensorRegistry.deactivateAdapters();
        setAttribute(SERVICE_UP, false);
		preStop();
		MachineLocation machine = removeFirstMachineLocation();
		if (machine != null) {
			stopInLocation(machine);
		}
        setAttribute(SERVICE_UP, false);
		setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
        if (log.isDebugEnabled()) log.debug("Stopped software process entity "+this);
	}

	private MachineLocation removeFirstMachineLocation() {
	    for (Location loc : getLocations()) {
	        if (loc instanceof MachineLocation) {
	            removeLocations(ImmutableList.of(loc));
	            return (MachineLocation) loc;
	        }
	    }
	    return null;
	}

    public void stopInLocation(MachineLocation machine) {
        MachineProvisioningLocation provisioner = getAttribute(PROVISIONING_LOCATION);
        Throwable err = null;
        
        try {
            if (sensorRegistry != null) sensorRegistry.close();
            if (driver != null) driver.stop();
            
        } catch (Throwable t) {
            LOG.warn("Error stopping "+this+" on machine "+machine+
                    (provisioner != null ? ", releasing machine and rethrowing" : ", rethrowing"), 
                    t);
            err = t;
            
        } finally {
            // Release this machine (even if error trying to stop it)
            // Only release this machine if we ourselves provisioned it (e.g. it might be running other services)
            try {
                if (provisioner != null) provisioner.release(machine);
            } catch (Throwable t) {
                if (err != null) {
                    LOG.warn("Error releasing machine "+machine+" while stopping "+this+"; rethrowing earlier exception", t);
                } else {
                    LOG.warn("Error releasing machine "+machine+" while stopping "+this+"; rethrowing ("+t+")");
                    err = t;
                }
            }
            driver = null;
        }
        
        if (err != null) throw Exceptions.propagate(err);
    }

    public void restart() {
        if (driver == null) throw new IllegalStateException("entity "+this+" not set up for operations (restart)");
        
        driver.restart();
        postDriverRestart();
        setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
    }
}
