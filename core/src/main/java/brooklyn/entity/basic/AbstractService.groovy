package brooklyn.entity.basic

import java.io.File
import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

/**
 * An {@link Entity} representing an abstract service process.
 *
 * A service can only run on a single {@link MachineLocation} at a time.
 * It typically takes config keys for suggested versions and filesystem locations, and for the environment variables to set.
 * It exposes sensors for service state (Lifecycle) and status (String), and for host info, log file location.
 * 
 * FIXME not happy with term "service"; it is too general; should this be AbstractProcessEntity ?; 
 * and could we offer conveniences for the pid of what we start?
 */
public abstract class AbstractService extends AbstractEntity implements Startable {
    public static final Logger log = LoggerFactory.getLogger(AbstractService.class)

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION
    @SetFromFlag("installDir")
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR
    @SetFromFlag("runDir")
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR
    public static final BasicConfigKey<Map> ENVIRONMENT = [ Map, "environment", "Map of environment variables to set at runtime", [:] ]

    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME
    public static final AttributeSensor<String> ADDRESS = Attributes.ADDRESS
    public static final AttributeSensor<String> LOG_FILE_LOCATION = Attributes.LOG_FILE_LOCATION

    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = [ Lifecycle, "service.state", "Service lifecycle state" ]
    public static final BasicAttributeSensor<Boolean> SERVICE_CONFIGURED = [ Boolean, "service.isConfigured", "Service configured" ]
    public static final BasicAttributeSensor<String> SERVICE_STATUS = [ String, "service.status", "Service status" ]

    private MachineProvisioningLocation provisioningLoc
    protected SshBasedAppSetup setup
    protected transient AttributePoller attributePoller
    
    AbstractService(Map properties=[:], Entity owner=null) {
        super(properties, owner)
 
        setAttribute(SERVICE_UP, false)
        setAttribute(SERVICE_CONFIGURED, false)
        setAttribute(SERVICE_STATE, Lifecycle.CREATED)
    }

    public abstract SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc)

    protected void preStart() { }
    protected void initSensors() { }
    protected void postStart() { }
    
    protected void preStop() { }
    protected void postStop() { }

    public void start(Collection<Location> locations) {
        setAttribute(SERVICE_STATE, Lifecycle.STARTING)
        attributePoller = new AttributePoller(this)
        
        preStart()
        startInLocation locations
        setAttribute(SERVICE_STATE, Lifecycle.STARTED)

        initSensors()
        postStart()

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
            throw new IllegalStateException("$this aborted soon after startup. Entity log file location: ${getAttribute(LOG_FILE_LOCATION)}")
        }
    }

    public void stop() {
        setAttribute(SERVICE_STATE, Lifecycle.STOPPING)
        MachineLocation machine = removeFirstMatchingLocation({ it in MachineLocation })
        if (machine) {
            shutdownInLocation(machine)
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

    public void shutdownInLocation(MachineLocation machine) {
        if (attributePoller) attributePoller.close()
        
        preStop()
        if (setup) setup.stop()
        postStop()
        
        // Only release this machine if we ourselves provisioned it (e.g. it might be running multiple services)
        provisioningLoc?.release(machine)
    }

    public void restart() {
        if (setup) {
            setup.restart()
        }
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
