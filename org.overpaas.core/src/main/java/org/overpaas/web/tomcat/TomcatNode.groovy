package org.overpaas.web.tomcat

import groovy.transform.InheritConstructors;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.overpaas.decorators.Startable;
import org.overpaas.entities.AbstractEntity;
import org.overpaas.entities.Group;
import org.overpaas.locations.SshBasedJavaAppSetup;
import org.overpaas.locations.SshMachineLocation;
import org.overpaas.types.ActivitySensor;
import org.overpaas.types.Location;
import org.overpaas.util.EntityStartUtils;
import org.overpaas.util.JmxSensorEffectorTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An entity that represents a single Tomcat instance.
 * 
 * @author Richard Downer <richard.downer@cloudsoftcorp.com>
 */
@InheritConstructors
public class TomcatNode extends AbstractEntity implements Startable {
	private static final Logger logger = LoggerFactory.getLogger(TomcatNode.class)
	
	public static final ActivitySensor<Integer> REQUESTS_PER_SECOND = [ "Reqs/Sec", "webapp.reqs.persec.RequestCount", Double ]

	static {
		TomcatNode.metaClass.startInLocation = { Group parent, SshMachineLocation loc -> new Tomcat7SshSetup(delegate).start loc }
		TomcatNode.metaClass.shutdownInLocation = { SshMachineLocation loc -> new Tomcat7SshSetup(delegate).shutdown loc }
        TomcatNode.metaClass.deploy = { String file, SshMachineLocation loc -> 
            new Tomcat7SshSetup(delegate).deploy(new File(file), loc)
		}
	}

	JmxSensorEffectorTool jmxTool;
	
	//TODO hack reference (for shutting down), need a cleaner way -- e.g. look up in the app's executor service for this entity
	ScheduledFuture jmxMonitoringTask;

	public void start(Map properties=[:], Group parent=null, Location location=null) {
		EntityStartUtils.startEntity properties, this, parent, location
		logger.trace "started... jmxHost is {} and jmxPort is {}", this.properties['jmxHost'], this.properties['jmxPort']
		
        if (this.properties['jmxHost'] && this.properties['jmxPort']) {
            jmxTool = new JmxSensorEffectorTool(this.properties.jmxHost, this.properties.jmxPort)
			if (!(jmxTool.connect(60*1000))) {
				logger.error "FAILED to connect JMX to {}", this
				throw new IllegalStateException("failed to completely start $this: JMX not found at $jmxHost:$jmxPort after 60s")
			}
			
			//TODO get executor from app, then die when finished; why isn't schedule working???
			//e.g. getApplication().getExecutors().
			jmxMonitoringTask = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay({ getJmxSensors() }, 1000, 1000, TimeUnit.MILLISECONDS)
		}
        if (this.war) {
            def deployLoc = location ?: this.location
            logger.trace "Deploying {} to {}", this.war, deployLoc
            this.deploy(this.war, deployLoc)
            logger.trace "Deployed {} to {}", this.war, deployLoc
        }
	}
	
	public double getJmxSensors() {
		def reqs = jmxTool.getChildrenAttributesWithTotal("Catalina:type=GlobalRequestProcessor,name=\"*\"")
		reqs.put "timestamp", System.currentTimeMillis()
		//update to explicit location in activity map, but not linked to sensor so probably shouldn't be used too widely 
		Map prev = activity.update(["jmx","reqs","global"], reqs)
		double diff = (reqs?.totals?.requestCount ?: 0) - (prev?.totals?.requestCount ?: 0)
		long dt = (reqs?.timestamp ?: 0) - (prev?.timestamp ?: 0)
		if (dt <= 0 || dt > 60*1000) diff = -1; else diff = ((double)1000.0*diff)/dt
		logger.trace "computed $diff reqs/sec over $dt millis for JMX tomcat process at $jmxHost:$jmxPort"
		
		//is a sensor, should generate update events against subscribers
		activity.update(REQUESTS_PER_SECOND, diff)
		diff
	}
	
	@Override
	public Collection<String> toStringFieldsToInclude() {
		return super.toStringFieldsToInclude() + ['tomcatHttpPort', 'jmxPort']
	}
 
//	public void startInLocation(GroupEntity parent, Location loc) {
//		TomcatNode tc = new TomcatNode([:], parent);
//		loc.logEvent("starting tomcat "+tc, parent)
//		tc
//	}

	public void shutdown() {
		if (jmxMonitoringTask) jmxMonitoringTask.cancel true
		shutdownInLocation(location)
	}

	public static class Tomcat7SshSetup extends SshBasedJavaAppSetup {
		String version = "7.0.14"
		String installDir = installsBaseDir+"/"+"tomcat"+"/"+"apache-tomcat-$version"
		
		TomcatNode entity
		String runDir
		
		public Tomcat7SshSetup(TomcatNode entity) {
			super(entity)
			this.entity = entity
			runDir = appBaseDir + "/" + "tomcat-"+entity.id
		}
		public String getInstallScript() {
			makeInstallScript(
				"wget http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz",
				"tar xvzf apache-tomcat-${version}.tar.gz")
		}
		
		/** creates the directories tomcat needs to run in a different location from where it is installed,
		 * renumber http and shutdown ports, and delete AJP connector, then start with JMX enabled
		 */
		public String getRunScript() { """\
mkdir -p $runDir && \\
cd $runDir && \\
export CATALINA_BASE=$runDir && \\
mkdir conf && \\
mkdir logs && \\
mkdir webapps && \\
cp $installDir/conf/{server,web}.xml conf/ && \\
sed -i.bk s/8080/${getTomcatHttpPort()}/g conf/server.xml && \\
sed -i.bk s/8005/${getTomcatShutdownPort()}/g conf/server.xml && \\
sed -i.bk /8009/D conf/server.xml && \\
export CATALINA_OPTS=""" + "\"" + toJavaDefinesString(getJvmStartupProperties())+ """\" && \\
export CATALINA_PID="pid.txt"
$installDir/bin/startup.sh
exit
"""
		}
        
        /** Assumes file is already in locOnServer. */
        public String getDeployScript(String locOnServer) {
            File to = new File(new File(runDir), "webapps")
            """\
cp $locOnServer $to
exit"""
        }
				
		public int getTomcatHttpPort() {
			getNextValue("tomcatHttpPort", 8080)
		}
		/** tomcat insists on having a port you can connect to for the sole purpose of shutting it down;
		 * don't see an easy way to disable it; causes collisions in its default location of 8005,
		 * so moving it to some anonymous high-numbered location
		 */
		public int getTomcatShutdownPort() {
			getNextValue("tomcatShutdownPort", 38180)
		}
	
		public void shutdown(SshMachineLocation loc) {

			logger.trace "invoking shutdown script"
			//we use kill -9 rather than shutdown.sh because the latter is not 100% reliable
			def result =  loc.run(out: System.out, "cd $runDir && echo killing process `cat pid.txt` on `hostname` && kill -9 `cat pid.txt` && rm -f pid.txt ; exit")
			if (result) println "WARNING: non-zero result code terminating "+entity+": "+result
			logger.trace "done invoking shutdown script"
		}
	}
	
}
