package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Networking;

import com.google.common.collect.ImmutableMap;

public class QpidSshDriver extends JavaSoftwareProcessSshDriver implements QpidDriver{

    private static final Logger log = LoggerFactory.getLogger(QpidSshDriver.class);
    
    private String expandedInstallDir;

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
    
    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }
    
    @Override
    public void install() {
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("qpid-broker-%s", getVersion()));
        
        List<String> commands = new LinkedList<String>();
        commands.addAll( CommonCommands.downloadUrlAs(urls, saveAs));
        commands.add(CommonCommands.INSTALL_TAR);
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
                .body.append("nohup ./bin/qpid-server -b '*' > /dev/null 2>&1 &")
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
        Map<String, Object> props = MutableMap.<String, Object>builder()
                .put("connector.port", getAmqpPort())
                .put("management.enabled", "true")
                .put("management.jmxport.registryServer", getJmxPort())
                .put("management.jmxport.connectorServer", getRmiServerPort())
                .put("management.http.enabled",  getHttpManagementPort() != null ? "true" : "false")
                .build();
        if (getHttpManagementPort() != null) {
            props.put("management.http.port", getHttpManagementPort());
        }
        return props;
    }

    public Map<String, String> getShellEnvironment() {
        Map<String, String> orig = super.getShellEnvironment();
        return MutableMap.<String, String>builder()
                .putAll(orig)
                .put("QPID_HOME", getRunDir())
                .put("QPID_WORK", getRunDir())
                .put("QPID_OPTS", (orig.containsKey("JAVA_OPTS") ? orig.get("JAVA_OPTS") : ""))
                .build();
    }
}
