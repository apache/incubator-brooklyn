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
import brooklyn.event.basic.DynamicAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.management.internal.task.Futures
import brooklyn.util.internal.EntityStartUtils


/**
 * An entity that represents a single Tomcat instance.
 * 
 * @author Richard Downer <richard.downer@cloudsoftcorp.com>
 */
public class TomcatNode extends AbstractEntity implements Startable {
	private static final Logger log = LoggerFactory.getLogger(TomcatNode.class)
 
    public static final BasicAttributeSensor<Integer> HTTP_PORT = [ Integer, "webapp.http.port", "HTTP port" ]
    public static final DynamicAttributeSensor<Integer> REQUESTS_PER_SECOND = [ Integer, "webapp.reqs.persec.RequestCount", "Reqs/Sec", { computeReqsPerSec() }, 1000 ]
    
    public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "jmx.reqs.global.totals.maxTime", "Request errors" ]
    public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "jmx.reqs.global.totals.maxTime", "Request count" ]
    public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "jmx.reqs.global.totals.requestCount", "Request count" ]
    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "jmx.reqs.global.totals.processingTime", "Total processing time" ]
	
//	public static final Effector START = new AbstractEffector("start", Void.TYPE, [], "starts an entity");
	
//	public static final Effector<Integer> GET_TOTAL_PROCESSING_TIME = [ "retrieves the total processing time", { delegate, arg1, arg2 -> delegate.getTotal(arg1, arg2) } ]
//	Task<Integer> invocation = entity.invoke(GET_TOTAL_PROCESSING_TIME, ...args)
	 
    // This might be more interesting as some status like 'starting', 'started', 'failed', etc.
    public static final BasicAttributeSensor<Boolean>  NODE_UP = [ Boolean, "webapp.hasStarted", "Node started" ];
    public static final BasicAttributeSensor<String>  NODE_STATUS = [ String, "webapp.status", "Node status" ];
    
    JmxSensorAdapter jmxAdapter;
 
	static {
		TomcatNode.metaClass.startInLocation = { SshMachineLocation loc ->
			def setup = new Tomcat7SshSetup(delegate)
			//pass http port to setup, if one was specified on this object
			if (attributes.httpPort) setup.httpPort = attributes.httpPort
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

		log.debug "started... jmxHost is {} and jmxPort is {}", this.attributes['jmxHost'], this.attributes['jmxPort']
		
		propertiesAdapter.addSensor HTTP_PORT, this.attributes['httpPort']
		propertiesAdapter.addSensor NODE_UP, false
        propertiesAdapter.addSensor NODE_STATUS, "starting"
 
		if (this.properties['jmxHost'] && this.properties['jmxPort']) {
			jmxAdapter = new JmxSensorAdapter(this, 60*1000)
            jmxAdapter.addSensor(ERROR_COUNT, "Catalina:type=GlobalRequestProcessor,name=\"*\"")
            jmxAdapter.addSensor(MAX_PROCESSING_TIME, "Catalina:type=GlobalRequestProcessor,name=\"*\"")
            jmxAdapter.addSensor(REQUEST_COUNT, "Catalina:type=GlobalRequestProcessor,name=\"*\"")
            jmxAdapter.addSensor(TOTAL_PROCESSING_TIME, "Catalina:type=GlobalRequestProcessor,name=\"*\"")
            jmxAdapter.addSensor(REQUESTS_PER_SECOND, "Catalina:type=GlobalRequestProcessor,name=\"*\"", { computeReqsPerSec() }, 1000)
            
            Futures.futureValueWhen({
        			// Wait for the HTTP port to become available
        			String state = null
        			int port = getAttribute(HTTP_PORT)
        			for(int attempts = 0; attempts < 30; attempts++) {
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
                }, { jmxAdapter.isConnected() }).get()
		}
 
        if (this.war) {
            log.debug "Deploying {} to {}", this.war, this.location
            this.deploy(this.war, this.location)
            log.debug "Deployed {} to {}", this.war, this.location
        }
	}
	
	private void computeReqsPerSec() {
        def reqs = jmxAdapter.getChildrenAttributesWithTotal("Catalina:type=GlobalRequestProcessor,name=\"*\"")
		reqs.put "timestamp", System.currentTimeMillis()
        log.info "got reqs: {}", reqs
		
        // update to explicit location in activity map, but not linked to sensor 
        // so probably shouldn't be used too widely 
		Map prev = updateAttribute(["jmx","reqs","global"], reqs)
        
        // Calculate requests per second
        double diff = (reqs?.totals?.requestCount ?: 0) - (prev?.totals?.requestCount ?: 0)
		long dt = (reqs?.timestamp ?: 0) - (prev?.timestamp ?: 0)
        
		if (dt <= 0 || dt > 60*1000) {
            diff = -1; 
		} else {
            diff = ((double) 1000.0 * diff) / dt
		}
		int rps = (int) Math.round(diff)
		log.trace "computed $rps reqs/sec over $dt millis for JMX tomcat process at $jmxHost:$jmxPort"
		updateAttribute(REQUESTS_PER_SECOND, rps)
	}
	
	// FIXME something like this seems a much nicer way to register sensors
//	{ addSensor(REQUESTS_PER_SECOND, new AttributeSensorSource(&computeReqsPerSec), defaultPeriod: 5*SECONDS) }
//	{ addSensor(REQUESTS_PER_SECOND, &computeReqsPerSec, defaultPeriod: 5*SECONDS) }
	/* the protected addSensor methods
	 *   addSensor(AttributeSensor<T>, AttributeSensorSource<T> 
	 * sit on AbstractEntity takes care of registering the list of sensors, scheduled tasks, etc;  
	 * if a subscriber requests a different period then that could trump (as enhancement; it gets tricky because
	 * we are computing derived values & might have someone getting every 3s and someone else every 7s, we'd want to tally total for 7s and for 3s, perhaps...) */
	// but problem with above is you can't easily see at design-time what the sensors are, could do something like
//	public AttributeSensorSource<Integer> requestsPerSecond = [ this, REQUESTS_PER_SECOND, this.&computeReqsPerSec, defaultPeriod: 5*SECONDS ]
	// then assuming AttributeSensorSource has an asSensor adapter method (or even extends AttributeSensor<Integer>) other guys could subscribe to
	// TomcatNode.requestsPerSecond as Sensor    which returns REQUESTS_PER_SECOND
	// (or even  TomcatNode.requestsPerSecond  directly...)	
	
	@Override
	public Collection<String> toStringFieldsToInclude() {
		return super.toStringFieldsToInclude() + ['tomcatHttpPort', 'jmxPort']
	}

	public void shutdown() {
		if (jmxAdapter) jmxAdapter.disconnect()
		if (location) shutdownInLocation(location)
	}
}
