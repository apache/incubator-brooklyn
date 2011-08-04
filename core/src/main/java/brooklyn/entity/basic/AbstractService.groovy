package brooklyn.entity.basic

import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.trait.Configurable
import brooklyn.entity.trait.Startable
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

/**
* An {@link brooklyn.entity.Entity} representing an abstract service process.
*
* A service can only run on a single {@link MachineLocation} at a time.
*/
public abstract class AbstractService extends AbstractEntity implements Startable, Configurable {
    public static final Logger log = LoggerFactory.getLogger(AbstractService.class)

    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION;
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR;
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR;
    public static final BasicConfigKey<Map> ENVIRONMENT = [ Map, "environment", "Map of environment variables to set at runtime", [:] ]

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
    }

    public abstract SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc);

    protected void initSensors() {
    }

    public void start(Collection<Location> locations) {
        doStart(locations)
        initSensors()
    }

    /**
     * Separate doStart method, to be called by sub-classes that override start to do extra stuff
     * before invoking initSensors
     */
    protected void doStart(Collection<Location> locations) {
        startInLocation locations
        
        attributePoller = new AttributePoller(this)
    }

    public void startInLocation(Collection<Location> locs) {
        // TODO check has exactly one?
        MachineProvisioningLocation loc = locs.find { it instanceof MachineProvisioningLocation }
        Preconditions.checkArgument loc != null, "None of the provided locations is a MachineProvisioningLocation"
        startInLocation(loc)
    }

    public void startInLocation(MachineProvisioningLocation location) {
        Map<String,Object> flags = location.getProvisioningFlags([getClass().getName()])
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
        setup = getSshBasedSetup(machine)
        setAttribute(SERVICE_STATUS, "starting")
        if (setup) {
            setup.install()
            setup.config()
	        configure()
            setup.runApp()
            setup.postStart()
	        waitForEntityStart()
        }
        setAttribute(SERVICE_STATUS, "running")
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
            setAttribute(SERVICE_STATUS, "failed")
            throw new IllegalStateException("$this aborted soon after startup")
        }
    }

    public void stop() {
        setAttribute(SERVICE_STATUS, "stopping")
        MachineLocation machine = locations.find { it in MachineLocation }
        shutdownInLocation(machine)
        setAttribute(SERVICE_STATUS, "stopped")
        setAttribute(SERVICE_UP, false)
    }

    public void shutdownInLocation(MachineLocation machine) {
        if (attributePoller) attributePoller.close()
        
        if (setup) setup.stop()
        
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
     * field is available and any {@link MachineLocation} is is instantiated, but before the {@link SshBasedAppSetup#start()}
     * method is called.
     */
    public void configure() { }
}
