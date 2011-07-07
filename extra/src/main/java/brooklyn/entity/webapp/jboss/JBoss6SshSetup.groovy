package brooklyn.entity.webapp.jboss

import brooklyn.entity.basic.Attributes
import brooklyn.util.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation

public class JBoss6SshSetup extends SshBasedJavaWebAppSetup {
    
    public static final String DEFAULT_VERSION = "6.0.0.Final"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"jboss/"
    public static final String DEFAULT_DEPLOY_SUBDIR = "deploy"
    public static final int DEFAULT_HTTP_PORT = 8080;

    private String jbossVersion
    private int portIncrement
    
    public static JBoss6SshSetup newInstance(JBossNode entity, SshMachineLocation machine) {
        Integer suggestedJbossVersion = entity.getConfig(JBossNode.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(JBossNode.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(JBossNode.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(JBossNode.SUGGESTED_JMX_PORT)
        String suggestedJmxHost = entity.getConfig(JBossNode.SUGGESTED_JMX_HOST)
        Integer suggestedPortIncrement = entity.getConfig(JBossNode.SUGGESTED_PORT_INCREMENT)
        
        String jbossVersion = suggestedJbossVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"jboss-$jbossVersion")
        String runDir = suggestedRunDir ?: (DEFAULT_RUN_DIR+"/"+"app-"+entity.getApplication()?.id+"/jboss-"+entity.id)
        String deployDir = runDir+"/"+DEFAULT_DEPLOY_SUBDIR
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
    
    @Override
    protected void postStart() {
        entity.setAttribute(Attributes.JMX_PORT, jmxPort)
        entity.setAttribute(Attributes.JMX_HOST, jmxHost)
        entity.setAttribute(Attributes.HTTP_PORT, DEFAULT_HTTP_PORT+portIncrement)
    }
    
    public String getInstallScript() {
        String url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-$jbossVersion/jboss-as-distribution-${jbossVersion}.zip?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F$jbossVersion%2F&ts=1307104229&use_mirror=kent"
        String saveAs  = "jboss-as-distribution-$jbossVersion"
        
        // Note the -o option to unzip, to overwrite existing files without warning.
        // The JBoss zip file contains lgpl.txt (at least) twice and the prompt to
        // overwrite interrupts the installer.
        makeInstallScript(
            "curl -L \"$url\" -o $saveAs",
            "unzip -o $saveAs"
        )
    }

    public String getRunScript() {
        // Notes:
        // LAUNCH_JBOSS_IN_BACKGROUND relays OS signals sent to the run.sh process to the JBoss process.
        // run.sh must be backgrounded otherwise the script will never return.
        /* Configuring ports:
           http://community.jboss.org/wiki/ConfiguringMultipleJBossInstancesOnOneMachine
           http://community.jboss.org/wiki/ConfigurePorts
           http://community.jboss.org/wiki/AS5ServiceBindingManager
           .. changing port numbers with sed is pretty brittle.
        */
        def portGroupName = "ports-brooklyn"
        def serverProfile = "default"
"""mkdir -p $runDir/server && \\
cd $runDir/server && \\
cp -r $installDir/server/$serverProfile $serverProfile && \\
cd $serverProfile/conf/bindingservice.beans/META-INF/ && \\
BJB="bindings-jboss-beans.xml" && \\
sed -i.bk 's/ports-03/$portGroupName/' \$BJB && \\
sed -i.bk 's/\\<parameter\\>300\\<\\/parameter\\>/\\<parameter\\>$portIncrement\\<\\/parameter\\>/' \$BJB && \\
export LAUNCH_JBOSS_IN_BACKGROUND=1 && \\
export JBOSS_HOME=$installDir && \\
export JAVA_OPTS=""" + "\"" + toJavaDefinesString(getJvmStartupProperties()) + "\"" + """ && \\
JAVA_OPTS="\$JAVA_OPTS -Djboss.platform.mbeanserver" && \\
JAVA_OPTS="\$JAVA_OPTS -Djavax.management.builder.initial=org.jboss.system.server.jmx.MBeanServerBuilderImpl" && \\
JAVA_OPTS="\$JAVA_OPTS -Djava.util.logging.manager=org.jboss.logmanager.LogManager" && \\
JAVA_OPTS="\$JAVA_OPTS -Dorg.jboss.logging.Logger.pluginClass=org.jboss.logging.logmanager.LoggerPluginImpl" && \\
export JBOSS_CLASSPATH="$installDir/lib/jboss-logmanager.jar" && \\
$installDir/bin/run.sh -Djboss.service.binding.set=$portGroupName -Djboss.server.base.dir=$runDir/server -Djboss.server.base.url=file://$runDir/server -c $serverProfile &
sleep 30
exit"""
    }

    /** script to return 1 if pid in runDir is running, 0 otherwise */
    public String getCheckRunningScript() { 
        def host = entity.getAttribute(Attributes.JMX_HOST)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        "$installDir/bin/twiddle.sh --host $host --port $port get \"jboss.system:type=Server\" Started; exit"
    }

    public String getDeployScript(String locOnServer) {
        ""
    }

    public void shutdown() {
        def host = entity.getAttribute(Attributes.JMX_HOST)
        def port = entity.getAttribute(Attributes.JMX_PORT)
        machine.run("$installDir/bin/shutdown.sh --host=$host --port=$port -S; exit", out: System.out)
    }
}