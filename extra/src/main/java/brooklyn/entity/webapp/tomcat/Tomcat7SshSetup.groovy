package brooklyn.entity.webapp.tomcat

import java.io.File
import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaWebAppSetup
import brooklyn.util.internal.LanguageUtils

/**
 * Start a {@link TomcatServer} in a {@link Location} accessible over ssh.
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
    
    public static Tomcat7SshSetup newInstance(TomcatServer entity, SshMachineLocation machine) {
        Integer suggestedTomcatVersion = entity.getConfig(TomcatServer.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(TomcatServer.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(TomcatServer.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(TomcatServer.SUGGESTED_JMX_PORT)
        Integer suggestedShutdownPort = entity.getConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT)
        Integer suggestedHttpPort = entity.getConfig(TomcatServer.HTTP_PORT.configKey)
        Map<String,Map<String,String>> propFilesToGenerate = entity.getConfig(TomcatServer.PROPERTY_FILES) ?: [:]
        
        String version = suggestedTomcatVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"${version}"+"/"+"apache-tomcat-${version}")
        String runDir = suggestedRunDir ?: (BROOKLYN_HOME_DIR+"/"+"${entity.application.id}"+"/"+"tomcat-${entity.id}")
        String deployDir = runDir+"/"+DEFAULT_DEPLOY_SUBDIR
        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort, DEFAULT_FIRST_JMX_PORT))
        int httpPort = machine.obtainPort(toDesiredPortRange(suggestedHttpPort, DEFAULT_FIRST_HTTP_PORT))
        int shutdownPort = machine.obtainPort(toDesiredPortRange(suggestedShutdownPort, DEFAULT_FIRST_SHUTDOWN_PORT))
        
        Tomcat7SshSetup result = new Tomcat7SshSetup(entity, machine)
        result.setJmxPort(jmxPort)
        result.setHttpPort(httpPort)
        result.setShutdownPort(shutdownPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setDeployDir(deployDir)
        result.setRunDir(runDir)
        result.setPropertyFiles(propFilesToGenerate)
        return result
    }
    
    public Tomcat7SshSetup(TomcatServer entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public void setShutdownPort(int val) {
        tomcatShutdownPort = val
    }
    
    @Override
    protected void postStart() {
        def host = entity.getAttribute(Attributes.HOSTNAME)
        entity.setAttribute(Attributes.JMX_PORT, jmxPort)
        entity.setAttribute(Attributes.HTTP_PORT, httpPort)
        entity.setAttribute(JavaWebApp.ROOT_URL, "http://${host}:${httpPort}/")
        entity.setAttribute(Attributes.VERSION, version)
        entity.setAttribute(TomcatServer.TOMCAT_SHUTDOWN_PORT, tomcatShutdownPort)
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
        return super.getRunEnvironment() + [
    			"CATALINA_BASE" : "${runDir}",
    			"CATALINA_OPTS" : toJavaDefinesString(getJvmStartupProperties()),
    			"CATALINA_PID" : "pid.txt"]
    }

    @Override
    public List<String> getConfigScript() {
        List<String> script = [
            "mkdir -p ${runDir}",
            "cd ${runDir}",
            "mkdir conf logs webapps temp",
            "cp ${installDir}/conf/{server,web}.xml conf/",
            "sed -i.bk s/8080/${httpPort}/g conf/server.xml",
            "sed -i.bk s/8005/${tomcatShutdownPort}/g conf/server.xml",
            "sed -i.bk /8009/D conf/server.xml",
        ]
        return script
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
       return makeCheckRunningScript("tomcat")
    }
    
    @Override
    protected void postShutdown() {
        machine.releasePort(jmxPort)
        machine.releasePort(httpPort);
        machine.releasePort(tomcatShutdownPort);
    }
}
