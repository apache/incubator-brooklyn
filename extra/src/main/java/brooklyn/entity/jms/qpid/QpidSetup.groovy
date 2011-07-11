package brooklyn.entity.jms.qpid

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp;
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaAppSetup;
import brooklyn.util.SshBasedJavaWebAppSetup

/**
 * Start a {@link QpidNode} in a {@link Location} accessible over ssh.
 */
public class QpidSetup extends SshBasedJavaAppSetup {
    public static final String DEFAULT_VERSION = "0.10"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"qpid"
    public static final int DEFAULT_FIRST_AMQP_PORT = 5672

    private int amqpPort
    private int rmiPort

    public static QpidSetup newInstance(QpidNode entity, SshMachineLocation machine) {
        Integer suggestedQpidVersion = entity.getConfig(QpidNode.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(QpidNode.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(QpidNode.SUGGESTED_RUN_DIR)
        Integer suggestedJmxPort = entity.getConfig(QpidNode.SUGGESTED_JMX_PORT)
        String suggestedJmxHost = entity.getConfig(QpidNode.SUGGESTED_JMX_HOST)
        Integer suggestedAmqpPort = entity.getConfig(QpidNode.SUGGESTED_AMQP_PORT)

        String version = suggestedQpidVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: (DEFAULT_INSTALL_DIR+"/"+"qpid-broker-${version}")
        String runDir = suggestedRunDir ?: (DEFAULT_RUN_DIR+"/"+"app-"+entity.getApplication()?.id+"/qpid-"+entity.id)
        String jmxHost = suggestedJmxHost ?: machine.getAddress().getHostName()
        int jmxPort = machine.obtainPort(toDesiredPortRange(suggestedJmxPort, DEFAULT_FIRST_JMX_PORT))
        int rmiPort = machine.obtainPort(toDesiredPortRange(jmxPort - 100))
        int amqpPort = machine.obtainPort(toDesiredPortRange(suggestedAmqpPort, DEFAULT_FIRST_AMQP_PORT))

        QpidSetup result = new QpidSetup(entity, machine)
        result.setRmiPort(rmiPort)
        result.setJmxPort(jmxPort)
        result.setJmxHost(jmxHost)
        result.setAmqpPort(amqpPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)

        return result
    }

    public QpidSetup(QpidNode entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public QpidSetup setAmqpPort(int val) {
        amqpPort = val
        return this
    }

    public QpidSetup setRmiPort(int val) {
        rmiPort = val
        return this
    }

    /** JMX is configured using command line switch. */
    @Override
    protected Map getJmxConfigOptions() { [:] }

    @Override
    protected void postStart() {
        entity.setAttribute(Attributes.JMX_PORT, jmxPort)
        entity.setAttribute(Attributes.JMX_HOST, jmxHost)
        entity.setAttribute(Attributes.AMQP_PORT, amqpPort)
        entity.setAttribute(Attributes.VERSION, version)
    }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://download.nextag.com/apache/qpid/${version}/qpid-java-broker-${version}.tar.gz",
                "tar xvzf qpid-java-broker-${version}.tar.gz",
            ])
    }

    /**
     * Creates the directories qpid needs to run in a different location from where it is installed,
     * renumber http and shutdown ports, and delete AJP connector, then start with JMX enabled
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
			"nohup ./bin/qpid-server -m ${jmxPort} -p ${amqpPort} --exclude-0-10 ${amqpPort} &",
        ]
        return script
    }

    public Map<String, String> getRunEnvironment() {
        Map<String, String> env = [
			"QPID_HOME" : "${runDir}",
			"QPID_WORK" : "${runDir}",
			"QPID_OPTS" : toJavaDefinesString(getJvmStartupProperties()),
        ]
        return env
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
        List<String> script = [
            "cd ${runDir}",
			"echo pid is `cat qpid-server.pid`",
			"(ps auxww | grep '[q]'pid | grep `cat qpid-server.pid` > pid.list || echo \"no qpid processes found\")",
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
            "cp -R ${installDir}/{bin,etc,lib} .",
        ]
        return script
    }

    @Override
    public void shutdown() {
        log.debug "invoking shutdown script"
        def result = machine.run(out:System.out, [
            "cd ${runDir}",
            "echo killing process `cat qpid-server.pid` on `hostname`",
            "kill -9 `cat qpid-server.pid`",
            "rm -f pid.txt" ] )
        if (result) log.info "non-zero result code terminating {}: {}", entity, result
        log.debug "done invoking shutdown script"
    }

    @Override
    protected void postShutdown() {
        machine.releasePort(rmiPort)
        machine.releasePort(jmxPort)
        machine.releasePort(amqpPort);
    }
}
