/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.NetworkUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.jmx.jmxrmi.JmxRmiAgent;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * Start a {@link CassandraNode} in a {@link Location} accessible over ssh.
 */
public class CassandraNodeSshDriver extends JavaSoftwareProcessSshDriver implements CassandraNodeDriver {

    private static final Logger log = LoggerFactory.getLogger(CassandraNodeSshDriver.class);

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

    @Override
    public void install() {
        log.info("Installing {}", entity);
        String url = entity.getConfig(CassandraNode.TGZ_URL);
        if (Strings.isEmpty(url)) {
            url = entity.getConfig(CassandraNode.MIRROR_URL) + String.format("/%1$s/apache-cassandra-%1$s-bin.tar.gz", getVersion());
        }
        String saveAs = String.format("apache-cassandra-%s-bin.tar.gz", getVersion());
        List<String> commands = ImmutableList.<String>builder()
                .addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel("/"), saveAs))
                .add(CommonCommands.INSTALL_TAR)
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
                .put("rmiPort", getRmiServerPort())
                .put("gossipPort", getGossipPort())
                .put("sslGossipPort:", getSslGossipPort())
                .put("thriftPort", getThriftPort())
                .build();
    }

    @Override
    public void customize() {
        log.info("Customizing {} (Cluster {})", entity, getClusterName());
        NetworkUtils.checkPortsValid(getPortMap());

        String logFileEscaped = getLogFileLocation().replace("/", "\\/"); // escape slashes

        ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add(String.format("cp -R %s/apache-cassandra-%s/{bin,conf,lib,interface,pylib,tools} .", getInstallDir(), getVersion()))
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

        // Copy JMX agent Jar to server
        // TODO do this based on config property in UsesJmx
        getMachine().copyTo(new ResourceUtils(this).getResourceFromUrl(getJmxRmiAgentJarUrl()), getJmxRmiAgentJarDestinationFilePath());
    }

    public String getJmxRmiAgentJarBasename() {
        return "brooklyn-jmxrmi-agent-" + BrooklynVersion.get() + ".jar";
    }

    public String getJmxRmiAgentJarUrl() {
        return "classpath://" + getJmxRmiAgentJarBasename();
    }

    public String getJmxRmiAgentJarDestinationFilePath() {
        return getRunDir() + "/" + getJmxRmiAgentJarBasename();
    }

    @Override
    public void launch() {
        log.info("Launching  {}", entity);
        newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
                .body.append(String.format("nohup ./bin/cassandra -p %s > ./cassandra-console.log 2>&1 &", getPidFile()))
                .execute();
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
    public Map<String, String> getShellEnvironment() {
        // TODO do this based on config property in UsesJmx
        String jvmOpts = String.format("-javaagent:%s -D%s=%d -D%s=%d -Djava.rmi.server.hostname=%s -Dcassandra.config=%s",
                getJmxRmiAgentJarDestinationFilePath(),
                JmxRmiAgent.JMX_SERVER_PORT_PROPERTY, getJmxPort(),
                JmxRmiAgent.RMI_REGISTRY_PORT_PROPERTY, getRmiServerPort(),
                getHostname(),
                getCassandraConfigFileName());
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("CASSANDRA_CONF", String.format("%s/conf", getRunDir()))
                .put("JVM_OPTS", jvmOpts) // TODO see QPID_OPTS setting in QpidSshDriver
                .build();
    }

    // Prepare the configuration file (from the template)
    private String processTemplate(String templateConfigUrl) {
        Map<String,?> substitutions = ImmutableMap.of("entity", entity, "driver", this);

        try {
            String templateConfigFile = new ResourceUtils(this).getResourceAsString(templateConfigUrl);

            Configuration cfg = new Configuration();
            StringTemplateLoader templateLoader = new StringTemplateLoader();
            templateLoader.putTemplate("config", templateConfigFile);
            cfg.setTemplateLoader(templateLoader);
            Template template = cfg.getTemplate("config");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer out = new OutputStreamWriter(baos);
            template.process(substitutions, out);
            out.flush();

            return new String(baos.toByteArray());
        } catch (Exception e) {
            log.warn("Error creating configuration file for "+entity, e);
            throw Exceptions.propagate(e);
        }
    }
}
