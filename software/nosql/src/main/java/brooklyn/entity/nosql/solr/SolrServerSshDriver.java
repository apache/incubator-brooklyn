/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.file.ArchiveUtils;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.Streams;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * Start a {@link SolrServer} in a {@link Location} accessible over ssh.
 */
public class SolrServerSshDriver extends AbstractSoftwareProcessSshDriver implements SolrServerDriver {

    private static final Logger log = LoggerFactory.getLogger(SolrServerSshDriver.class);

    public SolrServerSshDriver(SolrServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public Integer getSolrPort() { return entity.getAttribute(SolrServer.SOLR_PORT); }

    @Override
    public String getSolrConfigTemplateUrl() { return entity.getConfig(SolrServer.SOLR_CONFIG_TEMPLATE_URL); }

    public String getMirrorUrl() { return entity.getConfig(SolrServer.MIRROR_URL); }

    public String getPidFile() { return String.format("%s/solr.pid", getRunDir()); }

    @Override
    public void install() {
        log.debug("Installing {}", entity);
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("solr-%s", getVersion())));

        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(BashCommands.INSTALL_TAR)
                .add("tar xzfv " + saveAs)
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    public Set<Integer> getPortsUsed() {
        Set<Integer> result = Sets.newLinkedHashSet(super.getPortsUsed());
        result.addAll(getPortMap().values());
        return result;
    }

    private Map<String, Integer> getPortMap() {
        return ImmutableMap.<String, Integer>builder()
                .put("solrPort", getSolrPort())
                .build();
    }

    @Override
    public void customize() {
        log.debug("Customizing {}", entity);
        Networking.checkPortsValid(getPortMap());

        ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add("mkdir contrib")
                .add("mkdir solr")
                .add(String.format("cp -R %s/example/{etc,contexts,lib,logs,resources,webapps} .", getExpandedInstallDir()))
                .add(String.format("cp %s/example/start.jar .", getExpandedInstallDir()))
                .add(String.format("cp %s/dist/*.jar lib/", getExpandedInstallDir()))
                .add(String.format("cp %s/contrib/*/lib/*.jar contrib/", getExpandedInstallDir()));

        newScript(CUSTOMIZING)
                .body.append(commands.build())
                .execute();

        // Copy the solr.xml configuration file across
        String configFileContents = processTemplate(getSolrConfigTemplateUrl());
        String destinationConfigFile = String.format("%s/solr/solr.xml", getRunDir());
        getMachine().copyTo(Streams.newInputStreamWithContents(configFileContents), destinationConfigFile);

        // Copy the core definitions across
        Map<String, String> coreConfig = entity.getConfig(SolrServer.SOLR_CORE_CONFIG);
        for (String core : coreConfig.keySet()) {
            String url = coreConfig.get(core);
            String solr = Urls.mergePaths(getRunDir(), "solr");
            ArchiveUtils.deploy(url, getMachine(), solr);
        }
    }

    @Override
    public void launch() {
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), LAUNCHING)
                .body.append("nohup java $JAVA_OPTS -jar start.jar > ./logs/console.log 2>&1 &")
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), STOPPING).execute();
    }
}
