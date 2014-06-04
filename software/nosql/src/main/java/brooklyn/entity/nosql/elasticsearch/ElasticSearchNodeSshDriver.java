package brooklyn.entity.nosql.elasticsearch;

import static java.lang.String.format;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;

public class ElasticSearchNodeSshDriver extends AbstractSoftwareProcessSshDriver implements ElasticSearchNodeDriver {

    public ElasticSearchNodeSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        
        List<String> commands = ImmutableList.<String>builder()
            .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
            .add(BashCommands.installJavaLatestOrFail())
            .add(String.format("tar zxvf %s", saveAs))
            .build();
        
        newScript(INSTALLING).body.append(commands).execute();
        
        setExpandedInstallDir(getInstallDir() + "/" + resolver.getUnpackedDirectoryName(format("elasticsearch-%s", getVersion())));
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).execute();  //create the directory
        
        String configFileUrl = entity.getConfig(ElasticSearchNode.TEMPLATE_CONFIGURATION_URL);
        
        if (configFileUrl == null) {
            return;
        }

        String configScriptContents = processTemplate(configFileUrl);
        Reader configContents = new StringReader(configScriptContents);

        getMachine().copyTo(configContents, Urls.mergePaths(getRunDir(), getConfigFile()));
    }

    @Override
    public void launch() {
        String pidFile = getRunDir() + "/" + AbstractSoftwareProcessSshDriver.PID_FILENAME;
        entity.setAttribute(ElasticSearchNode.PID_FILE, pidFile);
        StringBuilder commandBuilder = new StringBuilder()
            .append(String.format("%s/bin/elasticsearch -d -p %s", getExpandedInstallDir(), pidFile));
        if (entity.getConfig(ElasticSearchNode.TEMPLATE_CONFIGURATION_URL) != null) {
            commandBuilder.append(" -Des.config=" + Os.mergePaths(getRunDir(), getConfigFile()));
        }
        appendConfigIfPresent(commandBuilder, ElasticSearchNode.DATA_DIR, "es.path.data", Os.mergePaths(getRunDir(), "data"));
        appendConfigIfPresent(commandBuilder, ElasticSearchNode.LOG_DIR, "es.path.logs", Os.mergePaths(getRunDir(), "logs"));
        appendConfigIfPresent(commandBuilder, ElasticSearchNode.NODE_NAME.getConfigKey(), "es.node.name");
        appendConfigIfPresent(commandBuilder, ElasticSearchNode.CLUSTER_NAME.getConfigKey(), "es.cluster.name");
        appendConfigIfPresent(commandBuilder, ElasticSearchNode.MULTICAST_ENABLED, "es.discovery.zen.ping.multicast.enabled");
        appendConfigIfPresent(commandBuilder, ElasticSearchNode.UNICAST_ENABLED, "es.discovery.zen.ping.unicast.enabled");
        commandBuilder.append(" > out.log 2> err.log < /dev/null");
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(commandBuilder.toString())
            .execute();
    }
    
    private void appendConfigIfPresent(StringBuilder builder, ConfigKey<?> configKey, String parameter) {
        appendConfigIfPresent(builder, configKey, parameter, null);
    }
    
    private void appendConfigIfPresent(StringBuilder builder, ConfigKey<?> configKey, String parameter, String defaultValue) {
        String config = null;
        if (entity.getConfig(configKey) != null) {
            config = String.valueOf(entity.getConfig(configKey));
        }
        if (config == null && defaultValue != null) {
            config = defaultValue;
        }
        if (config != null) {
            builder.append(String.format(" -D%s=%s", parameter, config));
        }
    }
    
    public String getConfigFile() {
        return "elasticsearch.yaml";
    }
    
    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", true), CHECK_RUNNING).execute() == 0;
    }
    
    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", true), STOPPING).execute();
    }
    
    @Override
    public void kill() {
        newScript(MutableMap.of("usePidFile", true), KILLING).execute();
    }

}
