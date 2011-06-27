package brooklyn.entity.webapp.jboss

import static brooklyn.entity.basic.AttributeDictionary.*
import static brooklyn.entity.basic.ConfigKeyDictionary.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AttributeDictionary
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
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

    public static final AttributeSensor<Integer> JMX_PORT = AttributeDictionary.JMX_PORT;
    public static final AttributeSensor<String> JMX_HOST = AttributeDictionary.JMX_HOST;
    public static final BasicAttributeSensor<Integer> REQUESTS_PER_SECOND = [ Double, "jmx.reqs.persec.RequestCount", "Reqs/Sec" ]
	public static final BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "jmx.reqs.global.totals.errorCount", "Error count" ]
	public static final BasicAttributeSensor<Integer> MAX_PROCESSING_TIME = [ Integer, "jmx.reqs.global.totals.maxTime", "Max processing time" ]
	public static final BasicAttributeSensor<Integer> REQUEST_COUNT = [ Integer, "jmx.reqs.global.totals.requestCount", "Request count" ]
	public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = [ Integer, "jmx.reqs.global.totals.processingTime", "Total processing time" ]

    transient JmxSensorAdapter jmxAdapter;
    
    public void startInLocation(SshMachineLocation loc) {
        def setup = new JBoss6SshSetup(this)
        setup.start loc
        locations.add(loc)
        
        //TODO extract to a utility method
        //TODO use same code with TomcatNode, or use extract new abstract superclass
        log.debug "waiting to ensure $this doesn't abort prematurely"
        long startTime = System.currentTimeMillis()
        boolean isRunningResult = false;
        while (!isRunningResult && System.currentTimeMillis() < startTime+60000) {
            Thread.sleep 3000
            isRunningResult = setup.isRunning(loc)
            log.debug "checked jboss $this, running result $isRunningResult"
        }
        if (!isRunningResult) throw new IllegalStateException("$this aborted soon after startup")
        
        log.warn "not setting http port for successful jboss execution"
    }
    
    public void start(Collection<Location> locs) {
        EntityStartUtils.startEntity(this, locs);
        
        if (!(getAttribute(JMX_HOST) && getAttribute(JMX_PORT)))
            throw new IllegalStateException("JMX is not available")
        
        log.debug "started jboss server: jmxHost {} and jmxPort {}", getAttribute(JMX_HOST), getAttribute(JMX_PORT)
        
		jmxAdapter = new JmxSensorAdapter(this, 60*1000)
		// Add JMX sensors
		jmxAdapter.addSensor(ERROR_COUNT, "jboss.web:type=GlobalRequestProcessor,name=http-*", "errorCount")
		jmxAdapter.addSensor(REQUEST_COUNT, "jboss.web:type=GlobalRequestProcessor,name=http-*", "requestCount")
		jmxAdapter.addSensor(TOTAL_PROCESSING_TIME, "jboss.web:type=GlobalRequestProcessor,name=http-*", "processingTime")
		// jmxAdapter.addSensor(REQUESTS_PER_SECOND, { computeReqsPerSec() }, 100L)

    }

    public void shutdown() {
		if (jmxAdapter) jmxAdapter.disconnect();
        if (location) { 
			new JBoss6SshSetup(this).shutdown loc;
        }
    }
}
