package brooklyn.entity.webapp.jboss

import org.codehaus.gmaven.runtime.support.stubgen.model.SuperParameterDef;

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
        // TODO: Config. Run using correct pre-setup jboss config dir. Also deal with making these.
        // Configuring ports:
        // http://docs.jboss.org/jbossas/docs/Server_Configuration_Guide/beta422/html/Additional_Services-Services_Binding_Management.html
        // http://docs.jboss.org/jbossas/6/Admin_Console_Guide/en-US/html/Administration_Console_User_Guide-Port_Configuration.html
        
        // Notes:
        // LAUNCH_JBOSS_IN_BACKGROUND relays OS signals sent to the run.sh process to the JBoss process.
        // See http://community.jboss.org/wiki/StartStopJBoss for more.
        // run.sh must be backgrounded otherwise the script will never return.
        // The until.. loop prevents the installation from returning til JBoss has started,
        // assume failure if 60 attempts to poll twiddle.sh fail.

        // Sets port.
        def props = getJvmStartupProperties()
        def port = entity.attributes.jmxPort    
        def host = entity.attributes.jmxHost 
        println "!!!!!!! " + port
        println "!!!!!!! " + host
        println "!!!!!!! " + props
        println super.entity.attributes
"""
export LAUNCH_JBOSS_IN_BACKGROUND=1
export JAVA_OPTS=""" + "\"" + toJavaDefinesString(props) + """\"
JAVA_OPTS="\$JAVA_OPTS -Djboss.platform.mbeanserver"
JAVA_OPTS="\$JAVA_OPTS -Djavax.management.builder.initial=org.jboss.system.server.jmx.MBeanServerBuilderImpl"
JAVA_OPTS="\$JAVA_OPTS -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
JAVA_OPTS="\$JAVA_OPTS -Dorg.jboss.logging.Logger.pluginClass=org.jboss.logging.logmanager.LoggerPluginImpl"
export JBOSS_CLASSPATH="$installDir/lib/jboss-logmanager.jar"
$installDir/bin/run.sh &
ATTEMPTS=60
until 
    $installDir/bin/twiddle.sh -s service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi \
            get "jboss.system:type=Server" Started > /dev/null 2>&1
    [ 0 -eq \$ATTEMPTS ]
do 
    echo "Waiting for JBoss start / \$ATTEMPTS"
    sleep 1
    ATTEMPTS=\$((\$ATTEMPTS - 1))
done
[ \$ATTEMPTS -gt 0 ]
exit \$?
"""
    }

    //TODO not working; need to write above to a pid.txt file, then copy (or refactor to share) code from TomcatNode.getCheckRunningScript
    /** script to return 1 if pid in runDir is running, 0 otherwise */
    public String getCheckRunningScript() { 
        "exit 0"
    }

    public String getDeployScript(String filename) {
        ""
    }

    public void shutdown(SshMachineLocation loc) {
        println '***********'
        //            def result =  loc.run(out: System.out,
        //                                  "cd $runDir && echo killing process `cat pid.txt` on `hostname` "
        //                                  + "&& kill -9 `cat pid.txt` && rm pid.txt ; exit")
        //          if (result) log.info "non-zero result code terminating {}: {}", entity, result
//      % ./shutdown.sh --host=myremotemachineOrIP  --port=1290 -S 
        def host = entity.attributes.jmxHost
        def port = getJmxPort()
        loc.run("$installDir/bin/shutdown.sh --host=$host --port=$port -S; exit", out: System.out)
    }
}