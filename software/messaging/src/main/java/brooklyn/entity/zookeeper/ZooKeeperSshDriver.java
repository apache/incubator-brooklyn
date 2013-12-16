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
import java.util.concurrent.ExecutionException;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.entity.messaging.storm.Storm;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.BashCommands;

import brooklyn.util.task.Tasks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

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
        return entity.getConfig(ZooKeeperNode.ZOOKEEPER_CONFIG_TEMPLATE);
    }

   // FIXME All for one, and one for all! If any node fails then we're stuck waiting for its hostname/port forever.
   // Need a way to terminate the wait based on the entity going on-fire etc.
   // FIXME Race in getMemebers. Should we change DynamicCluster.grow to create the members and only then call start on them all?
   public List<ZooKeeperServerConfig> getZookeeperServers() throws ExecutionException, InterruptedException {
      ZooKeeperEnsemble ensemble = (ZooKeeperEnsemble) entity.getParent();
      List<ZooKeeperServerConfig> result = Lists.newArrayList();

      for (Entity member : ensemble.getMembers()) {
         ZooKeeperNode server = (ZooKeeperNode) member;
         String hostname = attributeWhenReady(server, ZooKeeperNode.HOSTNAME);
         Integer port = attributeWhenReady(server, ZooKeeperNode.ZOOKEEPER_PORT);
         Integer electionPort = attributeWhenReady(server, ZooKeeperNode.ZOOKEEPER_ELECTION_PORT);
         result.add(new ZooKeeperServerConfig(hostname, port, electionPort));
      }
      return result;
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
        String destinationConfigFile = String.format("%s/conf/zoo.cfg", getRunDir());
        copyTemplate(getConfigFileName(), destinationConfigFile);
    }

    public String getPidFile() { return String.format("%s/zookeeper.pid", getRunDir()); }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", getPidFile()), LAUNCHING)
        .body.append(
                format("nohup java -cp zookeeper-%s.jar:lib/*:conf org.apache.zookeeper.server.quorum.QuorumPeerMain conf/zoo.cfg > %s 2>&1 &", getVersion(), getLogFileLocation()))
        .execute();        
    }

   @SuppressWarnings({ "unchecked", "serial" })
   public static <T> T attributeWhenReady(final Entity entity, final AttributeSensor<T> sensor) throws ExecutionException, InterruptedException {
      final Task<T> task = DependentConfiguration.attributeWhenReady(entity, sensor);
      TypeToken<T> type = new TypeToken<T>(sensor.getType()) {};
      return Tasks.resolveValue(task, (Class<T>) type.getRawType(), ((EntityInternal) entity).getExecutionContext(), "attributeSupplierWhenReady");
   }

   public static class ZooKeeperServerConfig {
      private final String hostname;
      private final Integer port;
      private final Integer electionPort;

      public ZooKeeperServerConfig(String hostname, Integer port, Integer electionPort) {
         this.hostname = hostname;
         this.port = port;
         this.electionPort = electionPort;
      }
      public String getHostname() {
         return hostname;
      }
      public Integer getPort() {
         return port;
      }
      public Integer getElectionPort() {
         return electionPort;
      }
   }
}
