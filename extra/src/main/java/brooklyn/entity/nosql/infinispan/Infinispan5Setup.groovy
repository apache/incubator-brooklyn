package brooklyn.entity.nosql.infinispan


import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaAppSetup

/**
 * Start a {@link TomcatServer} in a {@link Location} accessible over ssh.
 */
public class Infinispan5Setup extends SshBasedJavaAppSetup {
    public static final String DEFAULT_VERSION = "5.0.0.CR8"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"infinispan"
    
    private String protocol
    private Integer port
    
    public static Infinispan5Setup newInstance(Infinispan5Server entity, SshMachineLocation machine) {
        String suggestedProtocol = entity.getConfig(Infinispan5Server.PROTOCOL.getConfigKey())
        Integer suggestedPort = entity.getConfig(Infinispan5Server.PORT.getConfigKey())
        Integer suggestedInfinispanVersion = entity.getConfig(Infinispan5Server.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(Infinispan5Server.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(Infinispan5Server.SUGGESTED_RUN_DIR)
//        Integer suggestedJmxPort = entity.getConfig(Infinispan5Server.SUGGESTED_JMX_PORT)
//        String suggestedJmxHost = entity.getConfig(Infinispan5Server.SUGGESTED_JMX_HOST)
        
        String version = suggestedInfinispanVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"${version}"+"/"+"infinispan-${version}")
        String runDir = suggestedRunDir ?: (BROOKLYN_HOME_DIR+"/"+"${entity.application.id}"+"/"+"infinispan-${entity.id}")
//        String jmxHost = suggestedJmxHost ?: machine.getAddress().getHostName()
//        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort, DEFAULT_FIRST_JMX_PORT))
        
        Infinispan5Setup result = new Infinispan5Setup(entity, machine)
//        result.setJmxPort(jmxPort)
//        result.setJmxHost(jmxHost)
        result.setProtocol(suggestedProtocol)
        result.setPort(suggestedPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)
        return result
    }
    
    public Infinispan5Setup(Infinispan5Server entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public Infinispan5Setup setProtocol(String val) {
        this.protocol = val
        return this
    }
    
    public Infinispan5Setup setPort(Integer val) {
        this.port = val
        return this
    }
    
    @Override
    protected void postStart() {
        entity.setAttribute(Infinispan5Server.PROTOCOL, protocol)
        entity.setAttribute(Infinispan5Server.PORT, jmxHost)
        entity.setAttribute(Attributes.VERSION, version)
    }
    
    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://sourceforge.net/projects/infinispan/files/infinispan/${version}/infinispan-${version}-all.zip/download",
                "unzip infinispan-${version}-all.zip"
            ])
    }

    @Override
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
			"${installDir}/bin/startServer.sh --protocol $protocol"+(port != null ? " --port $port" : "")+" &",
            "echo \$! > pid.txt"
        ]
        return script
    }
    
    @Override
    public Map<String, String> getRunEnvironment() {
        Map<String, String> env = [:]
        return env
    }

    @Override
    public List<String> getConfigScript() {
        // TODO create and reference a conf.xml? And start with --cache_config <path>
        List<String> script = [
            "mkdir -p ${runDir}"
        ]
        return script
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
        return makeCheckRunningScript("startServer.sh", "pid.txt") + makeCheckPortOpenScript(port)
    }
    
    @Override
    public List<String> getShutdownScript() {
        return makeShutdownScript("startServer.sh", "pid.txt")
    }

    @Override
    protected void postShutdown() {
    }
}
