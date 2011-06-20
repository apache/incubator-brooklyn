package brooklyn.entity.webapp.tomcat

import groovy.transform.InheritConstructors

import java.util.Collection
import java.util.Map;
import java.util.Map
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import javax.management.InstanceNotFoundException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.EntityStartException
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.AttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityStartUtils

/**
 * An entity that represents a single Tomcat instance.
 * 
 * @author Richard Downer <richard.downer@cloudsoftcorp.com>
 */
public class TomcatNode extends AbstractEntity implements Startable {
	
	private static final Logger logger = LoggerFactory.getLogger(TomcatNode.class)

    public static final AttributeSensor<Integer> ERROR_COUNT = [ "Request errors", "jmx.reqs.global.totals.errorCount", Integer ]
    public static final AttributeSensor<Integer> HTTP_PORT = [ "HTTP port", "webapp.http.port", Integer ]
    public static final AttributeSensor<Integer> MAX_PROCESSING_TIME = [ "Request count", "jmx.reqs.global.totals.maxTime", Integer ]
    public static final AttributeSensor<Integer> REQUEST_COUNT = [ "Request count", "jmx.reqs.global.totals.requestCount", Integer ]
    public static final AttributeSensor<Integer> REQUESTS_PER_SECOND = [ "Reqs/Sec", "webapp.reqs.persec.RequestCount", Integer ]
    public static final AttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ "Total processing time", "jmx.reqs.global.totals.processingTime", Integer ]
	
//	public static final Effector<Integer> GET_TOTAL_PROCESSING_TIME = [ "retrieves the total processing time", { delegate, arg1, arg2 -> delegate.getTotal(arg1, arg2) } ]
//	Task<Integer> invocation = entity.invoke(GET_TOTAL_PROCESSING_TIME, ...args)
	 
    // This might be more interesting as some status like 'starting', 'started', 'failed', etc.
    public static final AttributeSensor<String>  NODE_UP = [ "Node started", "webapp.hasStarted", Boolean ];
    
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
			updateAttribute HTTP_PORT, setup.httpPort
		}
		TomcatNode.metaClass.shutdownInLocation = { SshMachineLocation loc -> new Tomcat7SshSetup(delegate).shutdown loc }
        TomcatNode.metaClass.deploy = { String file, SshMachineLocation loc -> 
            new Tomcat7SshSetup(delegate).deploy(new File(file), loc)
		}
	}

    JmxSensorAdapter jmxAdapter;
 
	//TODO hack reference (for shutting down), need a cleaner way -- e.g. look up in the app's executor service for this entity
	ScheduledFuture jmxMonitoringTask;

    public TomcatNode(Map properties=[:]) {
        super(properties);
    }

	public void start(Map startAttributes=[:]) {
		EntityStartUtils.startEntity startAttributes, this
		log.debug "started... jmxHost is {} and jmxPort is {}", this.attributes['jmxHost'], this.attributes['jmxPort']
		
		if (this.attributes['jmxHost'] && this.attributes['jmxPort']) {
			jmxAdapter = new JmxSensorAdapter(this.attributes.jmxHost, this.attributes.jmxPort)
			if (!(jmxAdapter.connect(60*1000))) {
				log.error "FAILED to connect JMX to {}", this
				throw new IllegalStateException("failed to completely start $this: JMX not found at $jmxHost:$jmxPort after 60s")
			}
			
			//TODO get executor from app, then die when finished; why isn't schedule working???
			//e.g. getApplication().getExecutors().
			jmxMonitoringTask = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay({ updateJmxSensors() }, 1000, 1000, TimeUnit.MILLISECONDS)
			
			// Wait for the HTTP port to become available
			String state = null
			int port = getAttribute(HTTP_PORT)
			for(int attempts = 0; attempts < 30; attempts++) {
				Map connectorAttrs;
				try {
					connectorAttrs = jmxAdapter.getAttributes("Catalina:type=Connector,port=$port")
					state = connectorAttrs['stateName']
				} catch(InstanceNotFoundException e) {
					state = "InstanceNotFound"
				}
				logger.trace "state: $state"
				if (state == "FAILED") {
                    attributes.update(NODE_UP, false)
					throw new EntityStartException("Tomcat connector for port $port is in state $state")
				} else if (state == "STARTED") {
                    attributes.update(NODE_UP, true)
					break;
				}
				Thread.sleep 250
			}
			if(state != "STARTED") {
                attributes.update(NODE_UP, false)
				throw new EntityStartException("Tomcat connector for port $port is in state $state after 30 seconds")
            }
		}
        if (this.war) {
            log.debug "Deploying {} to {}", this.war, this.location
            this.deploy(this.war, this.location)
            log.debug "Deployed {} to {}", this.war, this.location
        }
	}
	
	private int computeReqsPerSec() {
        def reqs = jmxAdapter.getChildrenAttributesWithTotal("Catalina:type=GlobalRequestProcessor,name=\"*\"")
		reqs.put "timestamp", System.currentTimeMillis()
		
        // update to explicit location in activity map, but not linked to sensor 
        // so probably shouldn't be used too widely 
		Map prev = activity.update(["jmx","reqs","global"], reqs)
        
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
		rps
	}
	
	/* FIXME discard use of this method (and the registration above), in favour of approach below */
	private void updateJmxSensors() {
		int rps = computeReqsPerSec()
		//is a sensor, should generate update events against subscribers
		activity.update(REQUESTS_PER_SECOND, rps)
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
		if (jmxMonitoringTask) jmxMonitoringTask.cancel true
		if (location) shutdownInLocation(location)
	}

}
