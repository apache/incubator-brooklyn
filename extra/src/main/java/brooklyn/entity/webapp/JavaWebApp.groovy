package brooklyn.entity.webapp

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AttributeDictionary
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.SshBasedJavaWebAppSetup

import brooklyn.util.internal.EntityStartUtils
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.location.basic.SshMachineLocation

/**
* An {@link brooklyn.entity.Entity} representing a single web application instance.
*/
public abstract class JavaWebApp extends AbstractEntity implements Startable {
    
    public static final Logger log = LoggerFactory.getLogger(JavaWebApp.class)

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
        // addEffector(Startable.START);
        propertiesAdapter.addSensor NODE_UP, false
        propertiesAdapter.addSensor NODE_STATUS, "starting"
    }

    public abstract SshBasedJavaWebAppSetup getSshBasedSetup(SshMachineLocation loc);
    protected abstract void initJmxSensors();
    abstract void waitForHttpPort();
        
    // TODO Thinking about things...
    // public void startInLocation(MachineFactoryLocation factory) {
    //     GeneralPurposeLocation loc = factory.newMachine();
    //     startInLocation(loc);
    // }
    
    public void startInLocation(Collection<GeneralPurposeLocation> locs) {
        // TODO check has exactly one?
        startInLocation(locs.iterator().next())
    }
    
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
    
    public void startInLocation(GeneralPurposeLocation loc) {
        
        locations.add(loc)
        if (!loc.attributes.provisioner)
            throw new IllegalStateException("Location $loc does not have a machine provisioner")

        SshMachineLocation machine = loc.attributes.provisioner.obtain()
        if (machine == null) throw new NoMachinesAvailableException(loc)
        this.machine = machine
        
        SshBasedJavaWebAppSetup setup = getSshBasedSetup(machine)
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
        if (!isRunningResult) throw new IllegalStateException("$this aborted soon after startup")
    }

    public void shutdown() {
        jmxAdapter.disconnect();
        shutdownInLocation(locations.iterator().next())
    }
    
    public void shutdownInLocation(GeneralPurposeLocation loc) {
        getSshBasedSetup(this.machine).shutdown()
    }
    
    public void deploy(String file) {
        getSshBasedSetup(this.machine).deploy(new File(file))
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
