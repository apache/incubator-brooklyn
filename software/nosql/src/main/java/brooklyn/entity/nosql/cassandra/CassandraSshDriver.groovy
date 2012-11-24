/*
 * Copyright 2012 by Andrew Kennedy
 */
package brooklyn.entity.nosql.cassandra;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.lifecycle.CommonCommands
import brooklyn.entity.java.JavaSoftwareProcessSshDriver
import brooklyn.entity.java.UsesJmx;
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.NetworkUtils

/**
 * Start a {@link CassandraServer} in a {@link Location} accessible over ssh.
 */
public class CassandraSshDriver extends JavaSoftwareProcessSshDriver implements CassandraDriver {

    private static final Logger log = LoggerFactory.getLogger(CassandraSshDriver.class);
    
    public CassandraSshDriver(CassandraServer entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    @Override
    protected String getLogFileLocation() { "${runDir}/cassandra.log"; }

    @Override
    public Integer getGossipPort() { entity.getAttribute(CassandraServer.GOSSIP_PORT) }

    @Override
    public Integer getSslGossipPort() { entity.getAttribute(CassandraServer.SSL_GOSSIP_PORT) }

    @Override
    public Integer getThriftPort() { entity.getAttribute(CassandraServer.THRIFT_PORT) }

    @Override
    public String getClusterName() { entity.getAttribute(CassandraServer.CLUSTER_NAME) }
    
    @Override
    public void install() {
        log.info("Installing ${entity}");
        String url = entity.getConfig(CassandraServer.TGZ_URL)
        if (!url) {
            url = entity.getConfig(CassandraServer.MIRROR_URL)+"/${version}/apache-cassandra-${version}-bin.tar.gz"
        }
        String saveAs  = "apache-cassandra-${version}-bin.tar.gz"

        List<String> commands = new LinkedList();
        commands.addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel('/'), saveAs));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv ${saveAs}")

        newScript(INSTALLING).failOnNonZeroResultCode().body.append(commands).execute();
    }

    @Override
    public void customize() {
        log.info("Customizing ${entity} (Cluster ${clusterName})");
        NetworkUtils.checkPortsValid(jmxPort:jmxPort, gossipPort:gossipPort, sslGossipPort:sslGossipPort, thriftPort:thriftPort);

        String logFileEscaped = getLogFileLocation().replace("/", "\\/"); // escape slashes
        String dataDirEscaped = "${runDir}/data".replace("/", "\\/"); // escape slashes

        List<String> commands = new LinkedList();
        commands.add("cp -R ${installDir}/apache-cassandra-${version}/{bin,conf,lib,interface,pylib,tools} .");
        commands.add("mkdir data");
        commands.add("sed -i.bk 's/log4j.appender.R.File=.*/log4j.appender.R.File=${logFileEscaped}/g' ${runDir}/conf/log4j-server.properties");
        commands.add("sed -i.bk 's/\\/var\\/lib\\/cassandra/${dataDirEscaped}/g' ${runDir}/conf/cassandra.yaml");
        commands.add("sed -i.bk \"s/^cluster_name: .*/cluster_name: '${clusterName}'/g\" ${runDir}/conf/cassandra.yaml");
        commands.add("sed -i.bk 's/^JMX_PORT=.*/JMX_PORT=\"${jmxPort}\"/g' ${runDir}/conf/cassandra-env.sh");
        commands.add("sed -i.bk 's/^rpc_port: .*/rpc_port: ${thriftPort}/g' ${runDir}/conf/cassandra.yaml");
        commands.add("sed -i.bk 's/^rpc_address: .*/rpc_address: ${hostname}/g' ${runDir}/conf/cassandra.yaml");
        commands.add("sed -i.bk 's/^storage_port: .*/storage_port: ${gossipPort}/g' ${runDir}/conf/cassandra.yaml");
        commands.add("sed -i.bk 's/^ssl_storage_port: .*/ssl_storage_port: ${sslGossipPort}/g' ${runDir}/conf/cassandra.yaml");
        commands.add("sed -i.bk 's/^listen_address: .*/listen_address: ${hostname}/g' ${runDir}/conf/cassandra.yaml");

        newScript(CUSTOMIZING).body.append(commands).execute();
    }

    @Override
    public void launch() {
        log.info("Launching ${entity}");
        String hostname = entity.getAttribute(Attributes.HOSTNAME);
        newScript(LAUNCHING, usePidFile:false).
                body.append(
                "nohup ./bin/cassandra -p ${pidFile} -Djava.rmi.server.hostname=${hostname} > ./cassandra-console.log 2>&1 &",
                ).execute();
    }

    public String getPidFile() {"cassandra.pid"}
    
    @Override
    public boolean isRunning() {
        newScript(CHECK_RUNNING, usePidFile:pidFile).execute() == 0;
    }

    @Override
    public void stop() {
//         newScript(STOPPING, usePidFile:pidFile).execute();
    }

    @Override
    public void kill() {
        newScript(KILLING, usePidFile:pidFile).execute();
    }

    public Map<String, String> getShellEnvironment() {
        def result = super.getShellEnvironment()
        result << [
            CASSANDRA_CONF: "${runDir}/conf",
        ]
    }
}
