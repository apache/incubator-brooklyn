package brooklyn.entity.messaging.activemq;

import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;

import com.google.common.collect.ImmutableMap;

public class ActiveMQSshDriver extends JavaSoftwareProcessSshDriver implements ActiveMQDriver {

    private String expandedInstallDir;

    public ActiveMQSshDriver(ActiveMQBrokerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { 
        return String.format("%s/data/activemq.log", getRunDir());
    }

    @Override
    public Integer getOpenWirePort() { 
        return entity.getAttribute(ActiveMQBroker.OPEN_WIRE_PORT);
    }

    public String getMirrorUrl() {
        return entity.getConfig(ActiveMQBroker.MIRROR_URL);
    }
    
    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }
    
    @Override
    public void install() {
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("apache-activemq-%s", getVersion()));

        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(urls, saveAs));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv "+saveAs);

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    protected String getTemplateConfigurationUrl() {
        return entity.getAttribute(ActiveMQBroker.TEMPLATE_CONFIGURATION_URL);
    }

    @Override
    public void customize() {
        Networking.checkPortsValid(ImmutableMap.of("jmxPort", getJmxPort(), "openWirePort", getOpenWirePort()));
        newScript(CUSTOMIZING).
                body.append(
                String.format("cp -R %s/{bin,conf,data,lib,webapps} .", getExpandedInstallDir()),
                
                // Required in version 5.5.1 (at least), but not in version 5.7.0
                "sed -i.bk 's/\\[-z \"$JAVA_HOME\"]/\\[ -z \"$JAVA_HOME\" ]/g' bin/activemq"
                
                ).execute();
        
        // TODO disable persistence (this should be a flag -- but it seems to have no effect, despite ):
        // "sed -i.bk 's/broker /broker persistent=\"false\" /g' conf/activemq.xml",

        // Copy the configuration file across
        String configFileContents = processTemplate(getTemplateConfigurationUrl());
        String destinationConfigFile = format("%s/conf/activemq.xml", getRunDir());
        getMachine().copyTo(new ByteArrayInputStream(configFileContents.getBytes()), destinationConfigFile);
    }

    @Override
    public void launch() {
        newScript(ImmutableMap.of("usePidFile", false), LAUNCHING).
                body.append(
                "nohup ./bin/activemq start > ./data/activemq-extra.log 2>&1 &"
                ).execute();
    }

    public String getPidFile() {
        return "data/activemq.pid";
    }
    
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

    public Map<String, String> getShellEnvironment() {
        Map<String,String> orig = super.getShellEnvironment();
        String hostname = getMachine().getAddress().getHostName();
        return MutableMap.<String,String>builder()
                .putAll(orig)
                .put("ACTIVEMQ_HOME", getRunDir())
                .put("ACTIVEMQ_PIDFILE", getPidFile())
                .put("ACTIVEMQ_OPTS", orig.get("JAVA_OPTS") != null ? orig.get("JAVA_OPTS") : "")
                .put("ACTIVEMQ_SUNJMX_CONTROL", String.format("--jmxurl service:jmx:rmi://%s:%s/jndi/rmi://%s:%s/jmxrmi", hostname, getRmiServerPort(), hostname, getJmxPort()))
                .put("JAVA_OPTS", "")
                .build();
    }
}
