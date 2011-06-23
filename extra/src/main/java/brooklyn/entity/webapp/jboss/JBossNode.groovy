package brooklyn.entity.webapp.jboss

import groovy.transform.InheritConstructors

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.AttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityStartUtils

/**
 * JBoss web application server.
 */
//@InheritConstructors
public class JBossNode extends AbstractEntity implements Startable {
    
    public static final AttributeSensor<Integer> REQUESTS_PER_SECOND = [ "Reqs/Sec", "jmx.reqs.persec.RequestCount", Double ]

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

    private void temp_testingJMX() {
        def mod_cluster_jmx = "jboss.web:service=ModCluster,provider=LoadBalanceFactor";
        def attrs = jmxAdapter.getAttributes(mod_cluster_jmx);
        println attrs
    }

    public void updateJmxSensors() {
    }

    public void shutdown() {
        if (jmxMonitoringTask) jmxMonitoringTask.cancel true
        if (location) shutdownInLocation(location)
    }

 
}
