/*
 * Copyright 2012 by Andrew Kennedy
 */
package brooklyn.entity.nosql.cassandra;

import java.util.List;
import java.util.Map;

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
import brooklyn.util.jmx.jmxrmi.JmxRmiAgent;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Start a {@link CassandraServer} in a {@link Location} accessible over ssh.
 */
public class CassandraSshDriver extends JavaSoftwareProcessSshDriver implements CassandraDriver {

    private static final Logger log = LoggerFactory.getLogger(CassandraSshDriver.class);
    
    public CassandraSshDriver(CassandraServer entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { return String.format("%s/cassandra.log", getRunDir()); }

    @Override
    public Integer getGossipPort() { return entity.getAttribute(CassandraServer.GOSSIP_PORT); }

    @Override
    public Integer getSslGossipPort() { return entity.getAttribute(CassandraServer.SSL_GOSSIP_PORT); }

    @Override
    public Integer getThriftPort() { return entity.getAttribute(CassandraServer.THRIFT_PORT); }

    @Override
    public String getClusterName() { return entity.getAttribute(CassandraServer.CLUSTER_NAME); }
    
    @Override
    public void install() {
        log.info("Installing {}", entity);
        String url = entity.getConfig(CassandraServer.TGZ_URL);
        if (Strings.isEmpty(url)) {
            url = entity.getConfig(CassandraServer.MIRROR_URL) + String.format("/%1$s/apache-cassandra-%1$s-bin.tar.gz", getVersion());
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

    @Override
    public void customize() {
        log.info("Customizing {} (Cluster {})", entity, getClusterName());
        Map<String, Integer> ports = ImmutableMap.<String, Integer>builder()
                .put("jmxPort", getJmxPort())
                .put("rmiPort", getRmiServerPort())
                .put("gossipPort", getGossipPort())
                .put("sslGossipPort:", getSslGossipPort())
                .put("thriftPort", getThriftPort())
                .build();
        NetworkUtils.checkPortsValid(ports);

        String logFileEscaped = getLogFileLocation().replace("/", "\\/"); // escape slashes
        String dataDirEscaped = (getRunDir() + "/data").replace("/", "\\/"); // escape slashes

        ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add(String.format("cp -R %s/apache-cassandra-%s/{bin,conf,lib,interface,pylib,tools} .", getInstallDir(), getVersion()))
                .add("mkdir data")
                .add(String.format("sed -i.bk 's/log4j.appender.R.File=.*/log4j.appender.R.File=%s/g' %s/conf/log4j-server.properties", logFileEscaped, getRunDir()))
                .add(String.format("sed -i.bk 's/\\/var\\/lib\\/cassandra/%s/g' %s/conf/cassandra.yaml", dataDirEscaped, getRunDir()))
                .add(String.format("sed -i.bk \"s/^cluster_name: .*/cluster_name: '%s'/g\" %s/conf/cassandra.yaml", getClusterName(), getRunDir()))
                .add(String.format("sed -i.bk '/JMX_PORT/d' %s/conf/cassandra-env.sh", getRunDir()))
                .add(String.format("sed -i.bk 's/-Xss180k/-Xss280k/g' %s/conf/cassandra-env.sh", getRunDir())) // Stack size
                .add(String.format("sed -i.bk 's/^rpc_port: .*/rpc_port: %d/g' %s/conf/cassandra.yaml", getThriftPort(), getRunDir()))
                .add(String.format("sed -i.bk 's/^rpc_address: .*/rpc_address: %s/g' %s/conf/cassandra.yaml", getHostname(), getRunDir()))
                .add(String.format("sed -i.bk 's/^storage_port: .*/storage_port: %d/g' %s/conf/cassandra.yaml", getGossipPort(), getRunDir()))
                .add(String.format("sed -i.bk 's/^ssl_storage_port: .*/ssl_storage_port: %d/g' %s/conf/cassandra.yaml", getSslGossipPort(), getRunDir()))
                .add(String.format("sed -i.bk 's/^listen_address: .*/listen_address: %s/g' %s/conf/cassandra.yaml", getHostname(), getRunDir()))
                .add(String.format("sed -i.bk 's/- seeds:.*/- seeds: \"%s\"/g' %s/conf/cassandra.yaml", getHostname(), getRunDir()));

        // Open inbound ports
        for (Integer port : ports.values()) {
            commands.add(String.format("which iptables && iptables -I INPUT 1 -p tcp --dport %d -j ACCEPT", port));
        }
        commands.add("which iptables && service iptables save");
        commands.add("which iptables && service iptables restart");

        newScript(CUSTOMIZING)
                .body.append(commands.build())
                .execute();

        // Copy JMX agent Jar to server
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
        String jvmOpts = String.format("-javaagent:%s -D%s=%d -D%s=%d -Djava.rmi.server.hostname=%s",
                getJmxRmiAgentJarDestinationFilePath(),
                JmxRmiAgent.JMX_SERVER_PORT_PROPERTY, getJmxPort(),
                JmxRmiAgent.RMI_REGISTRY_PORT_PROPERTY, getRmiServerPort(),
                getHostname());
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
	            .put("CASSANDRA_CONF", String.format("%s/conf", getRunDir()))
	            .put("JVM_OPTS", jvmOpts)
	            .build();
    }
}
