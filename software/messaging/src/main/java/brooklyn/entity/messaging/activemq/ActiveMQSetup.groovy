package brooklyn.entity.messaging.activemq

import java.util.List
import java.util.Map

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.legacy.JavaApp;
import brooklyn.entity.basic.lifecycle.legacy.SshBasedJavaAppSetup;
import brooklyn.location.PortRange;
import brooklyn.location.basic.SshMachineLocation

/**
 * Start a {@link ActiveMQBroker} in a {@link Location} accessible over ssh.
 */
public class ActiveMQSetup extends SshBasedJavaAppSetup {
    public static final String DEFAULT_VERSION = "5.5.1"
    public static final String DEFAULT_INSTALL_DIR = DEFAULT_INSTALL_BASEDIR+"/"+"activemq"
    public static final int DEFAULT_FIRST_OPEN_WIRE_PORT = 61616
    public static final int DEFAULT_FIRST_RMI_PORT = 1199

    private int openWirePort

    public static ActiveMQSetup newInstance(ActiveMQBroker entity, SshMachineLocation machine) {
        String suggestedVersion = entity.getConfig(ActiveMQBroker.SUGGESTED_VERSION)
        String suggestedInstallDir = entity.getConfig(ActiveMQBroker.SUGGESTED_INSTALL_DIR)
        String suggestedRunDir = entity.getConfig(ActiveMQBroker.SUGGESTED_RUN_DIR)
        PortRange suggestedJmxPort = entity.getConfig(ActiveMQBroker.JMX_PORT)
        PortRange suggestedRmiPort = entity.getConfig(ActiveMQBroker.RMI_PORT, ""+DEFAULT_FIRST_RMI_PORT+"+")
        PortRange suggestedOpenWirePort = entity.getConfig(ActiveMQBroker.OPEN_WIRE_PORT)

        String version = suggestedVersion ?: DEFAULT_VERSION
        String installDir = suggestedInstallDir ?: "$DEFAULT_INSTALL_DIR/${version}/apache-activemq-${version}"
        String runDir = suggestedRunDir ?: "$BROOKLYN_HOME_DIR/${entity.application.id}/activemq-${entity.id}"
        String logFileLocation = "$runDir/data/activemq.log"

        int jmxPort = machine.obtainPort(suggestedJmxPort)
        int rmiPort = machine.obtainPort(suggestedRmiPort)
        int openWirePort = machine.obtainPort(suggestedOpenWirePort)

        ActiveMQSetup result = new ActiveMQSetup(entity, machine)
        result.setJmxPort(jmxPort)
        result.setRmiPort(rmiPort)
        result.setOpenWirePort(openWirePort)
        result.setVersion(version)
        result.setInstallDir(installDir)
        result.setRunDir(runDir)
        entity.setAttribute(Attributes.LOG_FILE_LOCATION, logFileLocation)

        return result
    }

    public ActiveMQSetup(ActiveMQBroker entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public void setOpenWirePort(int val) {
        openWirePort = val
    }

    @Override
    protected void setEntityAttributes() {
		super.setEntityAttributes()
        entity.setAttribute(ActiveMQBroker.OPEN_WIRE_PORT, openWirePort)
    }

    @Override
    public List<String> getInstallScript() {
        makeInstallScript([
                "wget http://www.mirrorservice.org/sites/ftp.apache.org/activemq/apache-activemq/${version}/apache-activemq-${version}-bin.tar.gz",
                "tar xvzf apache-activemq-${version}-bin.tar.gz",
            ])
    }

    /**
     * Creates the directories ActiveMQ needs to run in a different location from where it is installed.
     */
    public List<String> getRunScript() {
        List<String> script = [
            "cd ${runDir}",
			"nohup ./bin/activemq start > ./data/activemq-extra.log 2>&1 &",
        ]
        return script
    }

    public Map<String, String> getShellEnvironment() {
		def result = super.getShellEnvironment()
        result << [
			"ACTIVEMQ_HOME" : "${runDir}",
            "ACTIVEMQ_OPTS" : result.JAVA_OPTS,
            "JAVA_OPTS" : "",
            "ACTIVEMQ_SUNJMX_CONTROL" : "--jmxurl service:jmx:rmi://${machine.address.hostName}:${rmiPort}/jndi/rmi://${machine.address.hostName}:${jmxPort}/jmxrmi"
        ]
    }

    /** @see SshBasedJavaAppSetup#getCheckRunningScript() */
    public List<String> getCheckRunningScript() {
       return makeCheckRunningScript("activemq", "data/activemq.pid")
    }

    @Override
    public List<String> getConfigScript() {
        List<String> script = [
            "mkdir -p ${runDir}",
            "cd ${runDir}",
            "cp -R ${installDir}/{bin,conf,data,lib,webapps} .",
            "sed -i.bk 's/\\[-z \"\$JAVA_HOME\"]/\\[ -z \"\$JAVA_HOME\" ]/g' bin/activemq",
            "sed -i.bk 's/broker /broker useJmx=\"true\" /g' conf/activemq.xml",
            "sed -i.bk 's/managementContext createConnector=\"false\"/managementContext connectorPort=\"${jmxPort}\"/g' conf/activemq.xml",
            "sed -i.bk 's/tcp:\\/\\/0.0.0.0:61616\"/tcp:\\/\\/0.0.0.0:${openWirePort}\"/g' conf/activemq.xml",
            //disable persistence (this should be a flag -- but it seems to have no effect, despite ):
//            "sed -i.bk 's/broker /broker persistent=\"false\" /g' conf/activemq.xml",
        ]
        return script
    }

    @Override
    public List<String> getRestartScript() {
       return makeRestartScript("activemq", "data/activemq.pid")
    }

    @Override
    public List<String> getShutdownScript() {
       return makeShutdownScript("activemq", "data/activemq.pid")
    }

    @Override
    protected void postShutdown() {
        machine.releasePort(rmiPort)
        machine.releasePort(jmxPort)
        machine.releasePort(openWirePort);
    }
}
