/*
 * Copyright 2012 by Andrew Kennedy
 */
package brooklyn.entity.nosql.cassandra;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.NetworkUtils;
import brooklyn.util.text.Strings;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

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

    public String getHostname() { return getMachine().getAddress().getCanonicalHostName(); }

    @Override
    public void customize() {
        log.info("Customizing {} (Cluster {})", entity, getClusterName());
        Map ports = ImmutableMap.<String, Integer>builder()
                .put("jmxPort", getJmxPort())
                .put("rmiPort", getRmiServerPort())
                .put("gossipPort", getGossipPort())
                .put("sslGossipPort:", getSslGossipPort())
                .put("thriftPort", getThriftPort())
                .build();
        NetworkUtils.checkPortsValid(ports);

        String logFileEscaped = getLogFileLocation().replace("/", "\\/"); // escape slashes
        String dataDirEscaped = (getRunDir() + "/data").replace("/", "\\/"); // escape slashes

        List<String> commands = ImmutableList.<String>builder()
                .add(String.format("cp -R %s/apache-cassandra-%s/{bin,conf,lib,interface,pylib,tools} .", getInstallDir(), getVersion()))
                .add("mkdir data")
                .add(String.format("sed -i.bk 's/log4j.appender.R.File=.*/log4j.appender.R.File=%s/g' %s/conf/log4j-server.properties", logFileEscaped, getRunDir()))
                .add(String.format("sed -i.bk 's/\\/var\\/lib\\/cassandra/%s/g' %s/conf/cassandra.yaml", dataDirEscaped, getRunDir()))
                .add(String.format("sed -i.bk \"s/^cluster_name: .*/cluster_name: '%s'/g\" %s/conf/cassandra.yaml", getClusterName(), getRunDir()))
                .add(String.format("sed -i.bk 's/^JMX_PORT=.*/JMX_PORT=\"%d\"/g' %s/conf/cassandra-env.sh", getJmxPort(), getRunDir()))
                .add(String.format("sed -i.bk 's/^rpc_port: .*/rpc_port: %d/g' %s/conf/cassandra.yaml", getThriftPort(), getRunDir()))
                .add(String.format("sed -i.bk 's/^rpc_address: .*/rpc_address: %s/g' %s/conf/cassandra.yaml", getHostname(), getRunDir()))
                .add(String.format("sed -i.bk 's/^storage_port: .*/storage_port: %d/g' %s/conf/cassandra.yaml", getGossipPort(), getRunDir()))
                .add(String.format("sed -i.bk 's/^ssl_storage_port: .*/ssl_storage_port: %d/g' %s/conf/cassandra.yaml", getSslGossipPort(), getRunDir()))
                .add(String.format("sed -i.bk 's/^listen_address: .*/listen_address: %s/g' %s/conf/cassandra.yaml", getHostname(), getRunDir()))
                .add(String.format("sed -i.bk 's/^seeds: .*/seeds: \"%s\"/g' %s/conf/cassandra.yaml", getHostname(), getRunDir()))
                .add(String.format("which iptables && iptables -A INPUT -p tcp --dports %s -j ACCEPT", Joiner.on(",").join(Iterables.transform(ports.values(), Functions.toStringFunction()))))
                .build();

        newScript(CUSTOMIZING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void launch() {
        log.info("Launching  {}", entity);
        newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
                .body.append(String.format("nohup ./bin/cassandra -p %s -Djava.rmi.server.hostname=%s > ./cassandra-console.log 2>&1 &", getPidFile(), getHostname()))
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
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
	            .put("CASSANDRA_CONF", String.format("%s/conf", getRunDir()))
	            .build();
    }
}
