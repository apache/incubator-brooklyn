package brooklyn.entity.webapp.tomcat

import static brooklyn.entity.basic.AttributeDictionary.*
import static brooklyn.entity.basic.ConfigKeyDictionary.*

import java.util.Collection
import java.util.Map

import javax.management.InstanceNotFoundException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AttributeDictionary
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.EntityStartException
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.management.internal.task.Futures
import brooklyn.util.internal.EntityStartUtils
import brooklyn.location.basic.SshMachine
import brooklyn.location.basic.NoMachinesAvailableException


/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
public class TomcatNode extends AbstractEntity implements Startable {
    private static final Logger log = LoggerFactory.getLogger(TomcatNode.class)
 
    public static final AttributeSensor<Integer> HTTP_PORT = AttributeDictionary.HTTP_PORT;
    public static final AttributeSensor<Integer> JMX_PORT = AttributeDictionary.JMX_PORT;
    public static final AttributeSensor<String> JMX_HOST = AttributeDictionary.JMX_HOST;
    public static final BasicAttributeSensor<Integer> REQUESTS_PER_SECOND = [ Integer, "webapp.reqs.persec", "Reqs/Sec" ]
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "webpp.reqs.processing.max", "Max processing time" ]
    public static final BasicAttributeSensor<Boolean> NODE_UP = [ Boolean, "webapp.hasStarted", "Node started" ];
    public static final BasicAttributeSensor<String> NODE_STATUS = [ String, "webapp.status", "Node status" ];
    public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "webapp.reqs.errors", "Request errors" ]
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "webapp.reqs.total", "Request count" ]
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "webapp.reqs.processing.time", "Total processing time" ]
    
//    public static final Effector<Integer> GET_TOTAL_PROCESSING_TIME = [ "retrieves the total processing time", { delegate, arg1, arg2 -> delegate.getTotal(arg1, arg2) } ]
//    Task<Integer> invocation = entity.invoke(GET_TOTAL_PROCESSING_TIME, ...args)
    
    transient JmxSensorAdapter jmxAdapter;
 
    public TomcatNode(Map properties=[:]) {
        super(properties);

        // addEffector(Startable.START);

        /*getAttribute(HTTP_PORT)*/
        propertiesAdapter.addSensor HTTP_PORT, (properties.httpPort ?: -1)
        propertiesAdapter.addSensor NODE_UP, false
        propertiesAdapter.addSensor NODE_STATUS, "starting"
    }

    // TODO Thinking about thinks...
//    public void startInLocation(MachineFactoryLocation factory) {
//        SshMachineLocation loc = factory.newMachine();
//        startInLocation(loc);
//    }

    public void startInLocation(Collection<SshMachineLocation> locs) {
        // TODO check has exactly one?
        startInLocation(locs.iterator().next())
    }
    
    public void startInLocation(SshMachineLocation loc) {
        locations.add(loc)
        
        if (! loc.attributes.provisioner)
            throw new IllegalStateException("Location $loc does not have a machine provisioner")
        SshMachine machine = loc.attributes.provisioner.obtain()
        if (machine == null) throw new NoMachinesAvailableException(loc)
        this.machine = machine
        def setup = new Tomcat7SshSetup(this, machine)
        
        // TODO Allow HTTP_PORT to be a CONFIG (if supplied by user) _and_ an ATTRIBUTE
        // (where it's actually running).
        setup.start()
        
        // TODO: remove the 3s sleep and find a better way to detect an early death of the Tomcat process
        log.debug "waiting to ensure $delegate doesn't abort prematurely"
        Thread.sleep 3000
        def isRunningResult = setup.isRunning()
        if (!isRunningResult) throw new IllegalStateException("$delegate aborted soon after startup")
    }

    public void shutdownInLocation(SshMachineLocation loc) {
        new Tomcat7SshSetup(this, machine).shutdown()
    }

    public void deploy(String file, SshMachineLocation loc) {
        new Tomcat7SshSetup(this, machine).deploy(new File(file))
    }

    public void start(Collection<Location> locs) {
        EntityStartUtils.startEntity this, locs
        
        if (!(getAttribute(JMX_HOST) && getAttribute(JMX_PORT)))
            throw new IllegalStateException("JMX is not available")
        
        log.debug "starting tomcat: httpPort {}, jmxHost {} and jmxPort {}", getAttribute(HTTP_PORT), getAttribute(JMX_HOST), getAttribute(JMX_PORT)

        jmxAdapter = new JmxSensorAdapter(this, 60*1000)
        
        AbstractEntity ae = this
        Futures.when({
                // Wait for the HTTP port to become available
                String state = null
                int port = getAttribute(HTTP_PORT)?:-1
                for (int attempts = 0; attempts < 30; attempts++) {
                    Map connectorAttrs;
                    try {
                        connectorAttrs = jmxAdapter.getAttributes("Catalina:type=Connector,port=$port")
                        log.info "attempt {} - connector attribs are {}", attempts, connectorAttrs
                        state = connectorAttrs['stateName']
                    } catch (InstanceNotFoundException e) {
                        state = "InstanceNotFound"
                    }
                    updateAttribute(NODE_STATUS, state) 
                    log.trace "state: $state"
                    if (state == "FAILED") {
                        updateAttribute(NODE_UP, false)
                        throw new EntityStartException("Tomcat connector for port $port is in state $state")
                    } else if (state == "STARTED") {
                        updateAttribute(NODE_UP, true)
                        break;
                    }
                    Thread.sleep 250
                }
                if (state != "STARTED") {
                    updateAttribute(NODE_UP, false)
                    throw new EntityStartException("Tomcat connector for port $port is in state $state after 30 seconds")
                }
            }, { 
                boolean connected = jmxAdapter.isConnected()
                if (connected) log.info "jmx connected"
                connected
            })
        
        // Add JMX sensors
        jmxAdapter.addSensor(ERROR_COUNT, "Catalina:type=GlobalRequestProcessor,name=http-*", "errorCount")
        jmxAdapter.addSensor(REQUEST_COUNT, "Catalina:type=GlobalRequestProcessor,name=http-*", "requestCount")
        jmxAdapter.addSensor(TOTAL_PROCESSING_TIME, "Catalina:type=GlobalRequestProcessor,name=http-*", "processingTime")
        jmxAdapter.addSensor(REQUESTS_PER_SECOND, { computeReqsPerSec() }, 1000L)
 
        if (this.war) {
            log.debug "Deploying {} to {}", this.war, this.location
            this.deploy(this.war, this.location)
            log.debug "Deployed {} to {}", this.war, this.location
        }
    }
    
    private void computeReqsPerSec() {
        def reqs = jmxAdapter.getAttributes("Catalina:type=GlobalRequestProcessor,name=\"*\"")
        log.trace "running computeReqsPerSec - {}", reqs
 
        def curTimestamp = System.currentTimeMillis()
        def curCount = reqs?.requestCount ?: 0
        
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
        return super.toStringFieldsToInclude() + ['tomcatHttpPort', 'jmxPort']
    }

    public void shutdown() {
        if (jmxAdapter) jmxAdapter.disconnect()
        if (locations) shutdownInLocation(locations.iterator().next())
    }
}
