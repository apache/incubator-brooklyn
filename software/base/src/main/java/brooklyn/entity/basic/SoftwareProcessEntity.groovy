package brooklyn.entity.basic

import java.util.Collection
import java.util.Map
import java.util.Set

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
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

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

	public StartStopDriver getDriver() { driverLocal }
	@Deprecated /** refer to driver instead */
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

	protected void preStop() { }

    @Override
	public void start(Collection<? extends Location> locations) {
        log.info("Starting software process entity "+this+" at "+locations);
		setAttribute(SERVICE_STATE, Lifecycle.STARTING)
		if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)

		startInLocation locations
		postStart()
		sensorRegistry.activateAdapters()
		setAttribute(SERVICE_STATE, Lifecycle.RUNNING)
	}

	public void startInLocation(Collection<Location> locations) {
		Preconditions.checkArgument locations.size() == 1
		Location location = Iterables.getOnlyElement(locations)
		startInLocation(location)
	}

	public void startInLocation(MachineProvisioningLocation location) {
		Map<String,Object> flags = location.getProvisioningFlags([ getClass().getName() ])
        if (!flags.inboundPorts) {
            def ports = getRequiredOpenPorts();
            if (ports) flags.inboundPorts = getRequiredOpenPorts()
        }
        LOG.info("SoftwareProcessEntity {} obtaining a new location instance in {} with ports {}", this, location, flags.inboundPorts)

		provisioningLoc = location
		SshMachineLocation machine = location.obtain(flags)
		if (machine == null) throw new NoMachinesAvailableException(location)
        LOG.info("SoftwareProcessEntity {} obtained a new location instance {}, now preparing process there", this, machine)
		startInLocation(machine)
	}

    /** returns the ports that this entity wants to use;
     * default implementation returns 22 plus first value for each PortAttributeSensorAndConfigKey config key PortRange.
     */
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = [22]
        for (ConfigKey k: getConfigKeys().values()) {
            if (PortRange.class.isAssignableFrom(k.getType())) {
                PortRange p = getConfig(k);
                if (!p?.isEmpty()) ports += p.iterator().next()
            }
        }
        log.debug("getRequiredOpenPorts detected ${ports} for ${this}")
        ports
    }


    public void startInLocation(SshMachineLocation machine) {
        locations.add(machine)

        // Note: must only apply config-sensors after adding to locations; otherwise can't do things like acquire free port from location
        // TODO use different method naming/overriding pattern, so as we have more things than SshMachineLocation they all still get called?
        ConfigSensorAdapter.apply(this);
        
		setAttribute(HOSTNAME, machine.address.hostName)
		setAttribute(ADDRESS, machine.address.hostAddress)

        // Opportunity to block startup until other dependent components are available
        Object val = getConfig(START_LATCH)
        if (val != null) LOG.debug("{} finished waiting for start-latch; continuing...", this, val)
        
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
		if (driver) {
            preStart();
			driver.start()
			waitForEntityStart()
		} else {
			throw new UnsupportedOperationException("cannot start ${this} on ${machine}: no setup class found");
		}
	}

	// TODO Find a better way to detect early death of process.
	public void waitForEntityStart() throws IllegalStateException {
		if (log.isDebugEnabled()) log.debug "waiting to ensure $this doesn't abort prematurely"
		long startTime = System.currentTimeMillis()
		long waitTime = startTime + 75000 // FIXME magic number; should be config key with default value?
		boolean isRunningResult = false;
		while (!isRunningResult && System.currentTimeMillis() < waitTime) {
			isRunningResult = driver.isRunning()
			if (log.isDebugEnabled()) log.debug "checked {}, is running returned: {}", this, isRunningResult
			Thread.sleep 1000 // FIXME magic number; should be config key with default value?
		}
		if (!isRunningResult) {
			setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE)
		}
	}

	public void stop() {
        log.info("Stopping software process entity "+this);
		setAttribute(SERVICE_STATE, Lifecycle.STOPPING)
        setAttribute(SERVICE_UP, false)
		if (sensorRegistry!=null) sensorRegistry.deactivateAdapters();
		preStop()
		MachineLocation machine = removeFirstMatchingLocation({ it in MachineLocation })
		if (machine) {
			stopInLocation(machine)
		}
		setAttribute(SERVICE_STATE, Lifecycle.STOPPED)
        if (log.isDebugEnabled()) log.debug("Stopped software process entity "+this);
	}

	Location removeFirstMatchingLocation(Closure matcher) {
		synchronized (locations) {
			Location loc = locations.find(matcher)
			if (loc) locations.remove(loc)
			return loc
		}
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
	}

}

public interface UsesJava {
	public static final BasicConfigKey<Map<String, String>> JAVA_OPTIONS = [ Map, "java.options", "Java command line options", [:] ]
}

public interface UsesJmx extends UsesJava {
	public static final int DEFAULT_JMX_PORT = 1099
	@SetFromFlag("jmxPort")
	public static final PortAttributeSensorAndConfigKey JMX_PORT = Attributes.JMX_PORT
	@SetFromFlag("rmiPort")
	public static final PortAttributeSensorAndConfigKey RMI_PORT = Attributes.RMI_PORT
	@SetFromFlag("jmxContext")
	public static final BasicAttributeSensorAndConfigKey<String> JMX_CONTEXT = Attributes.JMX_CONTEXT
	
	public static final BasicAttributeSensor<String> JMX_URL = [ String, "jmx.url", "JMX URL" ]
}

public interface UsesJavaMXBeans {
    
    @SetFromFlag("mxbeanStatsEnabled")
    public static final BasicConfigKey<Boolean> MXBEAN_STATS_ENABLED = [ Boolean, "java.metrics.mxbeanStatsEnabled", "Enables collection of JVM stats from the MXBeans, such as memory and thread usage (default is true)", true ]
    
    public static final BasicAttributeSensor<Long> USED_HEAP_MEMORY = [ Long, "java.metrics.heap.used", "current heap size in bytes" ]
    public static final BasicAttributeSensor<Long> INIT_HEAP_MEMORY = [ Long, "java.metrics.heap.init", "initial heap size in bytes" ]
    public static final BasicAttributeSensor<Long> COMMITTED_HEAP_MEMORY = [ Long, "java.metrics.heap.committed", "commited heap size in bytes" ]
    public static final BasicAttributeSensor<Long> MAX_HEAP_MEMORY = [ Long, "java.metrics.heap.max", "max heap size in bytes" ]
    public static final BasicAttributeSensor<Long> NON_HEAP_MEMORY_USAGE = [ Long, "java.metrics.nonheap.used", "current non-heap size in bytes" ]
    public static final BasicAttributeSensor<Long> CURRENT_THREAD_COUNT = [ Integer, "java.metrics.threads.current", "current number of threads" ]
    public static final BasicAttributeSensor<Long> PEAK_THREAD_COUNT = [ Integer, "java.metrics.threads.max", "peak number of threads" ]
    public static final BasicAttributeSensor<Long> START_TIME = [ Long, "java.metrics.starttime", "start time" ]
    public static final BasicAttributeSensor<Double> SYSTEM_LOAD_AVERAGE = [ Double, "java.metrics.systemload.average", "average system load" ]
    public static final BasicAttributeSensor<Integer> AVAILABLE_PROCESSORS = [ Integer, "java.metrics.processors.available", "number of processors available to the Java virtual machine" ]
    public static final BasicAttributeSensor<Long> GARBAGE_COLLECTION_TIME = [ Map, "java.metrics.gc.time", "garbage collection time" ]
}
