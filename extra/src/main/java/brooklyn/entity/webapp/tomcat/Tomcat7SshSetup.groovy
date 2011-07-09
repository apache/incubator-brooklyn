package brooklyn.entity.webapp.tomcat

import brooklyn.entity.basic.Attributes
import java.util.List;
import java.util.Map;

import brooklyn.util.SshBasedJavaWebAppSetup
import brooklyn.location.basic.SshMachineLocation

/**
 * Start a {@link TomcatNode} in a {@link Location} accessible over ssh.
 */
public class Tomcat7SshSetup extends SshBasedJavaWebAppSetup {
    public static final String DEFAULT_VERSION = "7.0.16"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"tomcat"
    public static final String DEFAULT_DEPLOY_SUBDIR = "webapps"
    public static final int DEFAULT_FIRST_HTTP_PORT = 8080
    public static final int DEFAULT_FIRST_SHUTDOWN_PORT = 31880
    
    /**
     * Tomcat insists on having a port you can connect to for the sole purpose of shutting it down;
     * don't see an easy way to disable it; causes collisions in its default location of 8005,
     * so moving it to some anonymous high-numbered location
     */
    private int tomcatShutdownPort;
    
    public static Tomcat7SshSetup newInstance(TomcatNode entity, SshMachineLocation machine) {
        Integer suggestedTomcatVersion = entity.getConfig(TomcatNode.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(TomcatNode.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(TomcatNode.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(TomcatNode.SUGGESTED_JMX_PORT)
        String suggestedJmxHost = entity.getConfig(TomcatNode.SUGGESTED_JMX_HOST)
        Integer suggestedShutdownPort = entity.getConfig(TomcatNode.SUGGESTED_SHUTDOWN_PORT)
        Integer suggestedHttpPort = entity.getConfig(TomcatNode.SUGGESTED_HTTP_PORT)
        
        String version = suggestedTomcatVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"apache-tomcat-${version}")
        String runDir = suggestedRunDir ?: (DEFAULT_RUN_DIR+"/"+"app-${entity.application.id}"+"/"+"tomcat-${entity.id}")
        String deployDir = runDir+"/"+DEFAULT_DEPLOY_SUBDIR
        String jmxHost = suggestedJmxHost ?: machine.getAddress().getHostName()
        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort, DEFAULT_FIRST_JMX_PORT))
        int httpPort = machine.obtainPort(toDesiredPortRange(suggestedHttpPort, DEFAULT_FIRST_HTTP_PORT))
        int shutdownPort = machine.obtainPort(toDesiredPortRange(suggestedShutdownPort, DEFAULT_FIRST_SHUTDOWN_PORT))
        
        Tomcat7SshSetup result = new Tomcat7SshSetup(entity, machine)
        result.setJmxPort(jmxPort)
        result.setJmxHost(jmxHost)
        result.setHttpPort(httpPort)
        result.setShutdownPort(shutdownPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setDeployDir(deployDir)
        result.setRunDir(runDir)
        
        return result
    }
    
    public Tomcat7SshSetup(TomcatNode entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public Tomcat7SshSetup setShutdownPort(int val) {
        tomcatShutdownPort = val
        return this
    }
    
    @Override
    protected void postStart() {
        entity.setAttribute(Attributes.JMX_PORT, jmxPort)
        entity.setAttribute(Attributes.JMX_HOST, jmxHost)
        entity.setAttribute(Attributes.HTTP_PORT, httpPort)
        entity.setAttribute(Attributes.VERSION, version)
        entity.setAttribute(TomcatNode.TOMCAT_SHUTDOWN_PORT, tomcatShutdownPort)
    }
    
    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz",
                "tar xvzf apache-tomcat-${version}.tar.gz",
            ])
    }

    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
			"${installDir}/bin/startup.sh",
        ]
        return script
    }
    
    public Map<String, String> getRunEnvironment() {
        Map<String, String> env = [
			"CATALINA_BASE" : "${runDir}",
			"CATALINA_OPTS" : toJavaDefinesString(getJvmStartupProperties()),
			"CATALINA_PID" : "pid.txt",
        ]
        return env
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
        List<String> script = [
            "cd ${runDir}",
			"echo pid is `cat pid.txt`",
			"(ps aux | grep '[t]'omcat | grep `cat pid.txt` > pid.list || echo \"no tomcat processes found\")",
			"cat pid.list",
			"if [ -z \"`cat pid.list`\" ] ; then echo process no longer running ; exit 1 ; fi",
        ]
        return script
        //note grep can return exit code 1 if text not found, hence the || in the block above
    }

    @Override
    public List<String> getConfigScript() {
        List<String> script = [
            "mkdir -p ${runDir}",
            "cd ${runDir}",
            "mkdir conf",
            "mkdir logs",
            "mkdir webapps",
            "cp ${installDir}/conf/{server,web}.xml conf/",
            "sed -i.bk s/8080/${httpPort}/g conf/server.xml",
            "sed -i.bk s/8005/${tomcatShutdownPort}/g conf/server.xml",
            "sed -i.bk /8009/D conf/server.xml",
        ]
        return script
    }

    @Override
    public void shutdown() {
        log.debug "invoking shutdown script"
        //we use kill -9 rather than shutdown.sh because the latter is not 100% reliable
        def result = machine.run(out:System.out, [
            "cd ${runDir}",
            "echo killing process `cat pid.txt` on `hostname`",
            "kill -9 `cat pid.txt`",
            "rm -f pid.txt" ] )
        if (result) log.info "non-zero result code terminating {}: {}", entity, result
        log.debug "done invoking shutdown script"
    }
    
    @Override
    protected void postShutdown() {
        machine.releasePort(jmxPort)
        machine.releasePort(httpPort);
        machine.releasePort(tomcatShutdownPort);
    }
}
