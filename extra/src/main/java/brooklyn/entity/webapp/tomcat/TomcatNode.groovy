package brooklyn.entity.webapp.tomcat

import java.util.Collection
import java.util.Map

import javax.management.InstanceNotFoundException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.EntityStartException
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.PropertiesSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.JmxAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.management.internal.task.Futures
import brooklyn.util.internal.EntityStartUtils


/**
 * An {@link Entity} that represents a single Tomcat instance.
 */
public class TomcatNode extends AbstractEntity implements Startable {
	private static final Logger log = LoggerFactory.getLogger(TomcatNode.class)
 
    public static final BasicAttributeSensor<Integer> HTTP_PORT = [ Integer, "webapp.http.port", "HTTP port" ]
    public static final BasicAttributeSensor<Integer> REQUESTS_PER_SECOND = [ Integer, "webapp.reqs.persec", "Reqs/Sec" ]
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "webpp.reqs.processing.max", "Max processing time" ]
    public static final BasicAttributeSensor<Boolean> NODE_UP = [ Boolean, "webapp.hasStarted", "Node started" ];
    public static final BasicAttributeSensor<String> NODE_STATUS = [ String, "webapp.status", "Node status" ];
    
    public static final JmxAttributeSensor<Integer> ERROR_COUNT = [ Integer, "webapp.reqs.errors", "Request errors", "Catalina:type=GlobalRequestProcessor,name=\"*\"", "errorCount" ]
    public static final JmxAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "webapp.reqs.total", "Request count", "Catalina:type=GlobalRequestProcessor,name=\"*\"", "requestCount"  ]
    public static final JmxAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "webapp.reqs.processing.time", "Total processing time", "Catalina:type=GlobalRequestProcessor,name=\"*\"", "processingTime" ]
	
//	public static final Effector START = new AbstractEffector("start", Void.TYPE, [], "starts an entity");
//	public static final Effector<Integer> GET_TOTAL_PROCESSING_TIME = [ "retrieves the total processing time", { delegate, arg1, arg2 -> delegate.getTotal(arg1, arg2) } ]
//	Task<Integer> invocation = entity.invoke(GET_TOTAL_PROCESSING_TIME, ...args)
    
    JmxSensorAdapter jmxAdapter;
 
	static {
		TomcatNode.metaClass.startInLocation = { SshMachineLocation loc ->
			def setup = new Tomcat7SshSetup(delegate)
			//pass http port to setup, if one was specified on this object
			if (attributes['httpPort']) setup.httpPort = attributes['httpPort']
            delegate.attributes['httpPort'] = setup.tomcatHttpPort // copy the http port to tomcat entity
			setup.start loc
			// TODO: remove the 3s sleep and find a better way to detect an early death of the Tomcat process
			log.debug "waiting to ensure $delegate doesn't abort prematurely"
			Thread.sleep 3000
			def isRunningResult = setup.isRunning(loc)
			if (!isRunningResult) throw new IllegalStateException("$delegate aborted soon after startup")
		}
		TomcatNode.metaClass.shutdownInLocation = { SshMachineLocation loc -> null
			new Tomcat7SshSetup(delegate).shutdown loc 
		}
        TomcatNode.metaClass.deploy = { String file, SshMachineLocation loc -> null 
            new Tomcat7SshSetup(delegate).deploy(new File(file), loc)
		}
	}
 
    public TomcatNode(Map properties=[:]) {
        super(properties);
    }

	public void start(Map startAttributes=[:]) {
		EntityStartUtils.startEntity startAttributes, this
        if (!this.jxmHost && !this.jmxPort)
            throw new IllegalStateException("JMX is not available")

		log.debug "starting tomcat: httpPort {}, jmxHost {} and jmxPort {}", this.attributes['httpPort'], this.attributes['jmxHost'], this.attributes['jmxPort']
		
		propertiesAdapter.addSensor HTTP_PORT, this.attributes['httpPort']
		propertiesAdapter.addSensor NODE_UP, false
        propertiesAdapter.addSensor NODE_STATUS, "starting"
 
		if (this.attributes['jmxHost'] && this.attributes['jmxPort']) {
			jmxAdapter = new JmxSensorAdapter(this, 60*1000)
            
            Futures.when({
        			// Wait for the HTTP port to become available
        			String state = null
        			int port = getAttribute(HTTP_PORT)
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
		}
        
        // Add JMX sensors
        jmxAdapter.addSensor(ERROR_COUNT)
        jmxAdapter.addSensor(REQUEST_COUNT)
        jmxAdapter.addSensor(TOTAL_PROCESSING_TIME)
        jmxAdapter.addSensor(REQUESTS_PER_SECOND, { computeReqsPerSec() }, 100L)
 
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
		def prevTimestamp = attributes['tmp.reqs.timestamp'] ?: 0
		def prevCount = attributes['tmp.reqs.count'] ?: 0
        attributes['tmp.reqs.timestamp'] = curTimestamp
        attributes['tmp.reqs.count'] = curCount
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
		if (location) shutdownInLocation(location)
	}
}
