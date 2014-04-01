package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableMap;

public class QpidSshDriver extends JavaSoftwareProcessSshDriver implements QpidDriver{

    private static final Logger log = LoggerFactory.getLogger(QpidSshDriver.class);
    
    public QpidSshDriver(QpidBrokerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { return getRunDir()+"/log/qpid.log"; }

    @Override
    public Integer getAmqpPort() { return entity.getAttribute(QpidBroker.AMQP_PORT); }

    @Override
    public String getAmqpVersion() { return entity.getAttribute(QpidBroker.AMQP_VERSION); }

    public Integer getHttpManagementPort() { return entity.getAttribute(QpidBroker.HTTP_MANAGEMENT_PORT); }
    
    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("qpid-broker-%s", getVersion())));
        
        List<String> commands = new LinkedList<String>();
        commands.addAll( BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add("tar xzfv "+saveAs);

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(MutableMap.of("jmxPort", getJmxPort(), "amqpPort", getAmqpPort()));
        newScript(CUSTOMIZING)
                .body.append(
                    format("cp -R %s/{bin,etc,lib} .", getExpandedInstallDir()),
                    "mkdir lib/opt"
                )
                .execute();

        Map runtimeFiles = entity.getConfig(QpidBroker.RUNTIME_FILES);
        copyResources(runtimeFiles);

        Map runtimeTemplates = entity.getConfig(QpidBroker.RUNTIME_TEMPLATES);
        copyTemplates(runtimeTemplates);
    }

    @Override
    public void launch() {
        newScript(ImmutableMap.of("usePidFile", false), LAUNCHING)
                .body.append("nohup ./bin/qpid-server -b '*' > qpid-server-launch.log 2>&1 &")
                .execute();
    }

    public String getPidFile() { return "qpid-server.pid"; }
    
    @Override
    public boolean isRunning() {
        return newScript(ImmutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING).execute() == 0;
    }


    @Override
    public void stop() {
        newScript(ImmutableMap.of("usePidFile", getPidFile()), STOPPING).execute();
    }

    @Override
    public void kill() {
        newScript(ImmutableMap.of("usePidFile", getPidFile()), KILLING).execute();
    }

    public Map<String, Object> getCustomJavaSystemProperties() {
        return MutableMap.<String, Object>builder()
                .putAll(super.getCustomJavaSystemProperties())
                .put("connector.port", getAmqpPort())
                .put("management.enabled", "true")
                .put("management.jmxport.registryServer", getRmiRegistryPort())
                .put("management.jmxport.connectorServer", getJmxPort())
                .put("management.http.enabled",  getHttpManagementPort() != null ? "true" : "false")
                .putIfNotNull("management.http.port", getHttpManagementPort())
                .build();
    }

    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("QPID_HOME", getRunDir())
                .put("QPID_WORK", getRunDir())
                .renameKey("JAVA_OPTS", "QPID_OPTS")
                .build();
    }
}
