package brooklyn.entity.basic

import java.util.Collection

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
import brooklyn.util.SshBasedJavaAppSetup

import com.google.common.base.Preconditions

/**
* An {@link brooklyn.entity.Entity} representing an abstract service process.
*/
public abstract class AbstractService extends AbstractEntity implements Startable {
    public static final Logger log = LoggerFactory.getLogger(AbstractService.class)

    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION;
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR;
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR;

    public static final BasicAttributeSensor<Boolean> SERVICE_UP = [ Boolean, "service.hasStarted", "Service started" ]
    public static final BasicAttributeSensor<String> SERVICE_STATUS = [ String, "service.status", "Service status" ]
    
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

    public void startInLocation(MachineProvisioningLocation loc) {
        SshMachineLocation machine = loc.obtain()
        if (machine == null) throw new NoMachinesAvailableException(loc)
        startInLocation(machine)
    }
    
    public void startInLocation(SshMachineLocation machine) {
        locations.add(machine)
        SshBasedAppSetup setup = getSshBasedSetup(machine)
        setAttribute(SERVICE_STATUS, "starting")
        setup.start()
        waitForEntityStart(setup)
    }

    // TODO Find a better way to detect early death of process.
    public void waitForEntityStart(SshBasedAppSetup setup) throws IllegalStateException {
        log.debug "waiting to ensure $this doesn't abort prematurely"
        long startTime = System.currentTimeMillis()
        long waitTime = startTime + 45000 // FIXME magic number
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
        setAttribute(SERVICE_STATUS, "running")
    }

    // FIXME: should MachineLocations below actually be SshMachineLocation? That's what XSshSetup requires, but not what the unit tests offer.
    public void stop() {
        setAttribute(SERVICE_STATUS, "stopping")
        shutdownInLocation(locations.find({ it instanceof MachineLocation }))
    }

    public void shutdownInLocation(MachineLocation loc) {
        getSshBasedSetup(loc).stop()
        setAttribute(SERVICE_STATUS, "stopped")
        setAttribute(SERVICE_UP, false)
    }

    public void restart() {
        locations.each { MachineLocation machine -> getSshBasedSetup(machine).restart() }
    }
}
