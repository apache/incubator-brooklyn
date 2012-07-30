package brooklyn.entity.messaging.qpid;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.lifecycle.CommonCommands

import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.NetworkUtils
import brooklyn.util.ResourceUtils
import brooklyn.entity.basic.lifecycle.JavaSoftwareProcessSshDriver

public class QpidSshDriver extends JavaSoftwareProcessSshDriver implements QpidDriver{

    private static final Logger log = LoggerFactory.getLogger(QpidSshDriver.class);

    public QpidSshDriver(QpidBroker entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    @Override
    protected String getLogFileLocation() { "${runDir}/log/qpid.log"; }

    @Override
    public Integer getAmqpPort() { entity.getAttribute(QpidBroker.AMQP_PORT) }

    @Override
    public String getAmqpVersion() { entity.getAttribute(QpidBroker.AMQP_VERSION) }
    
    protected String getInstallFilename() { "qpid-java-broker-${version}.tar.gz" }
    protected String getInstallUrl() { "http://download.nextag.com/apache/qpid/${version}/${installFilename}" }
    
    @Override
    public void install() {
        List<String> commands = new LinkedList<String>();
        commands.addAll( CommonCommands.downloadUrlAs(installUrl, getEntityVersionLabel('/'), installFilename));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv ${installFilename}");

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute()
    }

    @Override
    public void customize() {
        NetworkUtils.checkPortsValid(jmxPort:jmxPort, amqpPort:amqpPort);
        newScript(CUSTOMIZING)
                .body.append(
                    "cp -R ${installDir}/qpid-broker-${version}/{bin,etc,lib} .",
                    "mkdir lib/opt",
                )
                .execute()
        
        def rtf = entity.getConfig(QpidBroker.RUNTIME_FILES);
        if (rtf) {
            log.info("Customising ${entity} with runtime files ${rtf}");
        } 
        rtf.each {
            String dest, Object sourceO ->
            Object source =
                sourceO instanceof File ? sourceO :
                sourceO instanceof URL ? ((URL)sourceO).openStream() :
                new ResourceUtils(entity).getResourceFromUrl(""+sourceO);
            int result = machine.copyTo source, "${runDir}/${dest}"
            log.debug("copied runtime file for ${entity}: ${sourceO} to ${runDir}/${dest} - result ${result}")
        }
    }

    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile:false)
                .body.append(
                    "nohup ./bin/qpid-server -b '*' -m ${jmxPort} -p ${amqpPort} > /dev/null 2>&1 &",
                )
                .execute()
    }

    public String getPidFile() {"qpid-server.pid"}
    
    @Override
    public boolean isRunning() {
        newScript(CHECK_RUNNING, usePidFile:pidFile).execute() == 0
    }


    @Override
    public void stop() {
        newScript(STOPPING, usePidFile:pidFile).execute();
    }

    public Map<String, String> getShellEnvironment() {
        Map result = super.getShellEnvironment()
        result << [
            QPID_HOME: "${runDir}",
            QPID_WORK: "${runDir}",
            QPID_OPTS: result.JAVA_OPTS ?: [:]
        ]
    }

}
