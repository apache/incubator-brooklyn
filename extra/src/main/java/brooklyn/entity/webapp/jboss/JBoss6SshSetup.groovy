package brooklyn.entity.webapp.jboss

import java.util.List

import brooklyn.entity.basic.Attributes
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaAppSetup
import brooklyn.util.SshBasedJavaWebAppSetup

public class JBoss6SshSetup extends SshBasedJavaWebAppSetup {
    public static final String DEFAULT_VERSION = "6.0.0.Final"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"jboss"
    public static final String DEFAULT_DEPLOY_SUBDIR = "webapps"
    public static final int DEFAULT_HTTP_PORT = 8080;

    private static final portGroupName = "ports-brooklyn"
    private int portIncrement
    private String serverProfile
    private String clusterName
    
    public static JBoss6SshSetup newInstance(JBossServer entity, SshMachineLocation machine) {
        Integer suggestedJbossVersion = entity.getConfig(JBossServer.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(JBossServer.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(JBossServer.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(JBossServer.SUGGESTED_JMX_PORT)
        String suggestedJmxHost = entity.getConfig(JBossServer.SUGGESTED_JMX_HOST)
        Integer suggestedPortIncrement = entity.getConfig(JBossServer.SUGGESTED_PORT_INCREMENT)
        String suggestedServerProfile = entity.getConfig(JBossServer.SUGGESTED_SERVER_PROFILE)
        String suggestedClusterName = entity.getConfig(JBossServer.SUGGESTED_CLUSTER_NAME)
        
        String version = suggestedJbossVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"${version}"+"/"+"jboss-${version}")
        String runDir = suggestedRunDir ?: (BROOKLYN_HOME_DIR+"/"+"${entity.application.id}"+"/"+"jboss-${entity.id}")
        String deployDir = runDir+"/"+DEFAULT_DEPLOY_SUBDIR
        String serverProfile = suggestedServerProfile ?: "standard"
        String clusterName = suggestedClusterName ?: ""
        
        String jmxHost = suggestedJmxHost ?: machine.getAddress().getHostName()
        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort, DEFAULT_FIRST_JMX_PORT))
        int portIncrement = suggestedPortIncrement ?: 0
        
        JBoss6SshSetup result = new JBoss6SshSetup(entity, machine)
        result.setJmxPort(jmxPort)
        result.setJmxHost(jmxHost)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setDeployDir(deployDir)
        result.setRunDir(runDir)
        result.setPortIncrement(portIncrement)
        result.setHttpPort(DEFAULT_HTTP_PORT+portIncrement)
        result.setServerProfile(serverProfile)
        result.setClusterName(clusterName)
        
        return result
    }

    public JBoss6SshSetup(JBossServer entity, SshMachineLocation machine) {
        super(entity, machine)
    }
    
    public JBoss6SshSetup setPortIncrement(int val) {
        portIncrement = val
        return this
    }
    
    public JBoss6SshSetup setServerProfile(String val) {
        serverProfile = val
        return this
    }
    
    public JBoss6SshSetup setClusterName(String val) {
        this.clusterName = val
        return this
    }
    
    @Override
    protected void postStart() {
        entity.setAttribute(Attributes.JMX_PORT, jmxPort)
        entity.setAttribute(Attributes.JMX_HOST, jmxHost)
        entity.setAttribute(Attributes.HTTP_PORT, httpPort)
        entity.setAttribute(JavaWebApp.ROOT_URL, "http://${machine.address.hostAddress}:${httpPort}/")
        entity.setAttribute(Attributes.VERSION, version)
    }
    
    @Override
    public List<String> getInstallScript() {
        String url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-${version}/jboss-as-distribution-${version}.zip?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F${version}%2F&ts=1307104229&use_mirror=kent"
        String saveAs  = "jboss-as-distribution-${version}"
        
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
            "\$JBOSS_HOME/bin/run.sh -Djboss.service.binding.set=${portGroupName} -Djboss.server.base.dir=\$RUN/server " +
                    "-Djboss.server.base.url=file://\$RUN/server -Djboss.messaging.ServerPeerID=${entity.id} " +
                    "-b 0.0.0.0 ${clusterArg} -c ${serverProfile} " + // ${machine.address.hostAddress}
                    ">>\$RUN/console 2>&1 </dev/null &",
        ]
        return script
    }
    
    @Override
    protected Map getJavaConfigOptions() {
        Map<String, String> options = [
            "jboss.platform.mbeanserver" : null,
            "javax.management.builder.initial" : "org.jboss.system.server.jmx.MBeanServerBuilderImpl",
            "java.util.logging.manager" : "org.jboss.logmanager.LogManager",
            "org.jboss.logging.Logger.pluginClass" : "org.jboss.logging.logmanager.LoggerPluginImpl",
        ]
        return options
    }
 
    public Map<String, String> getRunEnvironment() {
        // LAUNCH_JBOSS_IN_BACKGROUND relays OS signals sent to the run.sh process to the JBoss process.
        Map<String, String> env = [
	        "LAUNCH_JBOSS_IN_BACKGROUND" : "1",
	        "JBOSS_HOME" : "${installDir}",
	        "JAVA_OPTS" : toJavaDefinesString(getJvmStartupProperties())+" -Xmx256m -Xms128m",
	        "JBOSS_CLASSPATH" : "\$JBOSS_HOME/lib/jboss-logmanager.jar",
	        "RUN" : "${runDir}",
        ]
        return env
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() { 
        def host = entity.getAttribute(Attributes.JMX_HOST)
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
           http://community.jboss.org/wiki/AS5ServiceBindingManager
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
        def host = entity.getAttribute(Attributes.JMX_HOST)
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
