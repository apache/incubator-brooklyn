package brooklyn.entity.messaging.qpid

import static brooklyn.entity.webapp.PortPreconditions.checkPortValid

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.lifecycle.legacy.SshBasedJavaAppSetup
import brooklyn.location.PortRange
import brooklyn.location.basic.PortRanges
import brooklyn.location.basic.SshMachineLocation

/**
 * Start an AMQP 0-10 {@link QpidBroker} in a {@link brooklyn.location.Location} accessible over ssh.
 */
public class QpidSetup extends SshBasedJavaAppSetup {
    public static final String DEFAULT_VERSION = "0.14"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"qpid"
    public static final int DEFAULT_FIRST_AMQP_PORT = 5672

    private int amqpPort

    // FIXME for ports, entity.setAttribute(portSensorAndConfig) will work,
    // and if entity has location set, it will look it up in the appropriate location
    // (doing that via SensorRegistry.apply, as in JBoss7Server, is preferred to the way setup is being done here)
    public static QpidSetup newInstance(QpidBroker entity, SshMachineLocation machine) {
        String suggestedVersion = entity.getConfig(QpidBroker.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(QpidBroker.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(QpidBroker.SUGGESTED_RUN_DIR)
        PortRange suggestedJmxPort = entity.getConfig(QpidBroker.JMX_PORT)
        PortRange suggestedAmqpPort = entity.getConfig(QpidBroker.AMQP_PORT)

        String version = suggestedVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: "$DEFAULT_INSTALL_DIR/${version}/qpid-broker-${version}"
        String runDir = suggestedRunDir ?: "$BROOKLYN_HOME_DIR/${entity.application.id}/qpid-${entity.id}"
        String logFileLocation = "$runDir/log/qpid.log"

        int jmxPort = checkPortValid(machine.obtainPort(suggestedJmxPort), "jmxPort (suggested $suggestedJmxPort)")
        int rmiPort = checkPortValid(machine.obtainPort(PortRanges.fromString(String.format("%d+", jmxPort+100))), "rmiPort (suggested $jmxPort+100)")
        int amqpPort = checkPortValid(machine.obtainPort(suggestedAmqpPort), "amqpPort (suggested $suggestedAmqpPort)")
        
        QpidSetup result = new QpidSetup(entity, machine)
        result.setRmiPort(rmiPort)
        result.setJmxPort(jmxPort)
        result.setAmqpPort(amqpPort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)
        entity.setAttribute(Attributes.LOG_FILE_LOCATION, logFileLocation)

        return result
    }

    public QpidSetup(QpidBroker entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public void setAmqpPort(int val) {
        amqpPort = val
    }

    /**
     * JMX is enabled using a command line switch and XML configuration.
     *
     * The RMI port for Qpid is automatically set using the JMX port from the command line {@code -m} plus 100.
     * <p>
     * We still need to set the RMI hostname to the (remotely resolved) IP address of our system. Required for environments
     * like AWS EC2 where DNS names resolve differently inside NAT, since RMI passes this address back to the client.
     */
    @Override
    protected Map getJmxJavaSystemProperties() {
        [
          "java.rmi.server.hostname" : machine.address.hostAddress,
        ]
    }

    @Override
    protected void setEntityAttributes() {
		super.setEntityAttributes()
        entity.setAttribute(Attributes.AMQP_PORT, amqpPort)
    }

    /**
     * Configure the broker.
     */
    @Override
    public void config() {
        super.config()
        copyFilesForRuntime()
    }

    public void copyFilesForRuntime() {
        entity.getConfig(QpidBroker.RUNTIME_FILES).each {
            String dest, File source ->
            int result = machine.copyTo source, "${runDir}/${dest}"
            log.info("copied ${source.path} to ${runDir}/${dest} - ${result}")
        }
    }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://download.nextag.com/apache/qpid/${version}/qpid-java-broker-${version}.tar.gz",
                "tar xvzf qpid-java-broker-${version}.tar.gz",
            ])
    }

    /**
     * Starts the Qpid Java broker.
     *
     * The {@code --exclude-0-8} and similar switches are used to force the broker to present using only AMQP 0-10 on the
     * configured AMQP port number. In future we may wish to parse the {@link QpidBroker#AMQP_VERSION} value to set up
     * brokers that accept arbitrary AMQP versions.
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
			"nohup ./bin/qpid-server -b '*' -m ${jmxPort} -p ${amqpPort} --exclude-0-8 ${amqpPort} --exclude-0-9 ${amqpPort} --exclude-0-9-1 ${amqpPort} &",
        ]
        return script
    }

    public Map<String, String> getShellEnvironment() {
        Map result = super.getShellEnvironment()
		result << [
			"QPID_HOME" : "${runDir}",
			"QPID_WORK" : "${runDir}",
			"QPID_OPTS" : result.JAVA_OPTS
        ]
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
       return makeCheckRunningScript("qpid", "qpid-server.pid")
    }

    /**
     * Creates the directories Qpid needs to run in a different location from where it is installed.
     */
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
    public List<String> getRestartScript() {
       return makeRestartScript("qpid", "qpid-server.pid")
    }

    @Override
    public List<String> getShutdownScript() {
       return makeShutdownScript("qpid", "qpid-server.pid")
    }

    @Override
    protected void postShutdown() {
        machine.releasePort(rmiPort)
        machine.releasePort(jmxPort)
        machine.releasePort(amqpPort)
    }
}
