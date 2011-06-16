package brooklyn.entity.webapp.jboss

import groovy.transform.InheritConstructors

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.AttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityStartUtils

/**
 * JBoss web application server.
 */
@InheritConstructors
public class JBossNode extends AbstractEntity implements Startable {
    public static final AttributeSensor<Integer> REQUESTS_PER_SECOND = [ "Reqs/Sec", "jmx.reqs.persec.RequestCount", Double ]

    JmxSensorAdapter jmxAdapter;

	//TODO hack reference (for shutting down), need a cleaner way -- e.g. look up in the app's executor service for this entity
	ScheduledFuture jmxMonitoringTask;

    public void start(Map properties=[:], Group parent=null, Location loc=null) {
        EntityStartUtils.startEntity(properties, this, parent, loc);
		log.debug "started... jmxHost is {} and jmxPort is {}", this.properties['jmxHost'], this.properties['jmxPort']
        
        if (this.properties['jmxHost'] && this.properties['jmxPort']) {
            jmxAdapter = new JmxSensorAdapter(this.properties.jmxHost, this.properties.jmxPort)
            if (!(jmxAdapter.connect(2*60*1000))) {
				log.error "FAILED to connect JMX to {}", this
                throw new IllegalStateException("failed to completely start $this: JMX not found at $jmxHost:$jmxPort after 60s")
            }

            //TODO get executor from app, then die when finished; why isn't schedule working???
            jmxMonitoringTask = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay({ getJmxSensors() }, 1000, 1000, TimeUnit.MILLISECONDS)
        }
    }

    private void temp_testingJMX() {
        def mod_cluster_jmx = "jboss.web:service=ModCluster,provider=LoadBalanceFactor";
        def attrs = jmxAdapter.getAttributes(mod_cluster_jmx);
        println attrs
    }

    public double getJmxSensors() {
        def reqs = jmxAdapter.getChildrenAttributesWithTotal("Catalina:type=GlobalRequestProcessor,name=\"*\"")
        reqs.put "timestamp", System.currentTimeMillis()
        //update to explicit location in activity map, but not linked to sensor so probably shouldn't be used too widely 
        Map prev = activity.update(["jmx","reqs","global"], reqs)
//        old = attributes['jmx.reqs.global']
        
        double diff = (reqs?.totals?.requestCount ?: 0) - (prev?.totals?.requestCount ?: 0)
        long dt = (reqs?.timestamp ?: 0) - (prev?.timestamp ?: 0)
        if (dt <= 0 || dt > 60*1000) diff = -1; else diff = ((double)1000.0*diff)/dt
        log.debug "computed $diff reqs/sec over $dt millis for JMX jboss process at $jmxHost:$jmxPort"
//        attributes['jmx.reqs.global'] = diff
        
        //is a sensor, should generate update events against subscribers
        activity.update(REQUESTS_PER_SECOND, diff)
        diff
    }

    public void shutdown() {
        if (jmxMonitoringTask) jmxMonitoringTask.cancel true
        shutdownInLocation(location)
    }

    public static class JBossSshSetup extends SshBasedJavaWebAppSetup {
        String version = "6.0.0.Final"
        String installDir = new File(new File(installsBaseDir), "jboss-$version").getPath()

        def JBOSS_HOME = installDir

        JBossNode entity
        String runDir
        
        public JBossSshSetup(JBossNode entity) {
            super(entity)
            this.entity = entity
            runDir = appBaseDir + "/" + "jboss-"+entity.id
        }

        public String getInstallScript() {
            def url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-$version/jboss-as-distribution-$version.zip?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F$version%2F&ts=1307104229&use_mirror=kent"
            makeInstallScript(
                    "curl -L " + url + "-o jboss-as-distribution-$version",
                    "unzip jboss-as-distribution-6.0.0.Final.zip"
                    )
        }

        public String getRunScript() {
            // TODO: Config. Run using correct pre-setup jboss config dir. Also deal with making these.
            // Configuring ports:
            // http://docs.jboss.org/jbossas/docs/Server_Configuration_Guide/beta422/html/Additional_Services-Services_Binding_Management.html
            // http://docs.jboss.org/jbossas/6/Admin_Console_Guide/en-US/html/Administration_Console_User_Guide-Port_Configuration.html

            """
            $runDir/bin/run.sh
            """
        }

		//TODO not working; need to write above to a pid.txt file, then copy (or refactor to share) code from TomcatNode.getCheckRunningScript         
		/** script to return 1 if pid in runDir is running, 0 otherwise */
		public String getCheckRunningScript() { """\
exit 0
"""
		}

        public String getDeployScript(String filename) {
            ""
        }

        public void shutdown(SshMachineLocation loc) {
            //we use kill -9 rather than shutdown.sh because the latter is not 100% reliable
            def result =  loc.run(out: System.out,
                                  "cd $runDir && echo killing process `cat pid.txt` on `hostname` "
                                  + "&& kill -9 `cat pid.txt` && rm pid.txt ; exit")
			if (result) log.info "non-zero result code terminating {}: {}", entity, result
        }
    }
}
