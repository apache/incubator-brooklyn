/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.zookeeper;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ZooKeeperSshDriver extends JavaSoftwareProcessSshDriver implements ZooKeeperDriver {

    public ZooKeeperSshDriver(ZooKeeperNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    private String expandedInstallDir;

    @Override
    protected String getLogFileLocation() { return getRunDir()+"/console.out"; }

    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }
    
    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("zookeeperPort", getZooKeeperPort());
    }

    protected String getConfigFileName() {
        return "zookeeper.properties";
    }

    @Override
    public Integer getZooKeeperPort() {
        return getEntity().getAttribute(ZooKeeperNode.ZOOKEEPER_PORT);
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(ImmutableMap.of("usePidFile", getPidFile()), STOPPING).execute();     
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("zookeeper-%s", getVersion()));

        List<String> commands = ImmutableList.<String> builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(BashCommands.INSTALL_CURL)
                .add(BashCommands.INSTALL_TAR)
                 .add("tar xzfv " + saveAs)
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();        
    }

    @Override
    public void customize() {
        log.debug("Customizing {}", entity);
        Networking.checkPortsValid(getPortMap());
        newScript(CUSTOMIZING)
                .body.append(
                    format("cp -R %s/* .", getExpandedInstallDir())
                )
                .execute();
    }

    public String getPidFile() { return String.format("%s/zookeeper.pid", getRunDir()); }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
        .body.append(
                format("nohup java -cp zookeeper-%s.jar:lib/*:conf org.apache.zookeeper.server.quorum.QuorumPeerMain conf/zoo_sample.cfg > %s 2>&1 &", getVersion(), getLogFileLocation()))
        .execute();        
    }

}
