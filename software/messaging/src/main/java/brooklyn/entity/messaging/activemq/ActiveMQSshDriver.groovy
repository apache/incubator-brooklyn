package brooklyn.entity.messaging.activemq;

import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.java.JavaSoftwareProcessSshDriver
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.NetworkUtils

public class ActiveMQSshDriver extends JavaSoftwareProcessSshDriver implements ActiveMQDriver {

    public ActiveMQSshDriver(ActiveMQBroker entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    @Override
    protected String getLogFileLocation() { "${runDir}/data/activemq.log"; }

    @Override
    public Integer getOpenWirePort() { entity.getAttribute(ActiveMQBroker.OPEN_WIRE_PORT) }
    
    @Override
    public void install() {
        String url = entity.getConfig(ActiveMQBroker.TGZ_URL)
        if (!url) {
            url = entity.getConfig(ActiveMQBroker.MIRROR_URL)+"/${version}/apache-activemq-${version}-bin.tar.gz"
        }
        String saveAs  = "apache-activemq-${version}-bin.tar.gz"

        List<String> commands = new LinkedList();
        commands.addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel('/'), saveAs));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv ${saveAs}")

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        NetworkUtils.checkPortsValid(jmxPort:jmxPort, openWirePort:openWirePort);
        newScript(CUSTOMIZING).
                body.append(
                "cp -R ${installDir}/apache-activemq-${version}/{bin,conf,data,lib,webapps} .",
                "sed -i.bk 's/\\[-z \"\$JAVA_HOME\"]/\\[ -z \"\$JAVA_HOME\" ]/g' bin/activemq",
                "sed -i.bk 's/broker /broker useJmx=\"true\" /g' conf/activemq.xml",
                "sed -i.bk 's/managementContext createConnector=\"false\"/managementContext connectorPort=\"${jmxPort}\"/g' conf/activemq.xml",
                "sed -i.bk 's/tcp:\\/\\/0.0.0.0:61616\"/tcp:\\/\\/0.0.0.0:${openWirePort}\"/g' conf/activemq.xml",
                //disable persistence (this should be a flag -- but it seems to have no effect, despite ):
//                "sed -i.bk 's/broker /broker persistent=\"false\" /g' conf/activemq.xml",
                ).execute();
    }

    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile:false).
                body.append(
                "nohup ./bin/activemq start > ./data/activemq-extra.log 2>&1 &",
                ).execute();
    }

    public String getPidFile() {"data/activemq.pid"}
    
    @Override
    public boolean isRunning() {
        newScript(CHECK_RUNNING, usePidFile:pidFile).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(STOPPING, usePidFile:pidFile).execute();
    }

    public Map<String, String> getShellEnvironment() {
        def result = super.getShellEnvironment()
        result << [
            ACTIVEMQ_HOME: "${runDir}",
            ACTIVEMQ_OPTS: result.JAVA_OPTS ?: "",
            ACTIVEMQ_SUNJMX_CONTROL: "--jmxurl service:jmx:rmi://${machine.address.hostName}:${rmiServerPort}/jndi/rmi://${machine.address.hostName}:${jmxPort}/jmxrmi",
            JAVA_OPTS: "",
        ]
    }
}
