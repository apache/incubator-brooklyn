package brooklyn.entity.basic

import groovy.time.Duration;

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.basic.lifecycle.StartStopDriver
import brooklyn.entity.basic.lifecycle.StartStopSshDriver
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.ConfigSensorAdapter
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.PortRange
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.Repeater

import com.google.common.base.Preconditions
import com.google.common.base.Predicate
import com.google.common.collect.Iterables
import com.google.common.collect.Maps
import groovy.time.TimeDuration

/**
 * An {@link Entity} representing a piece of software which can be installed, run, and controlled.
 * A single such entity can only run on a single {@link MachineLocation} at a time (you can have multiple on the machine). 
 * It typically takes config keys for suggested versions, filesystem locations to use, and environment variables to set.
 * <p>
 * It exposes sensors for service state (Lifecycle) and status (String), and for host info, log file location.
 */
public abstract class SoftwareProcessEntity extends AbstractEntity implements Startable {
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

	public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE
	
	private MachineProvisioningLocation provisioningLoc
	private StartStopDriver driverLocal
	protected transient SensorRegistry sensorRegistry

	public SoftwareProcessEntity(Map properties=[:], Entity owner=null) {
		super(properties, owner)

		setAttribute(SERVICE_UP, false)
		setAttribute(SERVICE_STATE, Lifecycle.CREATED)
	}

    protected void setProvisioningLocation(MachineProvisioningLocation val) {
        if (provisioningLoc) throw new IllegalStateException("Cannot change provisioning location: existing="+provisioningLoc+"; new="+val)
        provisioningLoc = val
    }
    
    protected MachineProvisioningLocation getProvisioningLocation() {
        return provisioningLoc
    }
    
	public StartStopDriver getDriver() { driverLocal }
    /**
     * @deprecated will be deleted in 0.5. Refer to driver instead
     */
    @Deprecated
	public StartStopDriver getSetup() { driver }
	
	protected abstract StartStopDriver newDriver(SshMachineLocation loc);

    protected void preStart() {
        if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)
        ConfigSensorAdapter.apply(this);
    }
    
	protected void postStart() {
		connectSensors()
		checkAllSensorsConnected()
	}
	
    protected void postActivation() {
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
        log.debug("Detected SERVICE_UP for software ${this}")
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
        log.info("Starting software process entity "+this+" at "+locations);
		setAttribute(SERVICE_STATE, Lifecycle.STARTING)
		if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)

		startInLocation locations
		postStart()
		sensorRegistry.activateAdapters()
        postActivation()
		if (getAttribute(SERVICE_STATE) == Lifecycle.STARTING) 
            setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
	}

	public void startInLocation(Collection<Location> locations) {
		Preconditions.checkArgument locations.size() == 1
		Location location = Iterables.getOnlyElement(locations)
		startInLocation(location)
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
            LOG.info("SoftwareProcessEntity {} obtaining a new location instance in {} with ports {}", this, location, flags.inboundPorts)
		provisioningLoc = location
		SshMachineLocation machine = location.obtain(flags)
		if (machine == null) throw new NoMachinesAvailableException(location)
        if (!(location in LocalhostMachineProvisioningLocation))
            LOG.info("SoftwareProcessEntity {} obtained a new location instance {}, now preparing process there", this, machine)
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
        log.debug("getRequiredOpenPorts detected default ${ports} for ${this}")
        ports
    }

    public String getLocalHostname() {
        Location where = Iterables.getFirst(locations, null)
	    String hostname = null
        if (where in JcloudsSshMachineLocation)
            hostname = ((JcloudsSshMachineLocation) where).getSubnetHostname()
        if (!hostname && where in SshMachineLocation)
            hostname = ((SshMachineLocation) where).getAddress()?.hostAddress
        log.debug("computed hostname ${hostname} for ${this}")
        if (!hostname)
            throw new IllegalStateException("Cannot find hostname for ${this} at location ${where}")
        return hostname
	}

    public void startInLocation(SshMachineLocation machine) {
        locations.add(machine)
        initDriver(machine)
        
        // Note: must only apply config-sensors after adding to locations and creating driver; 
        // otherwise can't do things like acquire free port from location, or allowing driver to set up ports
        // TODO use different method naming/overriding pattern, so as we have more things than SshMachineLocation they all still get called?
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

    protected void initDriver(SshMachineLocation machine) {
        if (driver!=null) {
            if ((driver in StartStopSshDriver) && ( ((StartStopSshDriver)driver).location==machine)) {
                //just reuse
            } else {
                log.warn("driver/location change for {} is untested: cannot start ${this} on ${machine}: driver already created");
                driverLocal = newDriver(machine)
            }
        } else {
            driverLocal = newDriver(machine)
        }
    }
    
	// TODO Find a better way to detect early death of process.
	public void waitForEntityStart() throws IllegalStateException {
		if (log.isDebugEnabled()) log.debug "waiting to ensure $this doesn't abort prematurely"
		long startTime = System.currentTimeMillis()
		long waitTime = startTime + 75000 // FIXME magic number; should be config key with default value?
		boolean isRunningResult = false;
		while (!isRunningResult && System.currentTimeMillis() < waitTime) {
		    Thread.sleep 1000 // FIXME magic number; should be config key with default value?
			isRunningResult = driver.isRunning()
			if (log.isDebugEnabled()) log.debug "checked {}, is running returned: {}", this, isRunningResult
		}
		if (!isRunningResult) {
            log.warn("Software process entity ${this} did not appear to start; setting state to indicate problem; consult logs for more details")
			setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE)
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
        log.info("Stopping software process entity "+this);
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
		provisioningLoc?.release(machine)

		driverLocal = null;
	}

	public void restart() {
		if (driver) driver.restart()
		else throw new IllegalStateException("entity "+this+" not set up for operations (restart)")
        //if successfully restarts
        setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
	}

}




