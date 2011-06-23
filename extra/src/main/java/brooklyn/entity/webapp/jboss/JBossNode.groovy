package brooklyn.entity.webapp.jboss

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityStartUtils

/**
 * JBoss web application server.
 */
//@InheritConstructors
public class JBossNode extends AbstractEntity implements Startable {
	private static final Logger log = LoggerFactory.getLogger(JBossNode.class)

    public static final BasicAttributeSensor<Integer> REQUESTS_PER_SECOND = [ Double, "jmx.reqs.persec.RequestCount", "Reqs/Sec" ]
	public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "jmx.reqs.global.totals.errorCount", "Error count" ]
	public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "jmx.reqs.global.totals.maxTime", "Max processing time" ]
	public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "jmx.reqs.global.totals.requestCount", "Request count" ]
	public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "jmx.reqs.global.totals.processingTime", "Total processing time" ]

    transient JmxSensorAdapter jmxAdapter;

	//TODO hack reference (for shutting down), need a cleaner way -- e.g. look up in the app's executor service for this entity
	ScheduledFuture jmxMonitoringTask;
    
    static {
        JBossNode.metaClass.startInLocation = { SshMachineLocation loc ->
			def setup = new JBoss6SshSetup(delegate)
			setup.start loc

			//TODO extract to a utility method
			//TODO use same code with TomcatNode, or use extract new abstract superclass
			log.debug "waiting to ensure $delegate doesn't abort prematurely"
			long startTime = System.currentTimeMillis()
			boolean isRunningResult = false;
			while (!isRunningResult && System.currentTimeMillis() < startTime+60000) {
				Thread.sleep 3000
				isRunningResult = setup.isRunning(loc)
				log.debug "checked jboss $delegate, running result $isRunningResult"
			}
			if (!isRunningResult) throw new IllegalStateException("$delegate aborted soon after startup")
			
			log.warn "not setting http port for successful jboss execution"
        }
        JBossNode.metaClass.shutdownInLocation = { SshMachineLocation loc ->
            new JBoss6SshSetup(delegate).shutdown loc;
        }
    }
    
    public void start(Map startAttributes=[:]) {
        EntityStartUtils.startEntity(startAttributes, this);
		log.debug "started... jmxHost is {} and jmxPort is {}", this.attributes['jmxHost'], this.attributes['jmxPort']
        
        if (this.attributes['jmxHost'] && this.attributes['jmxPort']) {
            
            jmxAdapter = new JmxSensorAdapter(this.attributes.jmxHost, this.attributes.jmxPort)

            if (!(jmxAdapter.connect(2*60*1000))) {
				log.error "FAILED to connect JMX to {}", this
                throw new IllegalStateException("failed to completely start $this: JMX not found at $jmxHost:$jmxPort after 60s")
            }

            //TODO get executor from app, then die when finished; why isn't schedule working???
            jmxMonitoringTask = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay({ updateJmxSensors() }, 1000, 1000, TimeUnit.MILLISECONDS)
        }
    }

    public void updateJmxSensors() {
		def reqs = jmxAdapter.getChildrenAttributesWithTotal("jboss.web:type=GlobalRequestProcessor,name=http*")
		reqs.put("timestamp", System.currentTimeMillis())
		updateAttribute(["jmx", "reqs", "global"], reqs)
   }

    public void shutdown() {
        if (jmxMonitoringTask) jmxMonitoringTask.cancel true
        if (location) shutdownInLocation(location)
    }

 
}
