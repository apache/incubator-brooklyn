package brooklyn.entity.webapp

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AttributeDictionary
import brooklyn.entity.basic.ConfigKeyDictionary
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityStartUtils

import com.google.common.base.Preconditions

/**
* An {@link brooklyn.entity.Entity} representing a single web application instance.
*/
public abstract class JavaWebApp extends AbstractEntity implements Startable {
    
    public static final Logger log = LoggerFactory.getLogger(JavaWebApp.class)

    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeyDictionary.SUGGESTED_VERSION;
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = ConfigKeyDictionary.SUGGESTED_INSTALL_DIR;
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = ConfigKeyDictionary.SUGGESTED_RUN_DIR;
    public static final ConfigKey<Integer> SUGGESTED_HTTP_PORT = ConfigKeyDictionary.SUGGESTED_HTTP_PORT;
    public static final ConfigKey<Integer> SUGGESTED_JMX_PORT = ConfigKeyDictionary.SUGGESTED_JMX_PORT;
    public static final ConfigKey<String> SUGGESTED_JMX_HOST = ConfigKeyDictionary.SUGGESTED_JMX_HOST;
    
    public static final AttributeSensor<Integer> HTTP_PORT = AttributeDictionary.HTTP_PORT;
    public static final AttributeSensor<Integer> JMX_PORT = AttributeDictionary.JMX_PORT;
    public static final AttributeSensor<String> JMX_HOST = AttributeDictionary.JMX_HOST;

    public static final BasicAttributeSensor<Boolean> NODE_UP = [ Boolean, "webapp.hasStarted", "Node started" ];
    public static final BasicAttributeSensor<String> NODE_STATUS = [ String, "webapp.status", "Node status" ];
    
    public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "webapp.reqs.errors", "Request errors" ]
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "webpp.reqs.processing.max", "Max processing time" ]
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "webapp.reqs.total", "Request count" ]
    public static final BasicAttributeSensor<Integer> REQUESTS_PER_SECOND = [ Integer, "webapp.reqs.persec", "Reqs/Sec" ]
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "webapp.reqs.processing.time", "Total processing time" ]

    // public static final Effector<Integer> GET_TOTAL_PROCESSING_TIME = [ "retrieves the total processing time", { delegate, arg1, arg2 -> delegate.getTotal(arg1, arg2) } ]
    // Task<Integer> invocation = entity.invoke(GET_TOTAL_PROCESSING_TIME, ...args)
    
    transient JmxSensorAdapter jmxAdapter;

    JavaWebApp(Map properties=[:]) {
        super(properties)
        if (properties.httpPort) setConfig(SUGGESTED_HTTP_PORT, properties.remove("httpPort"))
        if (properties.jmxPort) setConfig(SUGGESTED_JMX_PORT, properties.remove("jmxPort"))
        if (properties.jmxHost) setConfig(SUGGESTED_JMX_HOST, properties.remove("jmxHost"))
        
        // addEffector(Startable.START);
        updateAttribute(NODE_UP, false)
        updateAttribute(NODE_STATUS, "uninitialized")
    }

    public abstract SshBasedJavaWebAppSetup getSshBasedSetup(SshMachineLocation loc);
    protected abstract void initJmxSensors();
    abstract void waitForHttpPort();
        
    public void start(Collection<Location> locations) {
        EntityStartUtils.startEntity this, locations;
        
        if (!(getAttribute(JMX_HOST) && getAttribute(JMX_PORT)))
            throw new IllegalStateException("JMX is not available")
        
        log.debug "started $this: httpPort {}, jmxHost {} and jmxPort {}", 
                getAttribute(HTTP_PORT), getAttribute(JMX_HOST), getAttribute(JMX_PORT)
        
        jmxAdapter = new JmxSensorAdapter(this, 60*1000)
        initJmxSensors()
        jmxAdapter.addSensor(REQUESTS_PER_SECOND, { computeReqsPerSec() }, 1000L)
        
        waitForHttpPort()
        
        if (this.war) {
            log.debug "Deploying {} to {}", this.war, this.machine
            this.deploy(this.war)
            log.debug "Deployed {} to {}", this.war, this.machine
        }
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

        SshBasedJavaWebAppSetup setup = getSshBasedSetup(machine)
        updateAttribute(NODE_STATUS, "starting")
        setup.start()
        waitForEntityStart(setup)
    }
    
    // TODO Find a better way to detect early death of process.
    // This is uncallable if marked private?!
    public void waitForEntityStart(SshBasedJavaWebAppSetup setup) throws IllegalStateException {
        log.debug "waiting to ensure $this doesn't abort prematurely"
        long startTime = System.currentTimeMillis()
        boolean isRunningResult = false;
        while (!isRunningResult && System.currentTimeMillis() < startTime+45000) {
            Thread.sleep 3000
            isRunningResult = setup.isRunning()
            log.debug "checked $this, running result $isRunningResult"
        }
        if (!isRunningResult) {
            updateAttribute(NODE_STATUS, "failed")
            throw new IllegalStateException("$this aborted soon after startup")
        }
        updateAttribute(NODE_STATUS, "running")
    }

    // FIXME: should MachineLocations below actually be SshMachineLocation? That's what XSshSetup requires, but not what the unit tests offer.
    public void shutdown() {
        updateAttribute(NODE_STATUS, "stopping")
        jmxAdapter.disconnect();
        shutdownInLocation(locations.find({ it instanceof MachineLocation }))
    }
    
    public void shutdownInLocation(MachineLocation loc) {
        getSshBasedSetup(loc).shutdown()
        updateAttribute(NODE_STATUS, "stopped")
    }
    
    public void deploy(String file) {
        getSshBasedSetup(locations.find({ it instanceof MachineLocation })).deploy(new File(file))
    }
    
    protected void computeReqsPerSec() {
 
        def curTimestamp = System.currentTimeMillis()
        def curCount = getAttribute(REQUEST_COUNT) ?: 0
        
        // TODO Andrew reviewing/changing?
        def prevTimestamp = tempWorkings['tmp.reqs.timestamp'] ?: 0
        def prevCount = tempWorkings['tmp.reqs.count'] ?: 0
        tempWorkings['tmp.reqs.timestamp'] = curTimestamp
        tempWorkings['tmp.reqs.count'] = curCount
        log.trace "previous data {} at {}, current {} at {}", prevCount, prevTimestamp, curCount, curTimestamp
        
        // Calculate requests per second
        double diff = curCount - prevCount
        long dt = curTimestamp - prevTimestamp
        
        if (dt <= 0 || dt > 60*1000) {
            diff = -1;
        } else {
            diff = ((double) 1000.0 * diff) / dt
        }
        int rps = (int) Math.round(diff)
        log.trace "computed $rps reqs/sec over $dt millis"
        updateAttribute(REQUESTS_PER_SECOND, rps)
    }
    
    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['jmxPort']
    }

}
