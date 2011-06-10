package org.overpaas.web.jboss

import org.overpaas.core.locations.SshMachineLocation;
import org.overpaas.core.types.ActivitySensor;
import org.overpaas.web.tomcat.SshBasedJavaAppSetup;
import groovy.transform.InheritConstructors

import java.util.Map
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import org.overpaas.core.locations.SshMachineLocation
import org.overpaas.core.locations.SshMachineLocation.SshBasedJavaAppSetup
import org.overpaas.core.types.ActivitySensor
import org.overpaas.core.decorators.GroupEntity
import org.overpaas.core.decorators.Location
import org.overpaas.core.decorators.Startable
import org.overpaas.core.types.common.AbstractOverpaasEntity
import org.overpaas.core.types.common.EntityStartUtils
import org.overpaas.util.JmxSensorEffectorTool

@InheritConstructors
public class JBossNode extends AbstractOverpaasEntity implements Startable {

    JmxSensorEffectorTool jmxTool;

    public void start(Map properties=[:], GroupEntity parent=null, Location loc=null) {
        EntityStartUtils.startEntity(properties, this, parent, loc);
        println "Started."
        +" jmxHost is "+this.properties['jmxHost']
		+" and jmxPort is "+this.properties['jmxPort'];
        
        if (this.properties['jmxHost'] && this.properties['jmxPort']) {
            jmxTool = new JmxSensorEffectorTool(this.properties.jmxHost, this.properties.jmxPort)
            if (!(jmxTool.connect(2*60*1000))) {
                println "FAILED to connect JMX to $this"
                throw new IllegalStateException("failed to completely start $this: "
                    + "JMX not found at $jmxHost:$jmxPort after 60s")
            }

            //TODO get executor from app, then die when finished; why isn't schedule working???
            Executors.newScheduledThreadPool(1)
            .scheduleWithFixedDelay({ getJmxSensors() }, 1000, 1000, TimeUnit.MILLISECONDS)
        }
    }

    private void temp_testingJMX() {
        def mod_cluster_jmx = "jboss.web:service=ModCluster,provider=LoadBalanceFactor";
        def attrs = jmxTool.getAttributes(mod_cluster_jmx);
        println attrs
    }

    public double getJmxSensors() {
        def reqs = jmxTool.getChildrenAttributesWithTotal
            ("Catalina:type=GlobalRequestProcessor,name=\"*\"")
        reqs.put "timestamp", System.currentTimeMillis()
        Map prev = activity.update(["jmx", "reqs", "global"], reqs)
        double diff = (reqs?.totals?.requestCount ?: 0) - (prev?.totals?.requestCount ?: 0)
        long dt = (reqs?.timestamp ?: 0) - (prev?.timestamp ?: 0)
        if (dt <= 0 || dt > 60*1000) diff = -1; else diff = ((double)1000.0*diff)/dt
        println "computed $diff reqs/sec over $dt millis "
            + "for JMX JBoss process at $jmxHost:$jmxPort"
        activity.update(REQUESTS_PER_SECOND, diff)
        diff
    }

    public static final ActivitySensor<Integer> REQUESTS_PER_SECOND = [
        "Reqs/Sec",
        "jmx.reqs.persec.RequestCount",
        Double
    ]

    public void shutdown() {
        if (jmxMonitoringTask) jmxMonitoringTask.cancel true
        shutdownInLocation(location)
    }

    public static class JBossSshSetup extends SshBasedJavaAppSetup {
        String version = "6.0.0.Final"
        String installDir = new File(new File(installsBaseDir), "jboss-$version").getPath()

        def JBOSS_HOME = installDir

        JBossNode entity
        String runDir

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
        
        public String getDeployScript(String filename) {
            ""
        }

        public void shutdown(SshMachineLocation loc) {
            //          println "invoking shutdown script"
            //we use kill -9 rather than shutdown.sh because the latter is not 100% reliable
            def result =  loc.run(out: System.out,
                                  "cd $runDir && echo killing process `cat pid.txt` on `hostname` "
                                  + "&& kill -9 `cat pid.txt` && rm pid.txt ; exit")
            if (result) println "WARNING: non-zero result code terminating "+entity+": "+result
            //          println "done invoking shutdown script"
        }
    }



}
