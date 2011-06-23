package brooklyn.entity.webapp.tomcat

import brooklyn.location.Location;
import brooklyn.location.basic.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation


/**
 * Start a {@link TomcatNode} in a {@link Location} accessible over ssh.
 */
public class Tomcat7SshSetup extends SshBasedJavaWebAppSetup {
    String version = "7.0.16"
    String installDir = installsBaseDir + "/" + "tomcat" + "/" + "apache-tomcat-$version"
 
    public static DEFAULT_FIRST_HTTP_PORT = 8080
    public static DEFAULT_FIRST_SHUTDOWN_PORT = 31880

    TomcatNode entity
    String runDir

    Object httpPortLock = new Object()
    int httpPort = -1

    public Tomcat7SshSetup(TomcatNode entity) {
        super(entity)
        runDir = appBaseDir + "/" + "tomcat-" + entity.id
    }

    public String getInstallScript() {
        makeInstallScript(
                "wget http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz",
                "tar xvzf apache-tomcat-${version}.tar.gz")
    }

    /**
     * Creates the directories tomcat needs to run in a different location from where it is installed,
     * renumber http and shutdown ports, and delete AJP connector, then start with JMX enabled
     */
    public String getRunScript() {
        """\
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
export CATALINA_OPTS=""" + "\"" + toJavaDefinesString(getJvmStartupProperties()) + """\" && \\
export CATALINA_PID="pid.txt" && \\
$installDir/bin/startup.sh
exit
"""
    }

    /** script to return 1 if pid in runDir is running, 0 otherwise  */
    public String getCheckRunningScript() {"""\
cd $runDir && \\
echo pid is `cat pid.txt` && \\
(ps aux | grep '[t]'omcat | grep `cat pid.txt` > pid.list || echo "no tomcat processes found") && \\
cat pid.list && \\
if [ -z "`cat pid.list`" ] ; then echo process no longer running ; exit 1 ; fi
exit
"""
        //note grep can return exit code 1 if text not found, hence the || in the block above
    }

    /** Assumes file is already in locOnServer.  */
    public String getDeployScript(String locOnServer) {
        String to = runDir + "/" + "webapps"
        """\
cp $locOnServer $to
exit"""
    }

    public int getTomcatHttpPort() {
        synchronized (httpPortLock) {
            if (httpPort < 0)
                httpPort = getNextValue("tomcatHttpPort", DEFAULT_FIRST_HTTP_PORT)
        }
        return httpPort
    }
    /** tomcat insists on having a port you can connect to for the sole purpose of shutting it down;
     * don't see an easy way to disable it; causes collisions in its default location of 8005,
     * so moving it to some anonymous high-numbered location
     */
    public int getTomcatShutdownPort() {
        getNextValue("tomcatShutdownPort", DEFAULT_FIRST_SHUTDOWN_PORT)
    }

    public void shutdown(SshMachineLocation loc) {
        log.debug "invoking shutdown script"
        //we use kill -9 rather than shutdown.sh because the latter is not 100% reliable
        def result = loc.run(out: System.out, "cd $runDir && echo killing process `cat pid.txt` on `hostname` && kill -9 `cat pid.txt` && rm -f pid.txt ; exit")
        if (result) log.info "non-zero result code terminating {}: {}", entity, result
        log.debug "done invoking shutdown script"
    }
}
