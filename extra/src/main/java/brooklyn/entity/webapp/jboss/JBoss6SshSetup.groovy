package brooklyn.entity.webapp.jboss

import java.util.List

import brooklyn.entity.basic.Attributes
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaWebAppSetup

public class JBoss6SshSetup extends SshBasedJavaWebAppSetup {
    public static final String DEFAULT_VERSION = "6.0.0.Final"
    public static final String DEFAULT_INSTALL_DIR = "$DEFAULT_INSTALL_BASEDIR/jboss"
    public static final int DEFAULT_HTTP_PORT = 8080;

    private static final portGroupName = "ports-brooklyn"
    private int portIncrement
    private String serverProfile
    private String clusterName

    public static JBoss6SshSetup newInstance(JBoss6Server entity, SshMachineLocation machine) {
        Integer suggestedJbossVersion = entity.getConfig(JBoss6Server.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(JBoss6Server.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(JBoss6Server.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(JBoss6Server.JMX_PORT)
        Integer portIncrement = entity.getConfig(JBoss6Server.PORT_INCREMENT)
        String serverProfile = entity.getConfig(JBoss6Server.SERVER_PROFILE)
        String clusterName = entity.getConfig(JBoss6Server.CLUSTER_NAME)
		
        Map<String,Map<String,String>> propFilesToGenerate = entity.getConfig(JBoss6Server.PROPERTY_FILES) ?: [:]
        
        String version = suggestedJbossVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/${version}/jboss-${version}")
//        String runDir = suggestedRunDir ?: (BROOKLYN_HOME_DIR+"/${entity.application.id}/jboss6-${entity.id}")
//        String deployDir = "${runDir}/server/${serverProfile}/deploy"
//        String logFileLocation = "$runDir/server/standard/log/server.log"

        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort))
        
        JBoss6SshSetup result = new JBoss6SshSetup(entity, machine)
		
        result.setPortIncrement(portIncrement)
        result.setServerProfile(serverProfile)
        result.setClusterName(clusterName)
		
        result.setJmxPort(jmxPort)
        result.setHttpPort(DEFAULT_HTTP_PORT+portIncrement)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setDeployDir("${result.runDir}/server/${result.serverProfile}/deploy")
//        result.setRunDir(runDir)
        result.setEnvironmentPropertyFiles(propFilesToGenerate)
		
		entity.setAttribute(Attributes.LOG_FILE_LOCATION, "${result.runDir}/server/standard/log/server.log")

        return result
    }

    public JBoss6SshSetup(JBoss6Server entity, SshMachineLocation machine) {
        super(entity, machine)
    }
    
    public void setPortIncrement(int val) {
        portIncrement = val
    }
    
    public void setServerProfile(String val) {
        serverProfile = val
    }
    
    public void setClusterName(String val) {
        this.clusterName = val
    }
    
    @Override
    public List<String> getInstallScript() {
        String url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-${version}/jboss-as-distribution-${version}.zip?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F${version}%2F&ts=1307104229&use_mirror=kent"
        String saveAs  = "jboss-as-distribution-${version}.tgz"
        
        // Note the -o option to unzip, to overwrite existing files without warning.
        // The JBoss zip file contains lgpl.txt (at least) twice and the prompt to
        // overwrite interrupts the installer.
        makeInstallScript([
            "curl -L \"${url}\" -o ${saveAs}",
            "unzip -o ${saveAs}"
        ])
    }

    public List<String> getRunScript() {
        def clusterArg = (clusterName == "") ? "" : "-g $clusterName"
        // run.sh must be backgrounded otherwise the script will never return.
        List<String> script = [
            "${installDir}/bin/run.sh -Djboss.service.binding.set=${portGroupName} -Djboss.server.base.dir=\$RUN/server " +
                    "-Djboss.server.base.url=file://\$RUN/server -Djboss.messaging.ServerPeerID=${entity.id} " +
                    "-b 0.0.0.0 ${clusterArg} -c ${serverProfile} " +
                    ">>\$RUN/console 2>&1 </dev/null &"
        ]
        return script
    }
    
    @Override
    protected Map getCustomJavaSystemProperties() {
        Map<String, String> options = [
            "jboss.platform.mbeanserver" : null,
            "javax.management.builder.initial" : "org.jboss.system.server.jmx.MBeanServerBuilderImpl",
            "java.util.logging.manager" : "org.jboss.logmanager.LogManager",
            "org.jboss.logging.Logger.pluginClass" : "org.jboss.logging.logmanager.LoggerPluginImpl",
            "jboss.boot.server.log.dir" : "${runDir}/server/${serverProfile}/log"
        ]
        return options
    }
 
    public Map<String, String> getShellEnvironment() {
        // LAUNCH_JBOSS_IN_BACKGROUND relays OS signals sent to the run.sh process to the JBoss process.
        super.getShellEnvironment() +
		[
	        "LAUNCH_JBOSS_IN_BACKGROUND" : "1",
	        "JBOSS_CLASSPATH" : "${installDir}/lib/jboss-logmanager.jar",
	        "RUN" : "${runDir}",
        ]
    }
	
	@Override
	protected List<String> getCustomJavaConfigOptions() {
		return ["-Xms200m", "-Xmx800m", "-XX:MaxPermSize=400m"]
	}


    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() { 
        def host = entity.getAttribute(Attributes.HOSTNAME)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        List<String> script = [
            "${installDir}/bin/twiddle.sh --host ${host} --port ${port} get \"jboss.system:type=Server\" Started | grep false && exit 1"
        ]
        return script
    }

    @Override
    public List<String> getConfigScript() {
        /* Configuring ports:
           http://community.jboss.org/wiki/ConfiguringMultipleJBossInstancesOnOneMachine
           http://community.jboss.org/wiki/ConfigurePorts
           .. changing port numbers with sed is pretty brittle.
        */
        List<String> script = [
            "mkdir -p ${runDir}/server",
            "cd ${runDir}/server",
            "cp -r ${installDir}/server/${serverProfile} ${serverProfile}",
            "cd ${serverProfile}/conf/bindingservice.beans/META-INF/",
            "BJB=\"bindings-jboss-beans.xml\"",
            "sed -i.bk 's/ports-03/${portGroupName}/' \$BJB",
            "sed -i.bk 's/<parameter>300<\\/parameter>/<parameter>${portIncrement}<\\/parameter>/' \$BJB",
        ]
        return script
    }

    @Override
    public List<String> getShutdownScript() {
        def host = entity.getAttribute(Attributes.HOSTNAME)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        List<String> script = [
	            "${installDir}/bin/shutdown.sh --host ${host} --port ${port} -S",
            ]
        return script
    }

    @Override
    public List<String> getRestartScript() { [] }

    @Override
    protected void postShutdown() {
        machine.releasePort(jmxPort)
        machine.releasePort(httpPort);
    }
}
