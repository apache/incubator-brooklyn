package brooklyn.entity.webapp.jboss

import brooklyn.location.basic.SshBasedJavaWebAppSetup;
import brooklyn.location.basic.SshMachineLocation;

public class JBoss6SshSetup extends SshBasedJavaWebAppSetup {
    
    String version = "6.0.0.Final"
    String saveAs  = "jboss-as-distribution-$version"
    String installDir = "$installsBaseDir/jboss-$version"
    String runDir

    public JBoss6SshSetup(JBossNode entity) {
        super(entity)
        runDir = appBaseDir + "/" + "jboss-"+entity.id
    }

    public String getInstallScript() {
        def url = "http://downloads.sourceforge.net/project/jboss/JBoss/JBoss-$version/jboss-as-distribution-${version}.zip?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fjboss%2Ffiles%2FJBoss%2F$version%2F&ts=1307104229&use_mirror=kent"
        // Note the -o option to unzip, to overwrite existing files without warning.
        // The JBoss zip file contains lgpl.txt (at least) twice and the prompt to
        // overwrite breaks the installer.
        makeInstallScript(
            "curl -L \"$url\" -o $saveAs",
            "unzip -o $saveAs"
        )
    }

    public String getRunScript() {
        // Notes:
        // LAUNCH_JBOSS_IN_BACKGROUND relays OS signals sent to the run.sh process to the JBoss process.
        // run.sh must be backgrounded otherwise the script will never return.
		// Assume one instance of JBoss per machine (why would you run two?), 
		// this setup uses the default ports..

		// Configuring ports:
		// http://community.jboss.org/wiki/ConfiguringMultipleJBossInstancesOnOneMachine
		// http://community.jboss.org/wiki/ConfigurePorts
		// http://community.jboss.org/wiki/AS5ServiceBindingManager
		
"""
mkdir -p $runDir/server
cd $runDir/server
export JBOSS_HOME=$installDir
cp -r $installDir/server/default default
export LAUNCH_JBOSS_IN_BACKGROUND=1
export JAVA_OPTS=""" + "\"" + toJavaDefinesString(getJvmStartupProperties()) + """\"
JAVA_OPTS="\$JAVA_OPTS -Djboss.platform.mbeanserver"
JAVA_OPTS="\$JAVA_OPTS -Djavax.management.builder.initial=org.jboss.system.server.jmx.MBeanServerBuilderImpl"
JAVA_OPTS="\$JAVA_OPTS -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
JAVA_OPTS="\$JAVA_OPTS -Dorg.jboss.logging.Logger.pluginClass=org.jboss.logging.logmanager.LoggerPluginImpl"
export JBOSS_CLASSPATH="$installDir/lib/jboss-logmanager.jar"
$installDir/bin/run.sh -Djboss.server.base.dir=$runDir/server \
					   -Djboss.server.base.url=file://$runDir/server -c default &
exit
"""
    }

    //TODO not working; need to write above to a pid.txt file, then copy (or refactor to share) code from TomcatNode.getCheckRunningScript
    /** script to return 1 if pid in runDir is running, 0 otherwise */
    public String getCheckRunningScript() { 
		def port = entity.attributes.jmxPort
		def host = entity.attributes.jmxHost
"""
$installDir/bin/twiddle.sh -s service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi \
	get "jboss.system:type=Server" Started 
exit
"""
    }

    public String getDeployScript(String filename) {
        ""
    }

    public void shutdown(SshMachineLocation loc) {
        def host = entity.attributes.jmxHost
		def port = entity.attributes.jmxPort
        loc.run("$installDir/bin/shutdown.sh --host=$host --port=$port -S; exit", out: System.out)
    }
}