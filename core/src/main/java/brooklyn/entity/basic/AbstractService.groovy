package brooklyn.entity.basic

import java.io.File;
import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.trait.Configurable
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Preconditions
import com.google.common.collect.Iterables

/**
 * An {@link Entity} representing an abstract service process.
 *
 * A service can only run on a single {@link MachineLocation} at a time.
 */
public abstract class AbstractService extends AbstractEntity implements Startable, Configurable {
    public static final Logger log = LoggerFactory.getLogger(AbstractService.class)

    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION;
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR;
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR;
    public static final BasicConfigKey<Map> ENVIRONMENT = [ Map, "environment", "Map of environment variables to set at runtime", [:] ]

    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final AttributeSensor<String> ADDRESS = Attributes.ADDRESS;

    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = [ Lifecycle, "service.state", "Service lifecycle state" ]
    public static final BasicAttributeSensor<String> SERVICE_STATUS = [ String, "service.status", "Service status" ]

    private MachineProvisioningLocation provisioningLoc
    protected SshBasedAppSetup setup
    protected transient AttributePoller attributePoller
    
    AbstractService(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        setConfigIfValNonNull(SUGGESTED_VERSION, properties.version)
        setConfigIfValNonNull(SUGGESTED_INSTALL_DIR, properties.installDir)
        setConfigIfValNonNull(SUGGESTED_RUN_DIR, properties.runDir)
 
        setAttribute(SERVICE_UP, false)
        setAttribute(SERVICE_CONFIGURED, false)
        setAttribute(SERVICE_STATE, Lifecycle.CREATED)
    }

    public abstract SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc)

    protected void preStart() { }
    protected void postConfig() { }
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
        flags.inboundPorts = getRequiredOpenPorts()
        
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
            setup.install()
            setup.config()
	        configure()
	        postConfig()
            setup.runApp()
            setup.postStart()
	        waitForEntityStart()
        }
    }

    // TODO Find a better way to detect early death of process.
    public void waitForEntityStart() throws IllegalStateException {
        log.debug "waiting to ensure $this doesn't abort prematurely"
        long startTime = System.currentTimeMillis()
        long waitTime = startTime + 75000 // FIXME magic number
        boolean isRunningResult = false;
        while (!isRunningResult && System.currentTimeMillis() < waitTime) {
            Thread.sleep 3000 // FIXME magic number
            isRunningResult = setup.isRunning()
            log.debug "checked $this, running result $isRunningResult"
        }
        if (!isRunningResult) {
            setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE)
            throw new IllegalStateException("$this aborted soon after startup")
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

    /**
     * Configure the service.
     *
     * This is a NO-OP but should be overridden in implementing classes. It will be called after the {@link #setup}
     * field is available and any {@link MachineLocation} is is instantiated, but before the {@link SshBasedAppSetup#runApp()}
     * method is called.
     */
    public void configure() {
        setAttribute(SERVICE_STATE, Lifecycle.CONFIGURED)
        setAttribute(SERVICE_CONFIGURED, true)
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
