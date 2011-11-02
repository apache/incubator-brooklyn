package brooklyn.entity.basic

import java.io.File
import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.basic.lifecycle.StartStopHelper
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
    public static final Logger log = LoggerFactory.getLogger(SoftwareProcessEntity.class)

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
	
    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = [ Lifecycle, "service.state", "Service lifecycle state" ]

    private MachineProvisioningLocation provisioningLoc
    protected StartStopHelper setup
    protected transient SensorRegistry sensorRegistry
    
    public SoftwareProcessEntity(Map properties=[:], Entity owner=null) {
        super(properties, owner)
 
        setAttribute(SERVICE_UP, false)
        setAttribute(SERVICE_STATE, Lifecycle.CREATED)
    }

    public abstract StartStopHelper getSshBasedSetup(SshMachineLocation loc)

    protected void postStart() {
		connectSensors() 
	}
    protected void connectSensors() {
		
		//publish attributes for all config attribute sensors
		//TODO should only be applied at initial deployment
		sensorRegistry.register(new ConfigSensorAdapter())
		
    }
	
    protected void preStop() { }
    
	
    public void start(Collection<Location> locations) {
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
        // XXX port setup is fundamentally broken, I believe...
        // flags.inboundPorts = getRequiredOpenPorts()
        
        provisioningLoc = location
        SshMachineLocation machine = location.obtain(flags)
        if (machine == null) throw new NoMachinesAvailableException(location)
        startInLocation(machine)
    }
    
    protected Collection<Integer> getRequiredOpenPorts() {
        return [22]
    }
    
    public void startInLocation(SshMachineLocation machine) {
        locations.add(machine)

        setAttribute(HOSTNAME, machine.address.hostName)
        setAttribute(ADDRESS, machine.address.hostAddress)

        setup = getSshBasedSetup(machine)
        if (setup) {
            setup.start()
	        waitForEntityStart()
        } else {
            throw new UnsupportedOperationException("cannot start ${this} on ${machine}: no setup class found");
        }
    }

    // TODO Find a better way to detect early death of process.
    public void waitForEntityStart() throws IllegalStateException {
        log.debug "waiting to ensure $this doesn't abort prematurely"
        long startTime = System.currentTimeMillis()
        long waitTime = startTime + 75000 // FIXME magic number; should be config key with default value?
        boolean isRunningResult = false;
        while (!isRunningResult && System.currentTimeMillis() < waitTime) {
            Thread.sleep 3000 // FIXME magic number; should be config key with default value?
            isRunningResult = setup.isRunning()
            log.debug "checked $this, running result $isRunningResult"
        }
        if (!isRunningResult) {
            setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE)
        }
    }

    public void stop() {
        setAttribute(SERVICE_STATE, Lifecycle.STOPPING)
		if (sensorRegistry!=null) sensorRegistry.deactivateAdapters();
		preStop()
        MachineLocation machine = removeFirstMatchingLocation({ it in MachineLocation })
        if (machine) {
            stopInLocation(machine)
        }
        setAttribute(SERVICE_STATE, Lifecycle.STOPPED)
        setAttribute(SERVICE_UP, false)
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
        if (setup) setup.stop()
        
        // Only release this machine if we ourselves provisioned it (e.g. it might be running other services)
        provisioningLoc?.release(machine)
		
		setup = null;
    }

    public void restart() {
        if (setup) setup.restart()
        else throw new IllegalStateException("entity "+this+" not set up for operations (restart)")
    }

    public File copy(String file) {
        return copy(new File(file))
    }

    public File copy(File file) {
        return setup.copy(file)
    }
    
    public void deploy(String file) {
        deploy(new File(file))
    }
    
    public void deploy(File file, File target=null) {
        setup.deploy(file, target)
    }
}
