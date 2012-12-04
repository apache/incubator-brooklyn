package brooklyn.entity.basic

import static com.google.common.base.Preconditions.checkNotNull
import groovy.time.TimeDuration

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.drivers.DriverDependentEntity
import brooklyn.entity.rebind.RebindSupport
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.PortRange
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation
import brooklyn.mementos.EntityMemento
import brooklyn.util.MutableMap
import brooklyn.util.Time
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.Repeater
import brooklyn.util.task.Tasks

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.collect.Maps

/**
 * An {@link Entity} representing a piece of software which can be installed, run, and controlled.
 * A single such entity can only run on a single {@link MachineLocation} at a time (you can have multiple on the machine). 
 * It typically takes config keys for suggested versions, filesystem locations to use, and environment variables to set.
 * <p>
 * It exposes sensors for service state (Lifecycle) and status (String), and for host info, log file location.
 */
public abstract class SoftwareProcessEntity extends AbstractEntity implements Startable, DriverDependentEntity {
	private static final Logger log = LoggerFactory.getLogger(SoftwareProcessEntity.class)
    
    @SetFromFlag("startLatch")
    public static final ConfigKey<String> START_LATCH = ConfigKeys.START_LATCH
    
    @SetFromFlag("installLatch")
    public static final ConfigKey<String> INSTALL_LATCH = ConfigKeys.INSTALL_LATCH
    
    @SetFromFlag("customizeLatch")
    public static final ConfigKey<String> CUSTOMIZE_LATCH = ConfigKeys.CUSTOMIZE_LATCH
    
    @SetFromFlag("launchLatch")
    public static final ConfigKey<String> LAUNCH_LATCH = ConfigKeys.LAUNCH_LATCH
    
	@SetFromFlag("version")
	public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION
	
    @SetFromFlag("installDir")
	public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR
	
    @SetFromFlag("runDir")
	public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR

	@SetFromFlag("env")
	public static final BasicConfigKey<Map> SHELL_ENVIRONMENT = [ Map, "shell.env", "Map of environment variables to pass to the runtime shell", [:] ]

    @SetFromFlag("provisioningProperties")
    public static final BasicConfigKey<Map<String,Object>> PROVISIONING_PROPERTIES = [ Map, "provisioning.properties", 
            "Custom properties to be passed in when provisioning a new machine", [:] ]
    
	public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME
	public static final AttributeSensor<String> ADDRESS = Attributes.ADDRESS

    public static final AttributeSensor<MachineProvisioningLocation> PROVISIONING_LOCATION = new BasicAttributeSensor<MachineProvisioningLocation>(
            MachineProvisioningLocation.class, "softwareservice.provisioningLocation", "Location used to provision a machine where this is running");
        
	public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE
	
	private SoftwareProcessDriver driver
	protected transient SensorRegistry sensorRegistry

	public SoftwareProcessEntity(Map properties=[:], Entity owner=null) {
		super(properties, owner)
	}
    
    public SoftwareProcessEntity(Entity owner) {
        this(new MutableMap(), owner);
    }

    protected void setProvisioningLocation(MachineProvisioningLocation val) {
        if (getAttribute(PROVISIONING_LOCATION) != null) throw new IllegalStateException("Cannot change provisioning location: existing="+getAttribute(PROVISIONING_LOCATION)+"; new="+val)
        setAttribute(PROVISIONING_LOCATION, val);
    }
    
    protected MachineProvisioningLocation getProvisioningLocation() {
        return getAttribute(PROVISIONING_LOCATION);
    }
    
	public SoftwareProcessDriver getDriver() { driver }

  	protected SoftwareProcessDriver newDriver(MachineLocation loc){
        return getManagementContext().getEntityDriverFactory().build(this,loc);
    }

    protected void preStart() {
        if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)
        ConfigSensorAdapter.apply(this);
    }
    
	protected void postStart() {
		connectSensors()
		checkAllSensorsConnected()
	}
	
    protected void postActivation() {
        waitForServiceUp();
    }
    
    protected void postRestart() {
        waitForServiceUp();
    }
    
    // TODO Only do this when first being managed; not when moving
    @Override 
    public void onManagementStarting() {
        Lifecycle state = getAttribute(SERVICE_STATE);
        if (state == Lifecycle.RUNNING) {
            rebind();
        } else if (state != null && state != Lifecycle.CREATED) {
            LOG.warn("On start-up of {}, not (re)binding because state is {}", this, state);
    	} else {
            // Expect this is a normal start() sequence (i.e. start() will subsequently be called)
            setAttribute(SERVICE_UP, false)
            setAttribute(SERVICE_STATE, Lifecycle.CREATED)
    	}
    }
	
    @Override 
    public void onManagementStarted() {
        if (getAttribute(SERVICE_STATE) == Lifecycle.RUNNING) {
            postRebind();
        }
    }
    
    protected void rebind() {
        // e.g. rebinding to a running instance
        // FIXME For rebind, what to do about things in STARTING or STOPPING state?
        // FIXME What if location not set?
        LOG.info("Connecting to pre-running service: {}", this);
        
        Iterable<MachineLocation> machineLocations = Iterables.filter(getLocations(), Predicates.instanceOf(MachineLocation.class));
        if (!Iterables.isEmpty(machineLocations)) {
            initDriver(Iterables.get(machineLocations, 0));
            driver.rebind();
            if (LOG.isDebugEnabled()) LOG.debug("On rebind of {}, re-created driver {}", this, driver);
        } else {
            LOG.info("On rebind of {}, no MachineLocation found (with locations {}) so not generating driver",
                    this, getLocations());
        }
        
        if (!sensorRegistry) sensorRegistry = new SensorRegistry(this);
        postStart();
        sensorRegistry.activateAdapters();
    }
    
    protected void postRebind() {
        postActivation();
    }
    
	/** lifecycle message for connecting sensors to registry;
	 * typically overridden by subclasses */
	protected void connectSensors() {
	}

	protected void checkAllSensorsConnected() {
		//TODO warn if we don't have sensors wired up to something
		/*
				what about sensors where it doesn't necessarily make sense to register them here, e.g.:
				  config -- easy to exclude (by type)
				  lifecycle, member added -- ??
				  enriched sensors -- could add the policies here
		
				proposed solution:
				  - ignore if registered
				  - ignore is has a value
				  - ignore if manually excluded (registered with "manual provider"), e.g.
   						sensorRegistry.register(ManualSensorAdaptor).register(SOME_MANUAL_SENSOR)
				  
				those which are updated by a policy need to get recorded somehow
		*/
	}
    
    public void waitForServiceUp() {
        waitForServiceUp(60*TimeUnit.SECONDS)
    }
    public void waitForServiceUp(TimeDuration duration) {
        if (!Repeater.create(timeout:duration, description:"Waiting for SERVICE_UP on ${this}")
                .rethrowException().repeat().every(1*TimeUnit.SECONDS)
                .until() {
                    getAttribute(SERVICE_UP)
                }
                .run()) {
            throw new IllegalStateException("Timeout waiting for SERVICE_UP from ${this}");
        }
        log.debug("Detected SERVICE_UP for software {}", this)
    }

    public void checkModifiable() {
        def state = getAttribute(SERVICE_STATE);
        if (getAttribute(SERVICE_STATE) == Lifecycle.RUNNING) return;
        if (getAttribute(SERVICE_STATE) == Lifecycle.STARTING) return;
        // TODO this check may be redundant or even inappropriate
        throw new IllegalStateException("Cannot configure entity ${this} in state ${state}")
    }

	protected void preStop() { }

    @Override
	public void start(Collection<? extends Location> locations) {
        checkNotNull(locations, "locations");
		setAttribute(SERVICE_STATE, Lifecycle.STARTING)
        try {
    		if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)
            
    		startInLocation locations
    		postStart()
    		sensorRegistry.activateAdapters()
            postActivation()
    		if (getAttribute(SERVICE_STATE) == Lifecycle.STARTING) 
                setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
        } catch (Throwable t) {
            setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
	}

    public void startInLocation(Collection<Location> locations) {
        if (locations.isEmpty()) locations = this.locations;
        if (locations.size() != 1 || Iterables.getOnlyElement(locations)==null)
            throw new IllegalArgumentException("Expected one non-null location when starting "+this+", but given "+locations);
        startInLocation( Iterables.getOnlyElement(locations) )
    }

    protected Map<String,Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        Map result = Maps.newLinkedHashMap(location.getProvisioningFlags([ getClass().getName() ]));
        result.putAll(getConfig(PROVISIONING_PROPERTIES));
        if (!result.inboundPorts) {
            def ports = getRequiredOpenPorts();
            if (ports) result.inboundPorts = getRequiredOpenPorts()
        }
        result.callerContext = ""+this;
        return result;
    }
    
    /** @deprecated in 0.4.0. use obtainPF. 
     * introduced in a branch which duplicates changes in master where it is called "obtainPF".
     * will remove as soon as those uses are updated. */
    protected Map<String,Object> getProvisioningFlags(MachineProvisioningLocation location) {
        return obtainProvisioningFlags(location);
    }
    
	public void startInLocation(MachineProvisioningLocation location) {
		Map<String,Object> flags = obtainProvisioningFlags(location);
        if (!(location in LocalhostMachineProvisioningLocation))
            LOG.info("Starting {}, obtaining a new location instance in {} with ports {}", this, location, flags.inboundPorts)
		setAttribute(PROVISIONING_LOCATION, location);
        MachineLocation machine;
        Tasks.withBlockingDetails("Provisioning machine in "+location) {
            machine = location.obtain(flags);
        }
		if (machine == null) throw new NoMachinesAvailableException(location)
		if (LOG.isDebugEnabled())
		    LOG.debug("While starting {}, obtained new location instance {}, details {}", this, machine,
		            machine.getUser()+":"+Entities.sanitize(machine.getConfig()))
        if (!(location in LocalhostMachineProvisioningLocation))
            LOG.info("While starting {}, obtained a new location instance {}, now preparing process there", this, machine)
		startInLocation(machine)
	}

    /** returns the ports that this entity wants to use;
     * default implementation returns 22 plus first value for each PortAttributeSensorAndConfigKey config key PortRange.
     */
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = [22]
        for (ConfigKey k: getEntityType().getConfigKeys()) {
            if (PortRange.class.isAssignableFrom(k.getType())) {
                PortRange p = getConfig(k);
                if (p != null && !p.isEmpty()) ports += p.iterator().next()
            }
        }
        log.debug("getRequiredOpenPorts detected default {} for {}", ports, this)
        ports
    }

    public String getLocalHostname() {
        Location where = Iterables.getFirst(locations, null)
	    String hostname = null
        if (where in JcloudsSshMachineLocation)
            hostname = ((JcloudsSshMachineLocation) where).getSubnetHostname()
        if (!hostname && where in MachineLocation)
            hostname = ((MachineLocation) where).getAddress()?.hostAddress
        log.debug("computed hostname ${hostname} for ${this}")
        if (!hostname)
            throw new IllegalStateException("Cannot find hostname for ${this} at location ${where}")
        return hostname
	}

    public void startInLocation(MachineLocation machine) {
        log.info("Starting {} on machine {}", this, machine);
        
        addLocations(ImmutableList.of(machine));
        initDriver(machine);
        
        // Note: must only apply config-sensors after adding to locations and creating driver; 
        // otherwise can't do things like acquire free port from location, or allowing driver to set up ports
        ConfigSensorAdapter.apply(this);
        
		setAttribute(HOSTNAME, machine.address.hostName)
		setAttribute(ADDRESS, machine.address.hostAddress)

        // Opportunity to block startup until other dependent components are available
        Object val = getConfig(START_LATCH)
        if (val != null) LOG.debug("{} finished waiting for start-latch; continuing...", this, val)
        
		if (driver) {
            preStart();
			driver.start()
			waitForEntityStart()
		} else {
			throw new UnsupportedOperationException("cannot start ${this} on ${machine}: no setup class found");
		}
	}

    protected void initDriver(MachineLocation machine) {
        if (driver!=null) {
            if ((driver in AbstractSoftwareProcessDriver) && ( ((AbstractSoftwareProcessDriver)driver).location==machine)) {
                //just reuse
            } else {
                log.warn("driver/location change for {} is untested: cannot start ${this} on ${machine}: driver already created");
                driver = newDriver(machine)
            }
        } else {
            driver = newDriver(machine)
        }
    }
    
	// TODO Find a better way to detect early death of process.
	public void waitForEntityStart() {
		if (log.isDebugEnabled()) log.debug "waiting to ensure $this doesn't abort prematurely"
		long startTime = System.currentTimeMillis()
		long waitTime = startTime + 75000 // FIXME magic number; should be config key with default value?
		boolean isRunningResult = false;
		while (!isRunningResult && System.currentTimeMillis() < waitTime) {
		    Thread.sleep 1000 // FIXME magic number; should be config key with default value?
            try {
                isRunningResult = driver.isRunning()
            } catch (Exception  e) {
                setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
                // provide extra context info, as we're seeing this happen in strange circumstances
                if (driver==null) throw new IllegalStateException("${this} concurrent start and shutdown detected");
                throw new IllegalStateException("Error detecting whether ${this} is running: "+e, e);
            }
			if (log.isDebugEnabled()) log.debug "checked {}, is running returned: {}", this, isRunningResult
		}
		if (!isRunningResult) {
            String msg = "Software process entity "+this+" did not appear to start within "+
                    Time.makeTimeString(System.currentTimeMillis()-startTime)+
                    "; setting state to indicate problem and throwing; consult logs for more details";
            log.warn(msg);
			setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE)
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
		setAttribute(SERVICE_STATE, Lifecycle.STOPPING)
		if (sensorRegistry!=null) sensorRegistry.deactivateAdapters();
        setAttribute(SERVICE_UP, false)
		preStop()
		MachineLocation machine = removeFirstMatchingLocation({ it in MachineLocation })
		if (machine) {
			stopInLocation(machine)
		}
        setAttribute(SERVICE_UP, false)
		setAttribute(SERVICE_STATE, Lifecycle.STOPPED)
        if (log.isDebugEnabled()) log.debug("Stopped software process entity "+this);
	}

	Location removeFirstMatchingLocation(Closure matcher) {
        synchronized (locations) {
            Location loc = locations.find(matcher)
            if (loc) locations.remove(loc)
            return loc
        }
        return removeFirstMatchingLocation(matcher as Predicate)
	}

    Location removeFirstMatchingLocation(Predicate<Location> matcher) {
        return removeFirstMatchingLocation({ return matcher.apply(it) })
    }

	public void stopInLocation(MachineLocation machine) {
		if (sensorRegistry) sensorRegistry.close()
		if (driver) driver.stop()

		// Only release this machine if we ourselves provisioned it (e.g. it might be running other services)
		getAttribute(PROVISIONING_LOCATION)?.release(machine)

		driver = null;
	}

	public void restart() {
		if (!driver) throw new IllegalStateException("entity "+this+" not set up for operations (restart)");
        
        driver.restart()
        waitForEntityStart();
        postRestart();
        setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
	}
}
