package brooklyn.entity.webapp.jboss

import java.util.List;

import brooklyn.entity.basic.Attributes
import brooklyn.util.SshBasedJavaAppSetup;
import brooklyn.util.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation

public class JBoss6SshSetup extends SshBasedJavaWebAppSetup {
    public static final String DEFAULT_VERSION = "6.0.0.Final"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"jboss"
    public static final String DEFAULT_DEPLOY_SUBDIR = "deploy"
    public static final int DEFAULT_HTTP_PORT = 8080;

    private String jbossVersion
    private int portIncrement
    private String serverProfile
    
    public static JBoss6SshSetup newInstance(JBossNode entity, SshMachineLocation machine) {
        Integer suggestedJbossVersion = entity.getConfig(JBossNode.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(JBossNode.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(JBossNode.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(JBossNode.SUGGESTED_JMX_PORT)
        String suggestedJmxHost = entity.getConfig(JBossNode.SUGGESTED_JMX_HOST)
        Integer suggestedPortIncrement = entity.getConfig(JBossNode.SUGGESTED_PORT_INCREMENT)
        String suggestedServerProfile = entity.getConfig(JBossNode.SUGGESTED_SERVER_PROFILE)
        
        String jbossVersion = suggestedJbossVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"jboss-$jbossVersion")
        String runDir = suggestedRunDir ?: (DEFAULT_RUN_DIR+"/"+"app-"+entity.getApplication()?.id+"/jboss-"+entity.id)
        String deployDir = runDir+"/"+DEFAULT_DEPLOY_SUBDIR
        String serverProfile = suggestedServerProfile ?: "standard"
        
        String jmxHost = suggestedJmxHost ?: machine.getAddress().getHostName()
        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort, DEFAULT_FIRST_JMX_PORT))
        int portIncrement = suggestedPortIncrement ?: 0
        
        JBoss6SshSetup result = new JBoss6SshSetup(entity, machine)
        result.setJmxPort(jmxPort)
        result.setJmxHost(jmxHost)
        result.setJbossVersion(jbossVersion)
        result.setInstallDir(installDir)
        result.setDeployDir(deployDir)
        result.setRunDir(runDir)
        result.setPortIncrement(portIncrement)
        result.setServerProfile(serverProfile)
        
        return result
    }

    public JBoss6SshSetup(JBossNode entity, SshMachineLocation machine) {
        super(entity, machine)
    }
    
    public JBoss6SshSetup setJbossVersion(String val) {
        this.jbossVersion = val
        return this
    }
    
    public JBoss6SshSetup setPortIncrement(int val) {
        this.portIncrement = val
        return this
    }
    
    public JBoss6SshSetup setServerProfile(String val) {
        this.serverProfile = val
        return this
    }
    
    @Override
    protected void postStart() {
        entity.setAttribute(Attributes.JMX_PORT, jmxPort)
        entity.setAttribute(Attributes.JMX_HOST, jmxHost)
        entity.setAttribute(Attributes.HTTP_PORT, DEFAULT_HTTP_PORT+portIncrement)
    }
    
    public List<String> getInstallScript() {
        String url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-${jbossVersion}/jboss-as-distribution-${jbossVersion}.zip?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F${jbossVersion}%2F&ts=1307104229&use_mirror=kent"
        String saveAs  = "jboss-as-distribution-${jbossVersion}"
        
        // Note the -o option to unzip, to overwrite existing files without warning.
        // The JBoss zip file contains lgpl.txt (at least) twice and the prompt to
        // overwrite interrupts the installer.
        makeInstallScript([
	            "curl -L \"${url}\" -o ${saveAs}",
	            "unzip -o ${saveAs}"
            ])
    }

    public List<String> getRunScript() {
        def portGroupName = "ports-brooklyn"
        
        // run.sh must be backgrounded otherwise the script will never return.
        List<String> script = [
            "\$JBOSS_HOME/bin/run.sh -Djboss.service.binding.set=$portGroupName -Djboss.server.base.dir=\$RUN/server -Djboss.server.base.url=file://\$RUN/server -c $serverProfile &",
        ]
        return script
    }
    
    public Map<String, String> getRunEnvironment() {
        // LAUNCH_JBOSS_IN_BACKGROUND relays OS signals sent to the run.sh process to the JBoss process.
        Map<String, String> env = [
	        "LAUNCH_JBOSS_IN_BACKGROUND" : "1",
	        "JBOSS_HOME" : "$installDir",
	        "JAVA_OPTS" : toJavaDefinesString(getJvmStartupProperties()) + " " +
		        "-Djboss.platform.mbeanserver " +
		        "-Djavax.management.builder.initial=org.jboss.system.server.jmx.MBeanServerBuilderImpl " +
		        "-Djava.util.logging.manager=org.jboss.logmanager.LogManager " +
		        "-Dorg.jboss.logging.Logger.pluginClass=org.jboss.logging.logmanager.LoggerPluginImpl",
	        "JBOSS_CLASSPATH" : "$installDir/lib/jboss-logmanager.jar",
	        "RUN" : "$runDir",
        ]
        return env
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() { 
        def host = entity.getAttribute(Attributes.JMX_HOST)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        List<String> script = [
            "$installDir/bin/twiddle.sh --host $host --port $port get \"jboss.system:type=Server\" Started | grep false && exit 1"
        ]
        return script
    }

    public List<String> getConfigScript() {
        /* Configuring ports:
           http://community.jboss.org/wiki/ConfiguringMultipleJBossInstancesOnOneMachine
           http://community.jboss.org/wiki/ConfigurePorts
           http://community.jboss.org/wiki/AS5ServiceBindingManager
           .. changing port numbers with sed is pretty brittle.
        */
        def portGroupName = "ports-brooklyn"
        
        List<String> script = [
            "mkdir -p $runDir/server",
            "cd $runDir/server",
            "cp -r $installDir/server/$serverProfile $serverProfile",
            "cd $serverProfile/conf/bindingservice.beans/META-INF/",
            "BJB=\"bindings-jboss-beans.xml\"",
            "sed -i.bk 's/ports-03/$portGroupName/' \$BJB",
            "sed -i.bk 's/\\<parameter\\>300\\<\\/parameter\\>/\\<parameter\\>$portIncrement\\<\\/parameter\\>/' \$BJB",
        ]
        return script
    }

    /** Assumes file is already in locOnServer.  */
    public List<String> getDeployScript(String locOnServer) {
        String to = runDir + "/" + "webapps"
        List<String> script = [
            "cp $locOnServer $to",
        ]
        return script
    }

    @Override
    public void shutdown() {
        def host = entity.getAttribute(Attributes.JMX_HOST)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        machine.run(out:System.out, [
	            "$installDir/bin/shutdown.sh --host=$host --port=$port -S",
                "sleep 5",
                "ps auxwwww | grep ${entity.id} | awk '{ print \$2 }' | xargs kill -9"
            ])
    }
}
