package brooklyn.entity.basic

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaAppSetup
import brooklyn.util.internal.Repeater

import com.google.common.base.Preconditions

/**
* An {@link brooklyn.entity.Entity} representing a single web application instance.
*/
public abstract class JavaApp extends AbstractEntity implements Startable {
    public static final Logger log = LoggerFactory.getLogger(JavaApp.class)

    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.SUGGESTED_VERSION;
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeys.SUGGESTED_INSTALL_DIR;
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeys.SUGGESTED_RUN_DIR;
    public static final ConfigKey<Integer> SUGGESTED_JMX_PORT = ConfigKeys.SUGGESTED_JMX_PORT;
    public static final ConfigKey<String> SUGGESTED_JMX_HOST = ConfigKeys.SUGGESTED_JMX_HOST;

    public static final AttributeSensor<Integer> JMX_PORT = Attributes.JMX_PORT;
    public static final AttributeSensor<String> JMX_HOST = Attributes.JMX_HOST;

    public static final BasicAttributeSensor<Boolean> NODE_UP = [ Boolean, "app.hasStarted", "Node started" ];
    public static final BasicAttributeSensor<String> NODE_STATUS = [ String, "app.status", "Node status" ];

    transient JmxSensorAdapter jmxAdapter
    transient AttributePoller attributePoller

    JavaApp(Map properties=[:]) {
        super(properties)
        if (properties.jmxPort) setConfig(SUGGESTED_JMX_PORT, properties.remove("jmxPort"))
        if (properties.jmxHost) setConfig(SUGGESTED_JMX_HOST, properties.remove("jmxHost"))
 
        setAttribute(NODE_UP, false)
    }

    public abstract SshBasedJavaAppSetup getSshBasedSetup(SshMachineLocation loc);

    protected abstract void initJmxSensors();

    public void waitForJmx() {
        new Repeater("Wait for JMX").repeat().every(1, TimeUnit.SECONDS).until({jmxAdapter.isConnected()}).limitIterationsTo(30).run();
    }

    public void start(Collection<Location> locations) {
        startInLocation locations

        if (!(getAttribute(JMX_HOST) && getAttribute(JMX_PORT)))
            throw new IllegalStateException("JMX is not available")

        attributePoller = new AttributePoller(this)
        jmxAdapter = new JmxSensorAdapter(this, 60*1000)
        jmxAdapter.connect();
        waitForJmx()

        initJmxSensors()
    }

    public void startInLocation(Collection<Location> locs) {
        // TODO check has exactly one?
        MachineProvisioningLocation loc = locs.find({ it instanceof MachineProvisioningLocation });
        Preconditions.checkArgument loc != null, "None of the provided locations is a MachineProvisioningLocation"
        startInLocation(loc)
    }

    public void startInLocation(MachineProvisioningLocation loc) {
        SshMachineLocation machine = loc.obtain()
        if (machine == null) throw new NoMachinesAvailableException(loc)
        locations.add(machine)

        SshBasedJavaAppSetup setup = getSshBasedSetup(machine)
        setAttribute(NODE_STATUS, "starting")
        setup.start()
        waitForEntityStart(setup)
    }

    // TODO Find a better way to detect early death of process.
    // This is uncallable if marked private?!
    public void waitForEntityStart(SshBasedJavaAppSetup setup) throws IllegalStateException {
        log.debug "waiting to ensure $this doesn't abort prematurely"
        long startTime = System.currentTimeMillis()
        boolean isRunningResult = false;
        while (!isRunningResult && System.currentTimeMillis() < startTime+45000) {
            Thread.sleep 3000
            isRunningResult = setup.isRunning()
            log.debug "checked $this, running result $isRunningResult"
        }
        if (!isRunningResult) {
            setAttribute(NODE_STATUS, "failed")
            throw new IllegalStateException("$this aborted soon after startup")
        }
        setAttribute(NODE_STATUS, "running")
    }

    // FIXME: should MachineLocations below actually be SshMachineLocation? That's what XSshSetup requires, but not what the unit tests offer.
    public void stop() {
        setAttribute(NODE_STATUS, "stopping")
        if (attributePoller) attributePoller.close()
        if (jmxAdapter) jmxAdapter.disconnect();
        shutdownInLocation(locations.find({ it instanceof MachineLocation }))
    }

    public void shutdownInLocation(MachineLocation loc) {
        getSshBasedSetup(loc).stop()
        setAttribute(NODE_STATUS, "stopped")
        setAttribute(NODE_UP, false)
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['jmxPort']
    }
}
