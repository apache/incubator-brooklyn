package brooklyn.entity.basic

import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
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
public abstract class AbstractService extends AbstractEntity implements Startable {
    public static final Logger log = LoggerFactory.getLogger(AbstractService.class)

    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION;
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR;
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR;

    public static final BasicAttributeSensor<String> SERVICE_STATUS = [ String, "service.status", "Service status" ]

    protected SshBasedAppSetup setup
    
    AbstractService(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        if (properties.version) setConfig(SUGGESTED_VERSION, properties.remove("version"))
        if (properties.installDir) setConfig(SUGGESTED_INSTALL_DIR, properties.remove("installDir"))
        if (properties.runDir) setConfig(SUGGESTED_INSTALL_DIR, properties.remove("runDir"))
 
        setAttribute(SERVICE_UP, false)
    }

    public abstract SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc);

    public void start(Collection<Location> locations) {
        startInLocation locations
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
	        setup.start()
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
        if (setup) setup.stop()
        Location parent = machine.parentLocation
        if (parent instanceof MachineProvisioningLocation) {
            ((MachineProvisioningLocation) parent).release(setup.machine)
        }
    }

    public void restart() {
        if (setup) {
            setup.restart()
        }
    }
}
