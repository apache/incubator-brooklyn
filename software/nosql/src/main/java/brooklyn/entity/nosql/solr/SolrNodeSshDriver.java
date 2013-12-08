/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

/**
 * Start a {@link SolrNode} in a {@link Location} accessible over ssh.
 */
public class SolrNodeSshDriver extends JavaSoftwareProcessSshDriver implements SolrNodeDriver {

    private static final Logger log = LoggerFactory.getLogger(SolrNodeSshDriver.class);
    private String expandedInstallDir;

    public SolrNodeSshDriver(SolrNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { return String.format("%s/solr.log", getRunDir()); }

    @Override
    public Integer getSolrPort() { return entity.getAttribute(SolrNode.SOLR_PORT); }

    @Override
    public String getSolrConfigTemplateUrl() { return entity.getAttribute(SolrNode.SOLR_CONFIG_TEMPLATE_URL); }

    @Override
    public String getSolrConfigFileName() { return entity.getAttribute(SolrNode.SOLR_CONFIG_FILE_NAME); }

    public String getMirrorUrl() { return entity.getConfig(SolrNode.MIRROR_URL); }
    
    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }

    /*
       Solr ships with a working Jetty server, with optimized settings for Solr, inside the example directory. It is recommended
       that you use the provided Jetty server for optimal performance. If you absolutely must use a different servlet container
       then continue to the next section on how to install Solr.

       To install Solr

       1. Unpack the Solr distribution to your desired location.
       2. Stop your Java servlet container.
       3. Copy the solr.war file from the Solr distribution to the webapps directory of your servlet container. Do not change
          the name of this file: it must be named solr.war.
       4. Copy the Solr Home directory apache-solr-4.x.0/example/solr/ from the distribution to your desired Solr Home location.
       5. Start your servlet container, passing to it the location of your Solr Home in one of these ways:
          - Set the Java system property solr.solr.home to your Solr Home. (for example: java -Dsolr.solr.home=/some/dir -jar start.jar).
          - Configure the servlet container so that a JNDI lookup of java:comp/env/solr/home by the Solr webapp will point to your Solr Home.
          - Start the servlet container in the directory containing ./solr: the default Solr Home is solr under the JVM's current working directory.

       To confirm your installation, go to the Solr Admin page at http://localhost:8983/solr/. Note that your servlet container may
       have started on a different port: check the documentation for your servlet container to troubleshoot that issue. Also note that
       if that port is already in use, Solr will not start. In that case, shut down the servlet container running on that port, or
       change your Solr port.
    */
    
    @Override
    public void install() {
        log.debug("Installing {}", entity);
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("apache-solr-%s", getVersion()));
        
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
                .put("jmxPort", getJmxPort())
                .put("rmiPort", getRmiRegistryPort())
                .put("solrPort", getSolrPort())
                .build();
    }

    @Override
    public void customize() {
        log.debug("Customizing {} (Cluster {})", entity, getClusterName());
        Networking.checkPortsValid(getPortMap());

        String logFileEscaped = getLogFileLocation().replace("/", "\\/"); // escape slashes

        ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add(String.format("cp -R %s/{bin,conf,lib,interface,pylib,tools} .", getExpandedInstallDir()))
                .add("mkdir data")
                .add(String.format("sed -i.bk 's/log4j.appender.R.File=.*/log4j.appender.R.File=%s/g' %s/conf/log4j-server.properties", logFileEscaped, getRunDir()))
                .add(String.format("sed -i.bk '/JMX_PORT/d' %s/conf/solr-env.sh", getRunDir()))
                .add(String.format("sed -i.bk 's/-Xss180k/-Xss280k/g' %s/conf/solr-env.sh", getRunDir())); // Stack size

        newScript(CUSTOMIZING)
                .body.append(commands.build())
                .execute();

        // Copy the solr.yaml configuration file across
        String configFileContents = processTemplate(getSolrConfigTemplateUrl());
        String destinationConfigFile = String.format("%s/conf/%s", getRunDir(), getSolrConfigFileName());
        getMachine().copyTo(new ByteArrayInputStream(configFileContents.getBytes()), destinationConfigFile);
    }

    @Override
    public void launch() {
        String subnetHostname = Machines.findSubnetOrPublicHostname(entity).get();
        newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
                .body.append(
                        // log the date to attempt to debug occasional http://wiki.apache.org/solr/FAQ#schema_disagreement
                        // (can be caused by machines out of synch time-wise; but in our case it seems to be caused by other things!)
                        "echo date on solr server `hostname` when launching is `date`",
                        String.format("nohup ./bin/solr -p %s > ./solr-console.log 2>&1 &", getPidFile()))
                .execute();
    }

    public String getPidFile() { return String.format("%s/solr.pid", getRunDir()); }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING).body.append("true").execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", getPidFile()), STOPPING).body.append("true").execute();
    }

    @Override
    protected Map getCustomJavaSystemProperties() {
        return MutableMap.<String, String>builder()
                .putAll(super.getCustomJavaSystemProperties())
                .put("solr.confing", getSolrConfigFileName())
                .build();
    }
    
    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("SOLR_CONF", String.format("%s/conf", getRunDir()))
                .renameKey("JAVA_OPTS", "JVM_OPTS")
                .build();
    }
}
