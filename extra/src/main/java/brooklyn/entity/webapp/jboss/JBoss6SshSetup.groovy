package brooklyn.entity.webapp.jboss

import brooklyn.location.basic.SshBasedJavaWebAppSetup;
import brooklyn.location.basic.SshMachineLocation;

public class JBoss6SshSetup extends SshBasedJavaWebAppSetup {
    
    String version = "6.0.0.Final"
    String saveAs  = "jboss-as-distribution-$version"
    String installDir = "$installsBaseDir/jboss-$version"
    String runDir
	int jBossPortIncrement = 0

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
		/* Configuring ports:
		   http://community.jboss.org/wiki/ConfiguringMultipleJBossInstancesOnOneMachine
		   http://community.jboss.org/wiki/ConfigurePorts
	       http://community.jboss.org/wiki/AS5ServiceBindingManager
	       .. changing port numbers with sed is pretty brittle.
		*/
		def portGroupName = "ports-brooklyn"
"""
mkdir -p $runDir/server && \\
cd $runDir/server && \\
export JBOSS_HOME=$installDir && \\
cp -r $installDir/server/default default && \\
cd $installDir/server/default/conf/bindingservice.beans/META-INF/ && \\
BJB="bindings-jboss-beans.xml" && \\
cp \$BJB \$BJB.bk && \\
sed -i '' 's/ports-03/$portGroupName/' \$BJB && \\
sed -i '' 's/\\<parameter\\>300\\<\\/parameter\\>/\\<parameter\\>$jBossPortIncrement\\<\\/parameter\\>/' \$BJB && \\
export LAUNCH_JBOSS_IN_BACKGROUND=1 && \\
export JAVA_OPTS=""" + "\"" + toJavaDefinesString(getJvmStartupProperties()) + "\"" + """ && \\
JAVA_OPTS="\$JAVA_OPTS -Djboss.platform.mbeanserver" && \\
JAVA_OPTS="\$JAVA_OPTS -Djavax.management.builder.initial=org.jboss.system.server.jmx.MBeanServerBuilderImpl" && \\
JAVA_OPTS="\$JAVA_OPTS -Djava.util.logging.manager=org.jboss.logmanager.LogManager" && \\
JAVA_OPTS="\$JAVA_OPTS -Dorg.jboss.logging.Logger.pluginClass=org.jboss.logging.logmanager.LoggerPluginImpl" && \\
export JBOSS_CLASSPATH="$installDir/lib/jboss-logmanager.jar" && \\
$installDir/bin/run.sh -Djboss.service.binding.set=$portGroupName -Djboss.server.base.dir=$runDir/server -Djboss.server.base.url=file://$runDir/server -c default &
exit
"""
    }

    /** script to return 1 if pid in runDir is running, 0 otherwise */
    public String getCheckRunningScript() { 
		def port = entity.attributes.jmxPort
		def host = entity.attributes.jmxHost
		"$installDir/bin/twiddle.sh --host $host --port $port get \"jboss.system:type=Server\" Started; exit"
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