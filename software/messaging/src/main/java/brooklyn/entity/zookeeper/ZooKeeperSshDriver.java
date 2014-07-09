/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.zookeeper;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class ZooKeeperSshDriver extends JavaSoftwareProcessSshDriver implements ZooKeeperDriver {

    public ZooKeeperSshDriver(ZooKeeperNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { return Os.mergePathsUnix(getRunDir(), "console.out"); }

    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("zookeeperPort", getZooKeeperPort());
    }

    protected String getConfigFileName() {
        return entity.getConfig(ZooKeeperNode.ZOOKEEPER_CONFIG_TEMPLATE);
    }

    protected int getMyId() {
        return entity.getAttribute(ZooKeeperNode.MY_ID);
    }

    // FIXME All for one, and one for all! If any node fails then we're stuck waiting for its hostname/port forever.
    // Need a way to terminate the wait based on the entity going on-fire etc.
    // FIXME Race in getMemebers. Should we change DynamicCluster.grow to create the members and only then call start on them all?
    public List<ZooKeeperServerConfig> getZookeeperServers() throws ExecutionException, InterruptedException {
        ZooKeeperEnsemble ensemble = (ZooKeeperEnsemble) entity.getParent();
        List<ZooKeeperServerConfig> result = Lists.newArrayList();

        for (Entity member : ensemble.getMembers()) {
            Integer myid = Entities.attributeSupplierWhenReady(member, ZooKeeperNode.MY_ID).get();
            String hostname = Entities.attributeSupplierWhenReady(member, ZooKeeperNode.HOSTNAME).get();
            Integer port = Entities.attributeSupplierWhenReady(member, ZooKeeperNode.ZOOKEEPER_PORT).get();
            Integer leaderPort = Entities.attributeSupplierWhenReady(member, ZooKeeperNode.ZOOKEEPER_LEADER_PORT).get();
            Integer electionPort = Entities.attributeSupplierWhenReady(member, ZooKeeperNode.ZOOKEEPER_ELECTION_PORT).get();
            result.add(new ZooKeeperServerConfig(myid, hostname, port, leaderPort, electionPort));
        }
        return result;
    }

    @Override
    public Integer getZooKeeperPort() {
        return getEntity().getAttribute(ZooKeeperNode.ZOOKEEPER_PORT);
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(ImmutableMap.of(USE_PID_FILE, getPidFile()), STOPPING).execute();     
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("zookeeper-%s", getVersion())));

        List<String> commands = ImmutableList.<String> builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(BashCommands.INSTALL_TAR)
                .add("tar xzfv " + saveAs)
                .build();

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        log.debug("Customizing {}", entity);
        Networking.checkPortsValid(getPortMap());
        newScript(CUSTOMIZING)
                .body.append(
                        format("cp -R %s/* .", getExpandedInstallDir()),
                        format("mkdir %s/zookeeper", getRunDir()),
                        format("echo %d > %s/zookeeper/myid", getMyId(), getRunDir())
                    )
                .execute();

        String destinationConfigFile = Os.mergePathsUnix(getRunDir(), "conf/zoo.cfg");
        copyTemplate(getConfigFileName(), destinationConfigFile);
    }

    public String getPidFile() { return Os.mergePathsUnix(getRunDir(), "zookeeper.pid"); }

    @Override
    public void launch() {
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), LAUNCHING)
                .body.append(format("nohup java $JAVA_OPTS -cp zookeeper-%s.jar:lib/*:conf org.apache.zookeeper.server.quorum.QuorumPeerMain conf/zoo.cfg > %s 2>&1 &", getVersion(), getLogFileLocation()))
                .execute();
    }

    public static class ZooKeeperServerConfig {
        private final Integer myid;
        private final String hostname;
        private final Integer port;
        private final Integer leaderPort;
        private final Integer electionPort;

        public ZooKeeperServerConfig(Integer myid, String hostname, Integer port, Integer leaderPort, Integer electionPort) {
            this.myid = myid;
            this.hostname = hostname;
            this.port = port;
            this.leaderPort = leaderPort;
            this.electionPort = electionPort;
        }

        public Integer getMyid() { return myid; }
        public String getHostname() { return hostname; }
        public Integer getPort() { return port; }
        public Integer getLeaderPort() { return leaderPort; }
        public Integer getElectionPort() { return electionPort; }
    }
}
