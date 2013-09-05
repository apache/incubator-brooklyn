/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * Start a {@link CassandraNode} in a {@link Location} accessible over ssh.
 */
public class CassandraNodeSshDriver extends JavaSoftwareProcessSshDriver implements CassandraNodeDriver {

    private static final Logger log = LoggerFactory.getLogger(CassandraNodeSshDriver.class);
    private String expandedInstallDir;

    public CassandraNodeSshDriver(CassandraNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { return String.format("%s/cassandra.log", getRunDir()); }

    @Override
    public Integer getGossipPort() { return entity.getAttribute(CassandraNode.GOSSIP_PORT); }

    @Override
    public Integer getSslGossipPort() { return entity.getAttribute(CassandraNode.SSL_GOSSIP_PORT); }

    @Override
    public Integer getThriftPort() { return entity.getAttribute(CassandraNode.THRIFT_PORT); }

    @Override
    public String getClusterName() { return entity.getAttribute(CassandraNode.CLUSTER_NAME); }

    @Override
    public String getCassandraConfigTemplateUrl() { return entity.getAttribute(CassandraNode.CASSANDRA_CONFIG_TEMPLATE_URL); }

    @Override
    public String getCassandraConfigFileName() { return entity.getAttribute(CassandraNode.CASSANDRA_CONFIG_FILE_NAME); }

    public String getMirrorUrl() { return entity.getConfig(CassandraNode.MIRROR_URL); }
    
    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }
    
    @Override
    public void install() {
        log.debug("Installing {}", entity);
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("apache-cassandra-%s", getVersion()));
        
        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.downloadUrlAs(urls, saveAs))
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
                .put("gossipPort", getGossipPort())
                .put("sslGossipPort:", getSslGossipPort())
                .put("thriftPort", getThriftPort())
                .build();
    }

    @Override
    public void customize() {
        log.debug("Customizing {} (Cluster {})", entity, getClusterName());
        Networking.checkPortsValid(getPortMap());
        
        if (entity.getConfig(CassandraNode.INITIAL_SEEDS)==null)
            entity.setConfig(CassandraNode.INITIAL_SEEDS, 
                    DependentConfiguration.attributeWhenReady(entity.getParent(), CassandraCluster.CURRENT_SEEDS));

        String logFileEscaped = getLogFileLocation().replace("/", "\\/"); // escape slashes

        ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add(String.format("cp -R %s/{bin,conf,lib,interface,pylib,tools} .", getExpandedInstallDir()))
                .add("mkdir data")
                .add(String.format("sed -i.bk 's/log4j.appender.R.File=.*/log4j.appender.R.File=%s/g' %s/conf/log4j-server.properties", logFileEscaped, getRunDir()))
                .add(String.format("sed -i.bk '/JMX_PORT/d' %s/conf/cassandra-env.sh", getRunDir()))
                .add(String.format("sed -i.bk 's/-Xss180k/-Xss280k/g' %s/conf/cassandra-env.sh", getRunDir())); // Stack size

        newScript(CUSTOMIZING)
                .body.append(commands.build())
                .execute();

        // Copy the configuration file across
        String configFileContents = processTemplate(getCassandraConfigTemplateUrl());
        String destinationConfigFile = String.format("%s/conf/%s", getRunDir(), getCassandraConfigFileName());
        getMachine().copyTo(new ByteArrayInputStream(configFileContents.getBytes()), destinationConfigFile);
    }

    @Override
    public void launch() {
        String subnetHostname = Machines.findSubnetOrPublicHostname(entity).get();
        String seeds = ((CassandraNode)getEntity()).getSeeds();
        log.info("Launching " + entity + ": " +
                "cluster "+getClusterName()+", " +
        		"hostname (public) " + getEntity().getAttribute(Attributes.HOSTNAME) + ", " +
        		"hostname (subnet) " + subnetHostname + ", " +
        		"seeds "+seeds);
        boolean isFirst = seeds.startsWith(subnetHostname);
        if (!isFirst) {
            // if they start at the same time, they seem sometimes not to come up properly :( 
            long firstStartTime = Entities.submit(entity, DependentConfiguration.attributeWhenReady(getEntity().getParent(), CassandraCluster.FIRST_NODE_STARTED_TIME_UTC)).getUnchecked();
            Duration toWait = Duration.millis(firstStartTime + Duration.THIRTY_SECONDS.toMilliseconds() -  System.currentTimeMillis());
            if (toWait.toMilliseconds()>0) {
                log.info("Launching " + entity + ": delaying launch of non-first node by "+toWait+" to prevent schema disagreements");
                Tasks.setBlockingDetails("Pausing to ensure first node has time to start");
                Time.sleep(toWait);
                Tasks.resetBlockingDetails();
            }
        }
        newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
                .body.append(
                        // log the date to attempt to debug occasional http://wiki.apache.org/cassandra/FAQ#schema_disagreement
                        "echo date on cassandra server `hostname` is `date`",
                        String.format("nohup ./bin/cassandra -p %s > ./cassandra-console.log 2>&1 &", getPidFile()))
                .execute();
        if (isFirst) {
            ((EntityLocal)getEntity().getParent()).setAttribute(CassandraCluster.FIRST_NODE_STARTED_TIME_UTC, System.currentTimeMillis());
        }
    }

    public String getPidFile() { return String.format("%s/cassandra.pid", getRunDir()); }

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
                .put("cassandra.confing", getCassandraConfigFileName())
                .build();
    }
    
    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("CASSANDRA_CONF", String.format("%s/conf", getRunDir()))
                .renameKey("JAVA_OPTS", "JVM_OPTS")
                .build();
    }
}
